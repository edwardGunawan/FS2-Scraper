package fs2scraper.model

import cats.implicits._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, HCursor, Json}

case class AboutDocument(writers: Int)

object AboutDocument {
  implicit val encoder: Encoder[AboutDocument] = deriveEncoder[AboutDocument]
  implicit val decoder: Decoder[AboutDocument] = (hCursor: HCursor) =>
    for {
      lstOfStaffJson <- hCursor.downField("payload").downField("masthead").downField("staff").as[List[Json]]
      numberOfWriters <- lstOfStaffJson.foldLeft(0.asRight)(
        (count, json) => if (json.findAllByKey("isEditor").isEmpty) count.map(_ + 1) else count
      )
    } yield AboutDocument(numberOfWriters)
}
