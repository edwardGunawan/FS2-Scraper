package fs2scraper

import java.nio.file.Paths

import cats.effect.{Blocker, ContextShift, IO, Timer}
import cats.effect.concurrent.Ref
import fs2.concurrent.{NoneTerminatedQueue, Queue}
import fs2.io.file.writeAll
import fs2.{Chunk, Pipe, Stream, text}
import io.circe.Decoder
import org.http4s.Uri
import org.http4s.client.Client

import fs2scraper.model.PublicationPayload
import fs2scraper.model.AboutDocument
import fs2scraper.model.PublicationMetadata

import scala.concurrent.duration.FiniteDuration

trait ScrapperProcessor {
  sealed trait Status {
    val publication: PublicationMetadata
  }
  case class Error(publication: PublicationMetadata, description: String) extends Exception(description) with Status
  case class Success(publication: PublicationMetadata) extends Status

  case class ClientError(body: String, status: Http4sStatus) extends Exception(body)
  case class SetValues(setOfPublication: Set[String], setOfTags: Set[String])

  implicit class ThrottleOp[A](io: IO[A]) {
    def throttle(duration: FiniteDuration)(implicit timer: Timer[IO]) = IO.sleep(duration)
  }

  def generateAlphabeticalCombination(numCharacter: Int): List[String] = {
    val alphabetList = List(
      "A",
      "B",
      "C",
      "D",
      "E",
      "F",
      "G",
      "H",
      "I",
      "J",
      "K",
      "L",
      "N",
      "M",
      "O",
      "P",
      "Q",
      "R",
      "S",
      "T",
      "U",
      "V",
      "W",
      "X",
      "Y",
      "Z"
    )

    def go(lst: List[String], numCharacter: Int): List[List[String]] = (lst, numCharacter) match {
      case (Nil, _) =>
        Nil
      case (_, 0) =>
        List(Nil)
      case (h :: t, _) =>
        go(t, numCharacter - 1).map(h :: _) ::: go(t, numCharacter)

    }

    go(alphabetList, numCharacter).map(_.mkString)
  }

  def cleanJson(jsonString: String): String = {
    jsonString.dropWhile(_ != '{')
  }
  /*
        1. fetch https://medium.com/search/publications?q={combination}&format=json
        3. parse combination into document
        4. from the slug get the https://medium.com/{slug}/about page and check if the writer is more than 10
        5. If the writer is more than 10, construct Publication metadata and write it good file, the bad ones will be written into the bad file

        If it failed we want to put it into the bad file

        Having an internal queue to get all the publication Payload and send it to downstream queue.
        Downstream queue will poll and get About page and transform it into publication metadata
        Then write it to the file (good or bad ones)
   */

  def runProcess(
    searchTermsRef: Ref[IO, List[String]],
    client: Client[IO],
    blocker: Blocker,
    goodFilePath: String,
    badFilePath: String
  )(
    implicit cs: ContextShift[IO],
    T: Timer[IO]
  ): IO[Unit] =
    for {
      q <- Queue.boundedNoneTerminated[IO, Publication](40)
      ref <- Ref[IO].of(SetValues(Set.empty[String], Set.empty[String]))
      _ <- IO(println("instantiate subscriber and producer flow flow ..."))
      _ <- IO.race(
        subscribersFlow(q, client, blocker, goodFilePath, badFilePath).compile.drain,
        producerFlow(q, client, ref, searchTermsRef).compile.drain
      )
      setValues <- ref.get
      numOfPublication = setValues.setOfPublication
      tags = setValues.setOfTags
      _ <- IO(println(s"Crawled ${numOfPublication.size} number of publication which has ${tags.size} number of tags."))
      _ <- IO(println("Done. Exiting ..."))
    } yield ()

  val toChunk: Pipe[IO, List[String], String] = _.flatMap(lst => Stream.chunk(Chunk.seq(lst)))

  def producerFlow(
    q: NoneTerminatedQueue[IO, Publication],
    client: Client[IO],
    ref: Ref[IO, SetValues],
    searchTermRef: Ref[IO, List[String]]
  )(implicit cs: ContextShift[IO], T: Timer[IO]): Stream[IO, Unit] = {

    def processPublicationSearch: Stream[IO, Unit] =
      Stream
        .eval(searchTermRef.getAndUpdate(_ => List.empty[String])) // once get it empty the string for future tag flow
        .through(toChunk)
        .parEvalMapUnordered(5) { searchTerm =>
          fetchPublicationSearch(client, q, searchTerm, Some("1"), ref, searchTermRef).handleErrorWith {
            case ClientError(description, status) if status.code == 429 =>
              println(s"[429 ERROR] on fetch publication description of the 429 status throttling")
                .pure[IO]
                .throttle(3 minutes)
            case err =>
              println(s"[Error NOT THROTTLE] Fetch search publication error ${err}").pure[IO]
          } *> IO(println(s"Done with ${searchTerm}."))
        }

    // Recursively polling the publication search if the searchTerms still exist, or else just quit
    def pollProcessPublicationSearch: Stream[IO, Unit] =
      Stream.eval(searchTermRef.get).flatMap { searchTerms =>
        if (searchTerms.isEmpty) Stream.eval(IO.unit)
        else processPublicationSearch >> pollProcessPublicationSearch
      }

    /**
      *   Fetch all the publication and put it into an internal queue
      * @param client
      * @param q
      * @param searchTerm
      * @param startPage
      * @param cs
      * @return
      */
    def fetchPublicationSearch(
      client: Client[IO],
      q: NoneTerminatedQueue[IO, Publication],
      searchTerm: String,
      startPage: Option[String],
      ref: Ref[IO, SetValues],
      searchTermRef: Ref[IO, List[String]]
    )(implicit cs: ContextShift[IO]): IO[Unit] =
      if (startPage.isDefined) {
        for {
          //        _ <- IO(println(s"getting search term ${searchTerm} on page ${startPage}"))
          publicationPayload <- getPublication(client, searchTerm, startPage.get)
          //        _ <- IO(println(s"Successfully get the search term for ${startPage}"))
          _ <- publicationPayload.value.parTraverse { publication =>
            for {
              // get the previous set and update it with the current publication slug and the set of tags
              setValues <- ref.modify { setValues =>
                (
                  setValues.copy(
                    setOfPublication = setValues.setOfPublication + publication.slug,
                    setOfTags = setValues.setOfTags ++ publication.tags.toSet
                  ),
                  setValues
                )
              }

              setOfTags = setValues.setOfTags
              setOfPublication = setValues.setOfPublication

              // if the set contains the slug of the publication don't enqueue it into the queue
              _ <- IO(setOfPublication.contains(publication.slug)).ifM(
                IO(println(s"[Duplication] The set contains ${publication.slug} omitting writing to queue...")),
                q.enqueue1(Some(publication))
              )

              // updating all the tags that doesn't exist in the set of tags
              _ <- publication.tags.filterNot(t => setOfTags.contains(t)).parTraverse { tag =>
                IO(println(s"[Adding Tag] Adding ${tag} to the search term ...")) >>
                searchTermRef.update(tag :: _)
              }
            } yield ()

          }
          //        _ <- IO(
          //          println(s"Calling fetchPublicationSearchAgain (${searchTerm}) for ${publicationPayload.paging.nextPage}")
          //        )
          _ <- fetchPublicationSearch(client, q, searchTerm, publicationPayload.paging.nextPage, ref, searchTermRef)
        } yield ()
      } else {
        IO(println(s"End of the page for ${searchTerm}"))
      }

    // this is the entire main funciton here
    Stream(println("instantiate the fetch publication... \n")).covary[IO] >> pollProcessPublicationSearch >> Stream(
      println("[DONE] exhaust all the search terms")
    ).covary[IO]

  }

  def subscribersFlow(
    q: NoneTerminatedQueue[IO, Publication],
    client: Client[IO],
    blocker: Blocker,
    goodFileNamePath: String,
    badFilePath: String
  )(
    implicit cs: ContextShift[IO],
    T: Timer[IO]
  ) = {

    def writeToFile(fileName: String): Pipe[IO, PublicationMetadata, Unit] =
      _.map { metadata =>
        metadata.asJson.noSpaces
      }.intersperse(",\n")
        .through(text.utf8Encode)
        .through(
          writeAll[IO](Paths.get(fileName), blocker)
        )

    val processDownstream: Pipe[IO, Publication, Option[Status]] = stream => {
      stream.parEvalMapUnordered(5) { publication =>
        for {
          //          _ <- IO(println("Calling about Page"))
          aboutPageEither <- getAboutPage(client, publication.slug).attempt
          optionalValue <- aboutPageEither.fold(
            throwable =>
              throwable match {
                case ClientError(_, status) if status.code == 429 =>
                  IO(println(s"[429 ERROR] on fetching about page because of too many request throttling"))
                    .throttle(3 minute) *> IO(
                    Error(constructPublicationMetadata(publication), throwable.getMessage).some
                  )
                case err =>
                  IO(
                    println(
                      s"[Error NOT THROTTLE] on fetching about page not cause by throttling ${publication.slug} - ${err.getMessage}"
                    )
                  ) *>
                    IO(Error(constructPublicationMetadata(publication), err.getMessage).some)
              },
            aboutPage =>
              IO(isAbleToRequest(aboutPage))
                .ifM(
                  IO(Success(constructPublicationMetadata(publication)).some),
                  IO(println(s"[Omitting] ${publication.slug} because writer is less than 40")) *> IO(None)
                )
          )

        } yield (optionalValue)
      }
    }

    def filteredOutputAndWriteToFile(pred: Status => Boolean, fileName: String): Pipe[IO, Status, Unit] =
      _.filter(pred).map(_.publication).through(writeToFile(fileName))

    val dequeueResource: Stream[IO, Unit] =
      Stream.bracketCase {
        for {
          _ <- IO(println("Dequeueing from the queue"))

          _ <- q.dequeue
            .through(processDownstream)
            .filter(_.isDefined)
            .map(_.get)
            .observe(filteredOutputAndWriteToFile(status => status.isInstanceOf[Error], badFilePath))
            .through(filteredOutputAndWriteToFile(status => status.isInstanceOf[Success], goodFileNamePath))
            .compile
            .drain
        } yield ()
      }(
        (success, err) =>
          IO(println(s"------------------- closing resources - success ${success}, error ${err}")) *> q.enqueue1(None)
      )

    dequeueResource

  }

  def procure[A: Decoder](client: Client[IO], url: String): IO[A] =
    for {
      str <- client.expectOr[String](url) { onError =>
        for {
          lstOfString <- onError.body.compile.toList
        } yield (ClientError(new String(lstOfString.toArray), onError.status))
      }
      parsedString <- IO(cleanJson(str))
      model <- IO.fromEither(decode[A](parsedString))
    } yield model

  def procure[A: Decoder](client: Client[IO], url: Uri): IO[A] =
    for {
      str <- client.expectOr[String](url) { onError =>
        for {
          lstOfString <- onError.body.compile.toList
        } yield (ClientError(new String(lstOfString.toArray), onError.status))
      }
      parsedString <- IO(cleanJson(str))
      model <- IO.fromEither(decode[A](parsedString))
    } yield model

  def getPublication(client: Client[IO], searchTerm: String, nextPage: String): IO[PublicationPayload] =
    procure[PublicationPayload](
      client,
      Uri.encode(s"https://medium.com/search/publications?q=${searchTerm}&format=json&page=${nextPage}")
    )

  def getAboutPage(client: Client[IO], slug: String): IO[AboutDocument] =
    procure[AboutDocument](client, Uri.encode(s"https://medium.com/${slug}/about?format=json"))

  /**
    *   Check if the writer is more than 40
    * @param aboutDocument
    * @return
    */
  def isAbleToRequest(aboutDocument: AboutDocument): Boolean = aboutDocument.writers > 40

  def constructPublicationMetadata(publication: Publication): PublicationMetadata = PublicationMetadata(
    name = publication.name,
    slug = publication.slug
  )

}
