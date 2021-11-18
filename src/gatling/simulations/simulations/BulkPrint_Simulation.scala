package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.commons.stats.assertion.Assertion
import io.gatling.core.pause.PauseType
import scenarios._
import utils.Environment

import scala.concurrent.duration._

class BulkPrint_Simulation extends Simulation {

  val BaseURL = Environment.baseURL

  /* TEST TYPE DEFINITION */
  /* pipeline = nightly pipeline against the AAT environment (see the Jenkins_nightly file) */
  /* perftest (default) = performance test against the perftest environment */
  val testType = scala.util.Properties.envOrElse("TEST_TYPE", "perftest")

  //set the environment based on the test type
  val environment = testType match{
    case "perftest" => "perftest"
    case "pipeline" => "aat"
    case _ => "**INVALID**"
  }
  /* ******************************** */

  /* ADDITIONAL COMMAND LINE ARGUMENT OPTIONS */
  val debugMode = System.getProperty("debug", "off") //runs a single user e.g. ./gradle gatlingRun -Ddebug=on (default: off)
  val env = System.getProperty("env", environment) //manually override the environment aat|perftest e.g. ./gradle gatlingRun -Denv=aat
  /* ******************************** */

  /* PERFORMANCE TEST CONFIGURATION */
  val rampUpDurationMins = 5
  val rampDownDurationMins = 5
  val testDurationMins = 60

  val hourlyTarget:Double = 500
  val ratePerSec = hourlyTarget / 3600

  //If running in debug mode, disable pauses between steps
  val pauseOption:PauseType = debugMode match{
    case "off" => constantPauses
    case _ => disabledPauses
  }
  /* ******************************** */

  /* PIPELINE CONFIGURATION */
  val numberOfPipelineUsers:Double = 10
  /* ******************************** */

  val httpProtocol = Environment.HttpProtocol
    .baseUrl(BaseURL)

  before{
    println(s"Test Type: ${testType}")
    println(s"Test Environment: ${env}")
    println(s"Debug Mode: ${debugMode}")
  }

  val BulkPrintScenario = scenario("BulkPrint")
    .exitBlockOnFail {
      exec( _.set("env", s"${env}"))
      .exec(
        BulkPrint.Initialise,
        BulkPrint.Auth,
        BulkPrint.SendPrintRequest,
        BulkPrint.UploadFiles,
        BulkPrint.UploadManifest
      )
    }

    .exec {
      session =>
        println(session)
        session
    }

  //defines the Gatling simulation model, based on the inputs
  def simulationProfile(simulationType: String, userPerSecRate: Double, numberOfPipelineUsers: Double): Seq[OpenInjectionStep] = {
    simulationType match {
      case "perftest" =>
        if (debugMode == "off") {
          Seq(
            rampUsersPerSec(0.00) to (userPerSecRate) during (rampUpDurationMins minutes),
            constantUsersPerSec(userPerSecRate) during (testDurationMins minutes),
            rampUsersPerSec(userPerSecRate) to (0.00) during (rampDownDurationMins minutes)
          )
        }
        else{
          Seq(atOnceUsers(1))
        }
      case "pipeline" =>
        Seq(rampUsers(numberOfPipelineUsers.toInt) during (2 minutes))
      case _ =>
        Seq(nothingFor(0))
    }
  }

  //defines the test assertions, based on the test type
  def assertions(simulationType: String): Seq[Assertion] = {
    simulationType match {
      case "perftest" =>
        Seq(global.successfulRequests.percent.gte(99))
      case "pipeline" =>
        Seq(global.successfulRequests.percent.gte(100))
      case _ =>
        Seq()
    }
  }

  setUp(
    BulkPrintScenario.inject(simulationProfile(testType, ratePerSec, numberOfPipelineUsers)).pauses(pauseOption)
  ).protocols(httpProtocol)
    .assertions(assertions(testType))

}
