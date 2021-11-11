package utils

import io.gatling.core.Predef._
import io.gatling.http.Predef._

object Environment {

  val baseURL = "http://rpe-send-letter-service-${env}.service.core-compute-${env}.internal"
  val rpeAPIURL = "http://rpe-service-auth-provider-${env}.service.core-compute-${env}.internal"

  val minThinkTime = 5
  val maxThinkTime = 7

  val HttpProtocol = http

}
