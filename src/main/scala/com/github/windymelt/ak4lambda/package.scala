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
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.*
import org.joda.time.DateTime
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Value
import sttp.tapir.client.http4s.Http4sClientInterpreter

package object ak4lambda:
  def punch(
      punchType: endpoint.Ak4.StampType,
      coop: String,
      token: String
  ): IO[Either[ErrorOutput, StampOutput]] =
    val (punchRequest, parseResponse) =
      Http4sClientInterpreter[IO]()
        .toSecureRequest(
          endpoint.Ak4.punch,
          baseUri = Some(uri"https://atnd.ak4.jp/")
        )
        .apply(token)(
          coop,
          endpoint.Ak4.StampInput(
            punchType.code.toInt,
            DateTime.now(),
            "+09:00"
          )
        )

    val clientResource = EmberClientBuilder.default[IO].build

    val parsedResult: IO[DecodeResult[Either[ErrorOutput, StampOutput]]] =
      clientResource.flatMap(_.run(punchRequest)).use(parseResponse)

    extractDecodeResult(parsedResult)

  def renewToken(
      coop: String,
      token: String
  ): IO[Either[ErrorOutput, ReissueTokenOutput]] =
    val (reissueRequest, parseResponse) =
      Http4sClientInterpreter[IO]()
        .toSecureRequest(
          endpoint.Ak4.reissueToken,
          baseUri = Some(uri"https://atnd.ak4.jp/")
        )
        .apply(token)(
          coop,
          endpoint.Ak4.ReissueTokenInput(token)
        )

    val clientResource = EmberClientBuilder.default[IO].build

    val parsedResult
        : IO[DecodeResult[Either[ErrorOutput, ReissueTokenOutput]]] =
      clientResource.flatMap(_.run(reissueRequest)).use(parseResponse)

    extractDecodeResult(parsedResult)

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
