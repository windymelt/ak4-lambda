package com.github.windymelt.ak4lambda

import com.monovore.decline.Command
import java.io.{InputStream, OutputStream}
import cats.implicits.*
import cats.effect.unsafe.implicits.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.decode
import scala.io.Source
import scala.scalajs.js
import scala.scalajs.js.annotation._

object Lambda {
  @js.native
  trait ButtonClicked extends js.Object {
    val clickTypeName: String
  }

  @js.native
  trait Context extends js.Object {}

  private val logger = scribe.Logger("Lambda")

  private val cmd = Command("ak4", "Punch ak4 system", false)(
    (CLI.tokenEnvOpt, CLI.coopIdOpt, CLI.secretArnOpt).tupled
  )

  // AWS Lambda用エンドポイント
  @JSExportTopLevel(name = "handler", moduleID = "index")
  def handler(
      input: InputStream,
      output: OutputStream,
      context: Context
  ): Unit =
    val punchResult = for
      tuple <- cmd.parse(Seq(), sys.env)
      (envToken, coop, secretArn) = tuple
      event <- decode[ButtonClicked](Source.fromInputStream(input).mkString)
      clickType = event.clickTypeName
      stampType = clickType match
        case "SINGLE" => endpoint.Ak4.StampType.出勤
        case "DOUBLE" => endpoint.Ak4.StampType.退勤
        case "LONG"   => endpoint.Ak4.StampType.退勤
      token = envToken.get //.getOrElse(Secret.currentToken(secretArn).unsafeRunSync())
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
