package scenarios

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import utils.Environment
import java.util.UUID

object BulkPrint {

  val BaseURL = Environment.baseURL
  val RpeAPIURL = Environment.rpeAPIURL

  val MinThinkTime = Environment.minThinkTime
  val MaxThinkTime = Environment.maxThinkTime

  val serviceFeeder = csv("services.csv").random

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
        .body(RawFileBody("2MB.pdf"))
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
