package com.github.windymelt.ak4lambda

import com.monovore.decline.*

import java.util.UUID

object CLI:
  val tokenEnvOpt: Opts[Option[UUID]] = Opts
    .env[UUID](
      "AK4_TOKEN",
      help =
        "Ak4 API Token. If omitted, token is retrieved from AWS Secret Manager.",
      metavar = "xxxxxxxx-yyyy-zzzz-aaaa-bbbbbbbbbbbb"
    )
    .orNone
  val coopIdOpt: Opts[String] = Opts.env[String](
    "AK4_COOP_ID",
    help = "Ak4 cooperate ID",
    metavar = "company1234"
  )
  val punchTypeOpt: Opts[Option[String]] = Opts
    .argument[String]("on | off")
    .validate("punch type should be one of on | off")(Seq("on", "off").contains)
    .orNone
  val secretArnOpt: Opts[String] = Opts.env[String](
    "AWS_SECRET_ARN",
    help = "AWS Secret Manager Secret ARN",
    metavar =
      "arn:aws:secretsmanager:ap-northeast-1:66666666:secret:secret/token-123456"
  )
