package com.github.windymelt.ak4lambda

import cats.effect.IO
import cats.effect.*
import cats.implicits.*
import com.github.windymelt.ak4lambda.endpoint.Ak4.{
  ErrorOutput,
  ErrorResponse,
  ReissueTokenOutput,
  StampOutput,
  StampType
}
import com.monovore.decline.*
import com.monovore.decline.effect.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.*
import org.joda.time.DateTime
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Value
import sttp.tapir.client.http4s.Http4sClientInterpreter

import java.io.InputStream
import java.io.OutputStream

object Main
    extends CommandIOApp(
      "ak4",
      "Punch ak4 system",
      helpFlag = true,
      version = "0.0.1"
    ) {

  // CLI用エンドポイント
  override def main = (CLI.tokenEnvOpt, CLI.punchTypeOpt, CLI.coopIdOpt) mapN {
    (token, punchType, coop) =>
      {
        val punchTypeEnum = punchType match {
          case Some("on")  => endpoint.Ak4.StampType.出勤
          case Some("off") => endpoint.Ak4.StampType.退勤
          case Some(_)     => ???
          case None => throw new RuntimeException("CLIから呼び出すときは打刻種別が必要です")
        }

        punch(punchTypeEnum, coop, token.toString).debug(
          "result: "
        ) >> ExitCode.Success.pure
      }
  }
}

object Lambda {
  import com.amazonaws.services.lambda.runtime.Context
  import org.apache.logging.log4j.LogManager
  import org.apache.logging.log4j.Logger
  import cats.effect.unsafe.implicits._
  import reflect.Selectable.reflectiveSelectable
  import io.circe._, io.circe.generic.auto._, io.circe.parser._,
    io.circe.syntax._
  import scala.io.Source

  case class ButtonClicked(val clickType: String, val reportedTime: String)
  case class DeviceEvent(val buttonClicked: ButtonClicked)
  case class OneClickEvent(val deviceEvent: DeviceEvent)

  val logger = LogManager.getLogger(this.getClass())

  val cmd = Command("ak4", "Punch ak4 system", false)(
    (CLI.tokenEnvOpt, CLI.coopIdOpt, CLI.secretArnOpt).tupled
  )

  // AWS Lambda用エンドポイント
  def handler(
      input: InputStream,
      output: OutputStream,
      context: Context
  ): Unit =
    val punchResult = for
      tuple <- cmd.parse(Seq(), sys.env)
      (envToken, coop, secretArn) = tuple
      event <- decode[OneClickEvent](Source.fromInputStream(input).mkString)
      clickType = event.deviceEvent.buttonClicked.clickType
      stampType = clickType match
        case "SINGLE" => endpoint.Ak4.StampType.出勤
        case "DOUBLE" => endpoint.Ak4.StampType.退勤
        case "LONG"   => endpoint.Ak4.StampType.退勤
      token = envToken.getOrElse(Secret.currentToken(secretArn).unsafeRunSync())
      result <- punch(stampType, coop, token.toString)
        .unsafeRunSync()
        .filterOrElse(_.success, "Punch failed")
      _ = stampType match
        case endpoint.Ak4.StampType.出勤 =>
          output.write("""{"status":"in"}""".getBytes())
          // renew token every morning
          val newToken =
            renewToken(coop, token.toString).unsafeRunSync()
          newToken.foreach: t =>
            Secret
              .updateCurrentToken(secretArn, t.response.token)
              .unsafeRunSync()
        case endpoint.Ak4.StampType.退勤 =>
          output.write("""{"status":"out"}""".getBytes())
        case _ => // nop
    yield result
    punchResult match
      case Left(e) =>
        logger.error(s"punch failed:")
        logger.error(e.toString)
        output.write("fail".getBytes())
      case Right(_) => // nop

    input.close()
    output.flush()
    output.close()
}

def punch(
    punchType: endpoint.Ak4.StampType,
    coop: String,
    token: String
): IO[Either[ErrorOutput, StampOutput]] = {
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

  for {
    pr <- parsedResult
    v <- pr match {
      case Value(Right(v)) => IO(Right(v))
      case Value(Left(e))  => IO(Left(e))
      case otherwise =>
        IO(
          Left(
            ErrorOutput(
              false,
              Seq(ErrorResponse("CLIENT_FAILED", "decode failed"))
            )
          )
        )
    }
  } yield v
}

def renewToken(
    coop: String,
    token: String
): IO[Either[ErrorOutput, ReissueTokenOutput]] = {
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

  val parsedResult: IO[DecodeResult[Either[ErrorOutput, ReissueTokenOutput]]] =
    clientResource.flatMap(_.run(reissueRequest)).use(parseResponse)

  for {
    pr <- parsedResult
    v <- pr match {
      case Value(Right(v)) => IO(Right(v))
      case Value(Left(e))  => IO(Left(e))
      case otherwise =>
        IO.println(otherwise) >> IO(
          Left(
            ErrorOutput(
              false,
              Seq(ErrorResponse("CLIENT_FAILED", "decode failed"))
            )
          )
        )
    }
  } yield v
}
