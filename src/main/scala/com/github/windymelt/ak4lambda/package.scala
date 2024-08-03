package com.github.windymelt

import cats.effect.*
import cats.implicits.*
import com.github.windymelt.ak4lambda.endpoint.Ak4.{
  ErrorOutput,
  ErrorResponse,
  ReissueTokenOutput,
  StampOutput,
  StampType
}
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Value
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.model.Uri
import sttp.client3.FetchBackend

package object ak4lambda {
  def punch(
      punchType: endpoint.Ak4.StampType,
      coop: String,
      token: String
  ): IO[Either[ErrorOutput, StampOutput]] = {
    val res =
      SttpClientInterpreter()
        .toSecureRequest(
          endpoint.Ak4.punch,
          baseUri = Some(Uri("https://atnd.ak4.jp/"))
        )
        .apply(token)(
          coop,
          endpoint.Ak4.StampInput(
            punchType.code.toInt,
            java.time.OffsetDateTime.now(java.time.ZoneId.of("+9")),
            "+09:00"
          )
        )

    val client = FetchBackend()
    val result = IO.fromFuture(IO(client.send(res))).onError(e => IO(scribe.error(e.getMessage()))).map(_.body)
    extractDecodeResult(result)
  }
  // def renewToken(
  //     coop: String,
  //     token: String
  // ): IO[Either[ErrorOutput, ReissueTokenOutput]] =
  //   val res =
  //     SttpClientInterpreter()
  //       .toSecureRequest(
  //         endpoint.Ak4.reissueToken,
  //         baseUri = Some(Uri("https://atnd.ak4.jp/"))
  //       )
  //       .apply(token)(
  //         coop,
  //         endpoint.Ak4.ReissueTokenInput(token)
  //       )

  //   val clientResource = Resource.make(IO(FetchBackend()))(_ => IO.unit)

  //   val parsedResult
  //       : IO[DecodeResult[Either[ErrorOutput, ReissueTokenOutput]]] =
  //     clientResource.flatMap(_.send(reissueRequest)).use(parseResponse)

  //   extractDecodeResult(parsedResult)

  def extractDecodeResult[A](
      io: IO[DecodeResult[Either[ErrorOutput, A]]]
  ): IO[Either[ErrorOutput, A]] =
    for
      pr <- io
      v <- pr match
        case Value(either) => IO(either)
        case otherwise =>
          val err = Seq(ErrorResponse("CLIENT_FAILED", "decode failed"))
          IO.println(otherwise) >> Left(ErrorOutput(false, err)).pure
    yield v
}
