package uk.gov.hmrc.apiplatform.deleteapi

import java.net.HttpURLConnection.HTTP_OK

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.api_platform_manage_api.AwsApiGatewayClient.awsApiGatewayClient
import uk.gov.hmrc.api_platform_manage_api.ErrorRecovery.recovery
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.ProxiedRequestHandler

import scala.language.postfixOps
import scala.util.Try

class DeleteApiHandler(apiGatewayClient: ApiGatewayClient) extends ProxiedRequestHandler {

  def this() {
    this(awsApiGatewayClient)
  }

  override def handleInput(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent = {
    Try(deleteApi(input)) recover recovery get
  }

  def deleteApi(requestEvent: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent = {
    val apiId = requestEvent.getPathParameters.get("api_id")
    val deleteApiRequest = DeleteRestApiRequest.builder().restApiId(apiId).build()
    apiGatewayClient.deleteRestApi(deleteApiRequest)

    new APIGatewayProxyResponseEvent()
      .withStatusCode(HTTP_OK)
      .withBody(toJson(DeleteApiResponse(apiId)))
  }
}

case class DeleteApiResponse(restApiId: String)
