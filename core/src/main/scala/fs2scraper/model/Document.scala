package fs2scraper.model

import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder, HCursor}

case class PublicationPayload(total: Int, value: List[Publication], paging: Paging)

object PublicationPayload {
  implicit val decoder: Decoder[PublicationPayload] = (hcursor: HCursor) =>
    for {
      total <- hcursor.downField("payload").get[Int]("total")
      value <- hcursor.downField("payload").downField("value").as[List[Publication]]
      paging <- hcursor.downField("payload").get[Paging]("paging")
    } yield PublicationPayload(total, value, paging)

  implicit val encoder: Encoder[PublicationPayload] = deriveEncoder[PublicationPayload]
}

@JsonCodec
case class Publication(
  id: String,
  name: String,
  slug: String,
  description: String,
  shortDescription: String,
  tags: List[String]
)

case class Paging(nextPage: Option[String])

object Paging {
  implicit val encoder: Encoder[Paging] = deriveEncoder[Paging]

  implicit val decoder: Decoder[Paging] = (hCursor: HCursor) =>
    for {
      page <- hCursor.downField("next").downField("page").as[Option[Int]]
    } yield Paging(page.map(_.toString))
}
