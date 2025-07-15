package com.boycottpro.usercauses;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.ResponseMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeleteUserCausesHandler implements
        RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final String TABLE_NAME = "";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeleteUserCausesHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public DeleteUserCausesHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            Map<String, String> pathParams = event.getPathParameters();
            String userId = (pathParams != null) ? pathParams.get("user_id") : null;
            String causeId = (pathParams != null) ? pathParams.get("cause_id") : null;
            if (userId == null || userId.isEmpty()) {
                ResponseMessage message = new ResponseMessage(400,
                        "sorry, there was an error processing your request",
                        "user_id not present");
                String responseBody = objectMapper.writeValueAsString(message);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(Map.of("Content-Type", "application/json"))
                        .withBody(responseBody);
            }
            if (causeId == null || causeId.isEmpty()) {
                ResponseMessage message = new ResponseMessage(400,
                        "sorry, there was an error processing your request",
                        "cause_id not present");
                String responseBody = objectMapper.writeValueAsString(message);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(Map.of("Content-Type", "application/json"))
                        .withBody(responseBody);
            }
            boolean success = deleteUserCauses(userId,causeId);
            return response(200, "cause unfollowed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return response(500, "Transaction failed: " + e.getMessage());
        }
    }
    private APIGatewayProxyResponseEvent response(int status, String body)  {
        ResponseMessage message = new ResponseMessage(status,body,
                body);
        String responseBody = null;
        try {
            responseBody = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(responseBody);
    }
    private boolean deleteUserCauses(String userId, String causeId) {
        try {
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName("user_causes")
                    .keyConditionExpression("user_id = :uid AND cause_id = :cid")
                    .expressionAttributeValues(Map.of(
                            ":uid", AttributeValue.fromS(userId),
                            ":cid", AttributeValue.fromS(causeId)
                    ))
                    .build();
            QueryResponse queryResponse = dynamoDb.query(queryRequest);
            List<WriteRequest> deleteRequests = new ArrayList<>();
            for (Map<String, AttributeValue> item : queryResponse.items()) {
                Map<String, AttributeValue> key = new HashMap<>();
                key.put("user_id", item.get("user_id"));
                key.put("cause_id", item.get("cause_id"));
                deleteRequests.add(WriteRequest.builder()
                        .deleteRequest(DeleteRequest.builder().key(key).build())
                        .build());
            }
            for (int i = 0; i < deleteRequests.size(); i += 25) {
                List<WriteRequest> batch = deleteRequests.subList(i, Math.min(i + 25, deleteRequests.size()));
                BatchWriteItemRequest batchRequest = BatchWriteItemRequest.builder()
                        .requestItems(Map.of("user_causes", batch))
                        .build();
                dynamoDb.batchWriteItem(batchRequest);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
