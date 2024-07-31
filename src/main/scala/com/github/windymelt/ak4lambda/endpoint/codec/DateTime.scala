package com.github.windymelt.ak4lambda.endpoint.codec

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import scala.util.control.Exception.allCatch
import java.time.LocalDateTime

object DateTime {
  import java.time.OffsetDateTime as DateTime
  import java.time.ZoneId
  import java.time.format.DateTimeFormatter
  val JST = ZoneId.of("+9")

  val formatter = DateTimeFormatter.ofPattern("""yyyy/MM/dd HH:mm:ss""")

  given Encoder[DateTime] = (dt: DateTime) =>
    Json.fromString(
      dt.atZoneSameInstant(JST)
        .format(formatter)
    )

  given Decoder[DateTime] = (s: HCursor) =>
    for
      dtString <- s.focus
        .flatMap(_.asString)
        .toRight(DecodingFailure("Could not parse as string", s.history))
      dt <- (allCatch either {
        LocalDateTime.parse(dtString, formatter).atZone(JST).toOffsetDateTime()
      }).left.map(e => DecodingFailure(e.getMessage, s.history))
    yield dt
}
