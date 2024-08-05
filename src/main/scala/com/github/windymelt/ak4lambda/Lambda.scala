package com.github.windymelt.ak4lambda

import com.monovore.decline.Command
import java.io.{InputStream, OutputStream}
import cats.implicits.*
import cats.effect.unsafe.implicits.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.generic.semiauto.*
import io.circe.parser.decode
import scala.io.Source
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scribe.writer.BrowserConsoleWriter.args
import cats.effect.unsafe.IORuntime
import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.IO
import com.monovore.decline.Help
import com.github.windymelt.ak4lambda.endpoint.Ak4.StampType
import feral.lambda.{*, given}
import cats.effect.kernel.Resource
import cats.effect.std.Env
import com.github.windymelt.ak4lambda.endpoint.Ak4.ErrorOutput
import com.github.windymelt.ak4lambda.endpoint.Ak4.StampOutput

case class ButtonClicked(clickTypeName: String) derives io.circe.Decoder

object handler extends feral.lambda.IOLambda.Simple[ButtonClicked, INothing] {
  private val cmd = Command("ak4", "Punch ak4 system", false)(
    (CLI.tokenEnvOpt, CLI.coopIdOpt).tupled
  )

  case class Init(logger: scribe.Logger, envToken: String, coop: String)

  override def init: Resource[IO, Init] = Resource.make {
    val parseResult: IO[Either[Help, (Option[java.util.UUID], String)]] = for {
      env <- Env[IO].entries
    } yield cmd.parse(Seq(), env.toMap)

    parseResult.flatMap {
      case Left(_) =>
        IO.raiseError(new IllegalArgumentException("envvar parse failed"))
      case Right((None, _)) =>
        IO.raiseError(new IllegalArgumentException("invalid UUID"))
      case Right((Some(uuid), coop)) =>
        IO(Init(scribe.Logger("handler"), uuid.toString(), coop))
    }
  }(_ => IO.unit)

  def apply(
      event: ButtonClicked,
      context: Context[IO],
      init: Init
  ): IO[Option[INothing]] = {
    val punchResult: IO[Either[ErrorOutput, StampOutput]] = for {
      _ <- IO(init.logger.info("handler start"))
      punchType <- event.clickTypeName match {
        case "SINGLE" => IO.pure(endpoint.Ak4.StampType.出勤)
        case "DOUBLE" => IO.pure(endpoint.Ak4.StampType.退勤)
        case "LONG"   => IO.pure(endpoint.Ak4.StampType.退勤)
      }
      punchResult <- punch(punchType, init.coop, init.envToken)
    } yield punchResult

    punchResult.flatMap {
      case Left(e) =>
        for {
          _ <- IO(init.logger.error("punch failed"))
          _ <- IO(init.logger.error(e.errors.map(_.message).mkString("\n")))
        } yield None
      case Right(out) =>
        out.success match {
          case true =>
            for {
              _ <- IO(init.logger.info("punch successful"))
              _ <- IO(init.logger.info(out.response.stampedAt))
            } yield None
          case false =>
            for {
              _ <- IO(init.logger.error("punch failed"))
              _ <- IO(init.logger.error(out.errors.mkString("\n")))
            } yield None
        }
    }
  }
}
