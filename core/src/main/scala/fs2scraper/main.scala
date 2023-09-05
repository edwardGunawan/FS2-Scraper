package fs2scraper

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.FollowRedirect

import scala.concurrent.ExecutionContext

/*
  Go to all publication q=*&format=json
  Go to /about page on each publication
  Each publication has 5 tags that can be put in the publication tab (or is the user one much better)
  Get the value payload/masthead/staff check the user that is not an editor if the user is more than 10 put it into the list
  Get convert it into publication url, feed url and the name
  Write it into the csvfile

  // technically you can use slug to just watch the

 */
object main extends IOApp with ScrapperProcessor {

  val searchTerm = List(
    "*",
    "^A/*",
    "^B/*",
    "^C/*",
    "^D/*",
    "^E/*",
    "^F/*",
    "^G/*",
    "^H/*",
    "^I/*",
    "^J/*",
    "^K/*",
    "^L/*",
    "^N/*",
    "^M/*",
    "^O/*",
    "^P/*",
    "^Q/*",
    "^R/*",
    "^S/*",
    "^T/*",
    "^U/*",
    "^V/*",
    "^W/*",
    "^X/*",
    "^Y/*",
    "^Z/*"
  )
  override def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO](ExecutionContext.global).resource.product(Blocker[IO]).use {
      case (client, blocker) =>
        val followRedirectClient = FollowRedirect(maxRedirects = 10000)(client)
        for {
          searchTermRef <- Ref[IO].of(searchTerm)
          _ <- runProcess(
            searchTermRef,
            followRedirectClient,
            blocker,
            "good_May9.json",
            "bad_May9.json"
          )
        } yield (ExitCode.Success)
    }

}
