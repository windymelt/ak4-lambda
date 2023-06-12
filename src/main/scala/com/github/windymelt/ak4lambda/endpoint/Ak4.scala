package com.github.windymelt.ak4lambda.endpoint

import com.github.nscala_time.time.Imports._
import com.github.windymelt.ak4lambda.endpoint.codec.DateTime._
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.auto._
import sttp.tapir.SchemaType.SString
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object Ak4 {
  private val authInput: EndpointInput[String] = query[String]("token")
  private lazy val base =
    endpoint.securityIn(authInput).in("api" / "cooperation")

  lazy val punch =
    base.post
      .in(path[String] / "stamps")
      .in(jsonBody[StampInput])
      .out(jsonBody[StampOutput])
      .errorOut(jsonBody[ErrorOutput])

  enum StampType(val code: Long) {
    case 出勤 extends StampType(11)
    case 退勤 extends StampType(12)
    case 直行 extends StampType(21)
    case 直帰 extends StampType(22)
    case 休憩入 extends StampType(31)
    case 休憩出 extends StampType(32)
  }

  val JST = +9
  // Schemaではどのlow levelな表現に対応するかだけ示せばよい(実際の変換はcirceのcodecが行う)
  implicit val DTSchema: Schema[org.joda.time.DateTime] =
    Schema
      .string[org.joda.time.DateTime]

  type TZString = "+09:00" // we fixed it
  implicit val TZStringSchema: Schema[TZString] = Schema.string[TZString]
  case class StampInput(
      `type`: Int,
      stampedAt: DateTime,
      timezone: TZString
  )

  case class StampOutput(
      success: Boolean,
      response: StampResponse,
      errors: Option[Seq[ErrorResponse]]
  )

  case class ErrorOutput(
      success: Boolean,
      errors: Seq[ErrorResponse]
  )

  case class StampResponse(
      login_company_code: String,
      staff_id: Long,
      `type`: Long,
      stampedAt: String
  )
  case class ErrorResponse(code: String, message: String)
}
