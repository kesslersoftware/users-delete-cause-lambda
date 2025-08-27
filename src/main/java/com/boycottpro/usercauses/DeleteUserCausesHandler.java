package com.boycottpro.usercauses;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.utilities.JwtUtility;
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
        String sub = null;
        try {
            sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) return response(401, Map.of("message", "Unauthorized"));
            Map<String, String> pathParams = event.getPathParameters();
            String causeId = (pathParams != null) ? pathParams.get("cause_id") : null;
            if (causeId == null || causeId.isEmpty()) {
                ResponseMessage message = new ResponseMessage(400,
                        "sorry, there was an error processing your request",
                        "cause_id not present");
                return response(400, message);
            }
            boolean success = deleteUserCauses(sub,causeId);
            if(success) {
                decrementCauseRecord(causeId);
            }
            return response(200, Map.of("message",
                    "cause unfollowed successfully."));
        } catch (Exception e) {
            System.out.println(e.getMessage() + " for user " + sub);
            return response(500,Map.of("error", "Unexpected server error: " + e.getMessage()) );
        }
    }

    private APIGatewayProxyResponseEvent response(int status, Object body) {
        String responseBody = null;
        try {
            responseBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(responseBody);
    }

    private boolean decrementCauseRecord(String causeId) {
        try {
            int delta = -1;

            Map<String, AttributeValue> key = Map.of("cause_id", AttributeValue.fromS(causeId));
            Map<String, AttributeValue> values = new HashMap<>();
            values.put(":delta", AttributeValue.fromN(Integer.toString(delta)));
            values.put(":zero", AttributeValue.fromN("0"));

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName("causes")
                    .key(key)
                    .updateExpression("SET follower_count = if_not_exists(follower_count, :zero) + :delta")
                    .expressionAttributeValues(values)
                    .conditionExpression("attribute_exists(cause_id)")
                    .build();

            dynamoDb.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            System.err.println("Cause not found: " + causeId);
            throw e;
        } catch (DynamoDbException e) {
            e.printStackTrace();
            throw e;
        }
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
