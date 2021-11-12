package scenarios

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import utils.{Common, Environment}
import java.util.UUID
import scala.collection.mutable

object BulkPrint {

  val BaseURL = Environment.baseURL
  val RpeAPIURL = Environment.rpeAPIURL

  val MinThinkTime = Environment.minThinkTime
  val MaxThinkTime = Environment.maxThinkTime

  val serviceFeeder = csv("services.csv").random

  /* Generate variables for manifest*/
  val Initialise = {

    //Set the options for number of documents and copies and each of their associated distribution
    // e.g. 1 -> 50.0, 2 -> 20.0, 3 -> 15.0 would mean 1 has a 50% chance of being chosen, 2 has 20% chance, etc.
    //Note: Distributions don't have to add up to 100
    val numberOfDocumentsDistribution = Map(1 -> 35.0, 2 -> 35.0, 3 -> 20.0, 4 -> 10.0)
    val numberOfCopiesDistribution = Map(1 -> 50.0, 2 -> 20.0, 3 -> 15.0, 4 -> 10.0, 5 -> 5.0)
    //Use a mutable list to store each document JSON element e.g. {"file_name": "1.pdf","copies_required": 2}
    val docRequestJSON = mutable.ListBuffer.empty[String]

    //Select the number of documents in the manifest
    val numberOfDocuments = Common.sample(numberOfDocumentsDistribution)

    //For each document, select the number of copies, generate the JSON element and store in a list
    repeat(numberOfDocuments, "count"){
      exec { session =>
        val numberOfCopies = Common.sample(numberOfCopiesDistribution)
        val index = session("count").as[Int] + 1
        docRequestJSON += s"""{"file_name":"${index}.pdf","copies_required":${numberOfCopies}}"""
        session
      }
    }

    /*Join all of the document JSON list elements (comma-separate them) and save into the session
    Example:
      {
        "file_name": "1.pdf",
        "copies_required": 2
      },
      {
        "file_name": "2.pdf",
        "copies_required": 1
      }
     */
    .exec(_.set("docRequestJSON", docRequestJSON.mkString(",")))

  }

  val Auth = {

    feed(serviceFeeder)
      .exec(http("BulkPrint_010_Auth")
        .post(RpeAPIURL + "/testing-support/lease")
        .body(StringBody("""{"microservice":"${service}"}""")).asJson
        .check(bodyString.saveAs("authToken")))

  }

  val SendPrintRequest = {

    exec(_.set("guid", UUID.randomUUID().toString))

    .exec(http("BulkPrint_020_SendPrintRequest")
      .put(BaseURL + "/print-jobs/${guid}")
      .headers(Map(
        "ServiceAuthorization" -> "Bearer ${authToken}",
        "Accept" -> "application/json",
        "Content-Type" -> "application/vnd.uk.gov.hmcts.letter-service.in.print-job.v1+json"))
      .body(ElFileBody("bodies/PrintRequest.json"))
      .check(bodyString.saveAs("manifestJSON"))
      .check(jsonPath("$.print_job.id").saveAs("id"))
      .check(jsonPath("$.print_job.documents[*].upload_to_path").findAll.saveAs("uploadPaths"))
      .check(jsonPath("$.upload.upload_to_container").saveAs("uploadContainer"))
      .check(jsonPath("$.upload.sas").saveAs("sas"))
      .check(jsonPath("$.upload.manifest_path").saveAs("manifestPath")))

  }

  val UploadFiles = {

    foreach("${uploadPaths}", "path") {

      exec(http("BulkPrint_030_UploadFile")
        .put("${uploadContainer}/${path}?${sas}")
        .headers(Map(
          "x-ms-write" -> "update",
          "x-ms-blob-type" -> "BlockBlob"))
        .body(RawFileBody("test.pdf"))
        .check(status.is(201)))

    }

  }

  val UploadManifest = {

    exec(http("BulkPrint_040_UploadManifest")
      .put("${uploadContainer}/${manifestPath}?${sas}")
      .headers(Map(
        "x-ms-write" -> "update",
        "x-ms-blob-type" -> "BlockBlob"))
      .body(StringBody("${manifestJSON}")).asJson
      .check(status.is(201)))

  }

}
