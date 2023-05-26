package com.github.windymelt.ak4lambda

import cats.implicits._
import com.monovore.decline._

import java.util.UUID

object CLI {
  val tokenEnvOpt = Opts.env[UUID](
    "AK4_TOKEN",
    help = "Ak4 API Token",
    metavar = "xxxxxxxx-yyyy-zzzz-aaaa-bbbbbbbbbbbb"
  )
  val coopIdOpt = Opts.env[String](
    "AK4_COOP_ID",
    help = "Ak4 cooporate ID",
    metavar = "company1234"
  )
  val punchTypeOpt = Opts
    .argument[String]("on | off")
    .validate("punch type should be one of on | off")(Seq("on", "off").contains)
    .orNone
}
