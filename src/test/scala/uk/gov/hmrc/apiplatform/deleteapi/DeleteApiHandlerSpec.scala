package uk.gov.hmrc.apiplatform.deleteapi

import java.net.HttpURLConnection.{HTTP_OK, HTTP_UNAUTHORIZED}
import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper

import scala.collection.JavaConversions.mapAsJavaMap

class DeleteApiHandlerSpec extends WordSpecLike with Matchers with MockitoSugar with JsonMapper {

  trait Setup {
    val mockAPIGatewayClient: ApiGatewayClient = mock[ApiGatewayClient]
    val addApiHandler = new DeleteApiHandler(mockAPIGatewayClient)
  }

  "Delete API Handler" should {
    "delete the API definition from API Gateway" in new Setup {
      val id: String = UUID.randomUUID().toString
      val apiGatewayResponse: DeleteRestApiResponse = DeleteRestApiResponse.builder().build()
      when(mockAPIGatewayClient.deleteRestApi(any[DeleteRestApiRequest])).thenReturn(apiGatewayResponse)

      val response: APIGatewayProxyResponseEvent = addApiHandler.handleInput(new APIGatewayProxyRequestEvent()
        .withHttpMethod("DELETE")
        .withPathParamters(mapAsJavaMap(Map("api_id" -> id)))
      )

      response.getStatusCode shouldEqual HTTP_OK
      response.getBody shouldEqual s"""{"restApiId":"$id"}"""
    }

    "correctly handle UnauthorizedException thrown by AWS SDK when deleting API" in new Setup {
      val errorMessage = "You're an idiot"
      val id: String = UUID.randomUUID().toString
      when(mockAPIGatewayClient.deleteRestApi(any[DeleteRestApiRequest])).thenThrow(UnauthorizedException.builder().message(errorMessage).build())

      val response: APIGatewayProxyResponseEvent = addApiHandler.handleInput(new APIGatewayProxyRequestEvent()
        .withHttpMethod("DELETE")
        .withPathParamters(mapAsJavaMap(Map("api_id" -> id)))
      )

      response.getStatusCode shouldEqual HTTP_UNAUTHORIZED
      response.getBody shouldEqual errorMessage
    }
  }
}
