package uk.gov.hmrc.apiplatform.deleteapi

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.api_platform_manage_api.AwsApiGatewayClient.awsApiGatewayClient
import uk.gov.hmrc.api_platform_manage_api.AwsIdRetriever
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.{JsonMapper, SqsHandler}

import scala.language.postfixOps

class DeleteApiHandler(override val apiGatewayClient: ApiGatewayClient) extends SqsHandler with AwsIdRetriever with JsonMapper {

  def this() {
    this(awsApiGatewayClient)
  }

  override def handleInput(event: SQSEvent, context: Context): Unit = {
    val logger = context.getLogger

    if (event.getRecords.size != 1) {
      throw new IllegalArgumentException(s"Invalid number of records: ${event.getRecords.size}")
    }

    val api = fromJson[Api](event.getRecords.get(0).getBody)
    getAwsRestApiIdByApiName(api.apiName) match {
      case Some(awsId) => apiGatewayClient.deleteRestApi(DeleteRestApiRequest.builder().restApiId(awsId).build())
      case None => logger.log(s"API with name ${api.apiName} not found")
    }
  }
}

case class Api(apiName: String)
