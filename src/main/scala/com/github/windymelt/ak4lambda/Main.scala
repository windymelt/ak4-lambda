package com.github.windymelt.ak4lambda

import cats.effect.IO
import cats.effect.IOApp
import cats.effect._
import cats.implicits._
import com.github.nscala_time.time.Implicits._
import com.github.windymelt.ak4lambda.endpoint.Ak4.ErrorOutput
import com.github.windymelt.ak4lambda.endpoint.Ak4.ErrorResponse
import com.github.windymelt.ak4lambda.endpoint.Ak4.StampOutput
import com.github.windymelt.ak4lambda.endpoint.Ak4.StampType
import com.monovore.decline._
import com.monovore.decline.effect._
import org.http4s.client._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits._
import org.joda.time.DateTime
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Value
import sttp.tapir.client.http4s.Http4sClientInterpreter

import java.io.InputStream
import java.io.OutputStream
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
  import reflect.Selectable.reflectiveSelectable
  import io.circe._, io.circe.generic.auto._, io.circe.parser._,
    io.circe.syntax._
  import scala.io.Source

  case class ButtonClicked(val clickType: String, val reportedTime: String)
  case class DeviceEvent(val buttonClicked: ButtonClicked)
  case class OneClickEvent(val deviceEvent: DeviceEvent)

  val logger = LogManager.getLogger(this.getClass())

  val cmd = Command("ak4", "Punch ak4 system", false)(
    (CLI.tokenEnvOpt, CLI.coopIdOpt).tupled
  )

  // AWS Lambda用エンドポイント
  def handler(
      input: InputStream,
      output: OutputStream,
      context: Context
  ): Unit =
    cmd.parse(Seq(), sys.env) match {
      case Right((token, coop)) =>
        val jsonString = Source.fromInputStream(input).mkString
        val event = decode[OneClickEvent](jsonString)
        event match {
          case Left(msg) =>
            println(msg.toString())
            msg.toString()
          case Right(event) =>
            val clickType = event.deviceEvent.buttonClicked.clickType
            val stampType = clickType match {
              case "SINGLE" => endpoint.Ak4.StampType.出勤
              case "DOUBLE" => endpoint.Ak4.StampType.退勤
              case "LONG"   => endpoint.Ak4.StampType.退勤
            }
            val result = punch(stampType, coop, token.toString).unsafeRunSync()
            result match {
              case Right(out) if out.success =>
                logger.info(s"punch successful: ${stampType.toString()}")
                stampType match {
                  case StampType.出勤 =>
                    output.write("""{"status":"in"}""".getBytes())
                  case StampType.退勤 =>
                    output.write("""{"status":"out"}""".getBytes())
                  case _ => // nop
                }
              case Left(err) => {
                logger.error(s"punch failed:")
                logger.error(
                  err.errors.map(e => s"${e.code}: ${e.message}").mkString("\n")
                )
                output.write("fail".getBytes())
              }
              case otherwise =>
                logger.error("punch failed")
                output.write("fail".getBytes())
            }
        }
      case Left(h) =>
        logger.error(h.toString)
        output.write("fail".getBytes())
    }
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
