package com.github.windymelt.ak4lambda.endpoint.codec

class DateTimeSpec extends munit.FunSuite {
  test("can encode JST datetime") {
    val dt = java.time.OffsetDateTime.parse("1993-08-13T12:34:56+09:00")
  }

  test("can decode JST datetime") {}
}
