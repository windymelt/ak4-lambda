package com.github.windymelt.ak4lambda

import cats.effect.IO
import cats.effect.IOApp
import cats.effect._
import cats.implicits._
import com.amazonaws.services.lambda.runtime.events.IoTButtonEvent
import com.github.nscala_time.time.Implicits._
import com.github.windymelt.ak4lambda.endpoint.Ak4.StampOutput
import com.monovore.decline._
import com.monovore.decline.effect._
import org.http4s.client._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits._
import org.joda.time.DateTime
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Value
import sttp.tapir.client.http4s.Http4sClientInterpreter

import java.{util => ju}
import scala.concurrent.ExecutionContext.global

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

  val logger = LogManager.getLogger(Lambda.getClass())

  val cmd = Command("ak4", "Punch ak4 system", false)(
    (CLI.tokenEnvOpt, CLI.coopIdOpt).tupled
  )

  // AWS Lambda用エンドポイント
  def handler(event: IoTButtonEvent, context: Context): String =
    cmd.parse(Seq(), sys.env) match {
      case Right((token, coop)) =>
        val clickType = event.getClickType()
        val stampType = clickType match {
          case "SINGLE" => endpoint.Ak4.StampType.出勤
          case "DOUBLE" => endpoint.Ak4.StampType.退勤
          case "LONG"   => endpoint.Ak4.StampType.退勤
        }
        val result = punch(stampType, coop, token.toString).unsafeRunSync()
        result match {
          case Right(out) if out.success =>
            logger.info(s"punch successful: ${stampType.toString()}")
            "ok"
          case otherwise =>
            logger.error("punch failed")
            "fail"
        }
      case Left(h) =>
        logger.error(h.toString)
        "fail"
    }
}

def punch(
    punchType: endpoint.Ak4.StampType,
    coop: String,
    token: String
): IO[Either[Unit, StampOutput]] = {
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

  val parsedResult: IO[DecodeResult[Either[Unit, StampOutput]]] =
    clientResource.flatMap(_.run(punchRequest)).use(parseResponse)

  for {
    pr <- parsedResult
    v <- pr match {
      case Value(Right(v)) => IO(Right(v))
      case Value(Left(e))  => IO(Left(e))
      case otherwise       => IO(Left(()))
    }
  } yield v
}
