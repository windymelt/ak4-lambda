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

object Lambda {
  @js.native
  trait ButtonClicked extends js.Object {
    val clickTypeName: String
  }

  given ButtonClickedCodec: Codec[ButtonClicked] = new Codec[ButtonClicked] {
    def apply(c: HCursor): Decoder.Result[ButtonClicked] =
      for ctn <- c.downField("clickTypeName").as[String]
      yield js.Object
        .fromEntries(
          js.Array(
            "clickTypeName" -> ctn
          )
        )
        .asInstanceOf[ButtonClicked]
    def apply(a: ButtonClicked): Json =
      Json.obj("clickTypeName" -> Json.fromString(a.clickTypeName))
  }

  @js.native
  trait Context extends js.Object {}

  private val logger = scribe.Logger("Lambda")

  private val cmd = Command("ak4", "Punch ak4 system", false)(
    (CLI.tokenEnvOpt, CLI.coopIdOpt).tupled
  )

  // AWS Lambda用エンドポイント
  @JSExportTopLevel(name = "handler", moduleID = "index")
  def handler(
      input: ButtonClicked,
      context: Context
  ): Unit = {
    val env = js.Dynamic.global.process.env.asInstanceOf[js.Dictionary[String]].toMap
    val settings: Either[Help, (StampType, String, String)] = for
      (envToken, coop) <- cmd.parse(Seq(), env)
      clickType = input.clickTypeName
      stampType = clickType match
        case "SINGLE" => endpoint.Ak4.StampType.出勤
        case "DOUBLE" => endpoint.Ak4.StampType.退勤
        case "LONG"   => endpoint.Ak4.StampType.退勤
      token = envToken.get
    yield (stampType, coop, token.toString)

    App(settings).main(Array())
  }

  private class App(settings: Either[Help, (StampType, String ,String)]) extends IOApp.Simple {
    def run: IO[Unit] = {
      val f =
        punch.tupled.andThen(_.map(_.filterOrElse(_.success, "Punch failed")))
      val pr = settings.traverse(f)
      val prf = pr.map {
        case Left(e) =>
          logger.error(s"punch failed:")
          logger.error(e.toString)
          logger.info("fail")
        case Right(_) =>
          logger.info("ok")
      }

      prf >> IO.pure(ExitCode.Success)
    }
  }

  // val punchResult = for
  //   (envToken, coop, secretArn) <- cmd.parse(Seq(), sys.env)
  //   event <- decode[ButtonClicked](Source.fromInputStream(input).mkString)
  //   clickType = event.clickTypeName
  //   stampType = clickType match
  //     case "SINGLE" => endpoint.Ak4.StampType.出勤
  //     case "DOUBLE" => endpoint.Ak4.StampType.退勤
  //     case "LONG"   => endpoint.Ak4.StampType.退勤
  //   token = envToken.get //.getOrElse(Secret.currentToken(secretArn).unsafeRunSync())
  //   result <- punch(stampType, coop, token.toString)
  //     .filterOrElse(_.success, "Punch failed")
  //   _ = stampType match
  //     case endpoint.Ak4.StampType.出勤 =>
  //       output.write("""{"status":"in"}""".getBytes())
  //       // renew token every morning
  //       // val newToken =
  //       //   renewToken(coop, token.toString).unsafeRunSync()
  //       // newToken.foreach: t =>
  //       //   Secret
  //       //     .updateCurrentToken(secretArn, t.response.token)
  //       //     .unsafeRunSync()
  //     case endpoint.Ak4.StampType.退勤 =>
  //       output.write("""{"status":"out"}""".getBytes())
  //     case _ => // nop
  // yield result
  // punchResult match
  //   case Left(e) =>
  //     logger.error(s"punch failed:")
  //     logger.error(e.toString)
  //     output.write("fail".getBytes())
  //   case Right(_) => // nop
  // input.close()
  // output.flush()
  // output.close()
}
