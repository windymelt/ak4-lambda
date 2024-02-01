package com.github.windymelt.ak4lambda

import cats.effect.*
import cats.implicits.*
import com.github.windymelt.ak4lambda.endpoint.Ak4.{
  ErrorOutput,
  StampOutput,
  StampType
}
import com.monovore.decline.*
import com.monovore.decline.effect.*

object Main
    extends CommandIOApp(
      "ak4",
      "Punch ak4 system",
      helpFlag = true,
      version = "0.0.1"
    ):
  // CLI用エンドポイント
  override def main = (CLI.tokenEnvOpt, CLI.punchTypeOpt, CLI.coopIdOpt) mapN:
    (token, punchType, coop) =>
      val punchTypeEnum = punchType match {
        case Some("on")  => endpoint.Ak4.StampType.出勤
        case Some("off") => endpoint.Ak4.StampType.退勤
        case Some(_)     => ???
        case None        => throw new RuntimeException("CLIから呼び出すときは打刻種別が必要です")
      }

      punch(punchTypeEnum, coop, token.toString).debug(
        "result: "
      ) >> ExitCode.Success.pure
