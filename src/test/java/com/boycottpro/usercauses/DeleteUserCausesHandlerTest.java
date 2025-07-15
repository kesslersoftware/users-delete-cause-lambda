package com.boycottpro.usercauses;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
                .withPathParameters(Map.of("user_id", userId, "cause_id", causeId));

        Map<String, AttributeValue> item = Map.of(
                "user_id", AttributeValue.fromS(userId),
                "cause_id", AttributeValue.fromS(causeId)
        );

        QueryResponse queryResponse = QueryResponse.builder().items(List.of(item)).build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);
        when(dynamoDb.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(BatchWriteItemResponse.builder().build());

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
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("cause_id", "cause123"));

        var result = handler.handleRequest(event, context);

        assertEquals(400, result.getStatusCode());
        assertTrue(result.getBody().contains("user_id not present"));
    }

    @Test
    void handleRequest_missingCauseId_returns400() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("user_id", "user123"));

        var result = handler.handleRequest(event, context);

        assertEquals(400, result.getStatusCode());
        assertTrue(result.getBody().contains("cause_id not present"));
    }

    @Test
    void handleRequest_dynamoException_returns500() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("user_id", "user123", "cause_id", "cause456"));

        when(dynamoDb.query(any(QueryRequest.class))).thenThrow(RuntimeException.class);

        var result = handler.handleRequest(event, context);

        assertEquals(500, result.getStatusCode());
        assertTrue(result.getBody().contains("Transaction failed"));
    }
}
