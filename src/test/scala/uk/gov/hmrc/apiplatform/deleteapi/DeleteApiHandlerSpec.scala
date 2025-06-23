package uk.gov.hmrc.apiplatform.deleteapi

import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model._
import scala.jdk.CollectionConverters._
import uk.gov.hmrc.api_platform_manage_api.utils.JsonMapper
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class DeleteApiHandlerSpec extends AnyWordSpec with Matchers with MockitoSugar with JsonMapper {

  trait Setup {
    val apiId: String = UUID.randomUUID().toString
    val apiName = "foo--1.0"

    val requestBody = s"""{"apiName": "$apiName"}"""
    val message = new SQSMessage()
    message.setBody(requestBody)
    val sqsEvent = new SQSEvent()
    sqsEvent.setRecords(List(message).asJava)

    val mockAPIGatewayClient: ApiGatewayClient = mock[ApiGatewayClient]
    val deleteApiHandler = new DeleteApiHandler(mockAPIGatewayClient)
    val mockContext = mock[Context]
    when(mockContext.getLogger).thenReturn(mock[LambdaLogger])
  }

  "Delete API Handler" should {
    "delete the API definition from API Gateway when found" in new Setup {
      when(mockAPIGatewayClient.getRestApis(any[GetRestApisRequest])).thenReturn(buildMatchingRestApisResponse(apiId, apiName))
      val deleteRequestCaptor: ArgumentCaptor[DeleteRestApiRequest] = ArgumentCaptor.forClass(classOf[DeleteRestApiRequest])
      when(mockAPIGatewayClient.deleteRestApi(deleteRequestCaptor.capture())).thenReturn(DeleteRestApiResponse.builder().build())

      deleteApiHandler.handleInput(sqsEvent, mockContext)

      deleteRequestCaptor.getValue.restApiId shouldEqual apiId
    }

    "not do anything when API is not found" in new Setup {
      when(mockAPIGatewayClient.getRestApis(any[GetRestApisRequest])).thenReturn(buildNonMatchingRestApisResponse(1))

      deleteApiHandler.handleInput(sqsEvent, mockContext)

      verify(mockAPIGatewayClient, times(0)).deleteRestApi(any[DeleteRestApiRequest])
    }

    "throw an Exception if multiple messages have been retrieved from SQS" in new Setup {
      sqsEvent.setRecords(List(message, message).asJava)

      val exception = intercept[IllegalArgumentException](deleteApiHandler.handleInput(sqsEvent, mockContext))

      exception.getMessage shouldEqual "Invalid number of records: 2"
    }

    "throw an Exception if no messages have been retrieved from SQS" in new Setup {
      sqsEvent.setRecords(List().asJava)

      val exception = intercept[IllegalArgumentException](deleteApiHandler.handleInput(sqsEvent, mockContext))

      exception.getMessage shouldEqual "Invalid number of records: 0"
    }

    "propagate any exceptions thrown by SDK" in new Setup {
      when(mockAPIGatewayClient.getRestApis(any[GetRestApisRequest])).thenReturn(buildMatchingRestApisResponse(apiId, apiName))

      val errorMessage = "You're an idiot"
      when(mockAPIGatewayClient.deleteRestApi(any[DeleteRestApiRequest])).thenThrow(UnauthorizedException.builder().message(errorMessage).build())

      val exception = intercept[UnauthorizedException](deleteApiHandler.handleInput(sqsEvent, mockContext))

      exception.getMessage shouldEqual errorMessage
    }
  }

  def buildMatchingRestApisResponse(matchingId: String, matchingName: String): GetRestApisResponse = {
    GetRestApisResponse.builder()
      .items(RestApi.builder().id(matchingId).name(matchingName).build())
      .build()
  }

  def buildNonMatchingRestApisResponse(count: Int): GetRestApisResponse = {
    val items: Seq[RestApi] = (1 to count).map(c => RestApi.builder().id(s"$c").name(s"Item $c").build())

    GetRestApisResponse.builder()
      .items(items.asJava)
      .build()
  }
}
