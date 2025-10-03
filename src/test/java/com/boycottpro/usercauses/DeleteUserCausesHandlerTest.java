package com.boycottpro.usercauses;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import com.fasterxml.jackson.core.JsonProcessingException;

@ExtendWith(MockitoExtension.class)
public class DeleteUserCausesHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @InjectMocks
    private DeleteUserCausesHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void handleRequest_validPathParams_deletesRecord() {
        // Arrange
        String userId = "user123";
        String causeId = "cause456";

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of( "cause_id", causeId));
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);


        Map<String, AttributeValue> item = Map.of(
                "user_id", AttributeValue.fromS(userId),
                "cause_id", AttributeValue.fromS(causeId)
        );

        QueryResponse queryResponse = QueryResponse.builder().items(List.of(item)).build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);
        when(dynamoDb.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(BatchWriteItemResponse.builder().build());
        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());
        // Act
        var result = handler.handleRequest(event, context);

        // Assert
        assertEquals(200, result.getStatusCode());
        assertTrue(result.getBody().contains("cause unfollowed successfully."));

        // Capture and assert the query request
        ArgumentCaptor<QueryRequest> queryCaptor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDb).query(queryCaptor.capture());
        QueryRequest capturedQuery = queryCaptor.getValue();
        assertEquals("user_causes", capturedQuery.tableName());
        assertTrue(capturedQuery.expressionAttributeValues().containsKey(":uid"));
        assertTrue(capturedQuery.expressionAttributeValues().containsKey(":cid"));

        // Verify batch delete called
        verify(dynamoDb, times(1)).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    @Test
    void handleRequest_missingUserId_returns400() {
        APIGatewayProxyRequestEvent event = null;

        var response = handler.handleRequest(event, mock(Context.class));

        assertEquals(401, response.getStatusCode());
        assertTrue(response.getBody().contains("Unauthorized"));
    }

    @Test
    void handleRequest_missingCauseId_returns400() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        var result = handler.handleRequest(event, context);

        assertEquals(400, result.getStatusCode());
        assertTrue(result.getBody().contains("cause_id not present"));
    }

    @Test
    void handleRequest_dynamoException_returns500() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of( "cause_id", "cause456"));
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        when(dynamoDb.query(any(QueryRequest.class))).thenThrow(RuntimeException.class);

        var response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Unexpected server error"));
    }

    @Test
    public void testDefaultConstructor() {
        // Test the default constructor coverage
        // Note: This may fail in environments without AWS credentials/region configured
        try {
            DeleteUserCausesHandler handler = new DeleteUserCausesHandler();
            assertNotNull(handler);

            // Verify DynamoDbClient was created (using reflection to access private field)
            try {
                Field dynamoDbField = DeleteUserCausesHandler.class.getDeclaredField("dynamoDb");
                dynamoDbField.setAccessible(true);
                DynamoDbClient dynamoDb = (DynamoDbClient) dynamoDbField.get(handler);
                assertNotNull(dynamoDb);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                fail("Failed to access DynamoDbClient field: " + e.getMessage());
            }
        } catch (software.amazon.awssdk.core.exception.SdkClientException e) {
            // AWS SDK can't initialize due to missing region configuration
            // This is expected in Jenkins without AWS credentials - test passes
            System.out.println("Skipping DynamoDbClient verification due to AWS SDK configuration: " + e.getMessage());
        }
    }

    @Test
    public void testUnauthorizedUser() {
        // Test the unauthorized block coverage
        handler = new DeleteUserCausesHandler(dynamoDb);

        // Create event without JWT token (or invalid token that returns null sub)
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        // No authorizer context, so JwtUtility.getSubFromRestEvent will return null

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertEquals(401, response.getStatusCode());
        assertTrue(response.getBody().contains("Unauthorized"));
    }

    @Test
    public void testJsonProcessingExceptionInResponse() throws Exception {
        // Test JsonProcessingException coverage in response method by using reflection
        handler = new DeleteUserCausesHandler(dynamoDb);

        // Use reflection to access the private response method
        java.lang.reflect.Method responseMethod = DeleteUserCausesHandler.class.getDeclaredMethod("response", int.class, Object.class);
        responseMethod.setAccessible(true);

        // Create an object that will cause JsonProcessingException
        Object problematicObject = new Object() {
            public Object writeReplace() throws java.io.ObjectStreamException {
                throw new java.io.NotSerializableException("Not serializable");
            }
        };

        // Create a circular reference object that will cause JsonProcessingException
        Map<String, Object> circularMap = new HashMap<>();
        circularMap.put("self", circularMap);

        // This should trigger the JsonProcessingException -> RuntimeException path
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            try {
                responseMethod.invoke(handler, 500, circularMap);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw new RuntimeException(e.getCause());
            }
        });

        // Verify it's ultimately caused by JsonProcessingException
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof JsonProcessingException,
                "Expected JsonProcessingException, got: " + cause.getClass().getSimpleName());
    }

    @Test
    public void testEmptyCauseId() {
        // Test line 47: when cause_id is empty string
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        // Set empty string cause_id
        event.setPathParameters(Map.of("cause_id", ""));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("cause_id not present"));
    }

    @Test
    public void testConditionalCheckFailedExceptionInDecrementCauseRecord() {
        // Test lines 98-99: ConditionalCheckFailedException in decrementCauseRecord
        String causeId = "cause456";

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("cause_id", causeId));
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        // Mock successful deleteUserCauses
        Map<String, AttributeValue> item = Map.of(
                "user_id", AttributeValue.fromS("user123"),
                "cause_id", AttributeValue.fromS(causeId)
        );
        QueryResponse queryResponse = QueryResponse.builder().items(List.of(item)).build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);
        when(dynamoDb.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(BatchWriteItemResponse.builder().build());

        // Mock ConditionalCheckFailedException on updateItem (decrementCauseRecord)
        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("Cause does not exist").build());

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Unexpected server error"));
    }

    @Test
    public void testDynamoDbExceptionInDecrementCauseRecord() {
        // Test lines 100-102: DynamoDbException in decrementCauseRecord
        String causeId = "cause456";

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("cause_id", causeId));
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        // Mock successful deleteUserCauses
        Map<String, AttributeValue> item = Map.of(
                "user_id", AttributeValue.fromS("user123"),
                "cause_id", AttributeValue.fromS(causeId)
        );
        QueryResponse queryResponse = QueryResponse.builder().items(List.of(item)).build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);
        when(dynamoDb.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(BatchWriteItemResponse.builder().build());

        // Mock generic DynamoDbException on updateItem (decrementCauseRecord)
        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Unexpected server error"));
    }

}
