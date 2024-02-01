package com.github.windymelt.ak4lambda.endpoint.codec

import com.github.nscala_time.time.Implicits.*
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import org.joda.time.DateTimeZone
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import scala.util.control.Exception.allCatch

object DateTime {
  import org.joda.time.DateTime
  val JST = +9

  given Encoder[DateTime] = new Encoder[DateTime] {
    final def apply(dt: DateTime): Json =
      Json.fromString(
        dt.withZone(DateTimeZone.forOffsetHours(JST))
          .toString("""yyyy/MM/dd HH:mm:ss""")
      )
  }

  given Decoder[DateTime] = new Decoder[DateTime] {
    final def apply(s: HCursor): Decoder.Result[DateTime] = {
      val fmt = DateTimeFormat.forPattern("""yyyy/MM/dd HH:mm:ss""")
      for
        dtString <- s.focus
          .flatMap(_.asString)
          .toRight(DecodingFailure("Could not parse as string", s.history))
        dt <- (allCatch either {
          org.joda.time.DateTime.parse(dtString, fmt)
        }).left.map(e => DecodingFailure(e.getMessage, s.history))
      yield dt
    }
  }
}
