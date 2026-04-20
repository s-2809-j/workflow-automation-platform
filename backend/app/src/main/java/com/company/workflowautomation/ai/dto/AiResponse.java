package com.company.workflowautomation.ai.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import lombok.*;
import java.io.IOException;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonDeserialize(using = AiResponse.AiResponseDeserializer.class)
public class AiResponse {
    private Anomaly anomaly;
    private RetryDecision retryDecision;
    private String nextAction;

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class Anomaly {
        private String type;
        private String severity;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class RetryDecision {
        private boolean shouldRetry;
        private String strategy;
        private int maxRetries;
        private long baseDelayMs;
    }

    // Bridges the flat API response into your nested structure
    public static class AiResponseDeserializer extends StdDeserializer<AiResponse> {
        public AiResponseDeserializer() { super(AiResponse.class); }

        @Override
        public AiResponse deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            JsonNode root = p.getCodec().readTree(p);

            RetryDecision retryDecision = RetryDecision.builder()
                .shouldRetry(root.path("shouldRetry").asBoolean(false))
                .strategy(root.path("strategy").asText(null))
                .maxRetries(root.path("maxRetries").asInt(0))
                .baseDelayMs(root.path("baseDelayMs").asLong(0))
                .build();

            Anomaly anomaly = null;
            if (root.has("type") || root.has("severity")) {
                anomaly = Anomaly.builder()
                    .type(root.path("type").asText(null))
                    .severity(root.path("severity").asText(null))
                    .build();
            }

            return AiResponse.builder()
                .retryDecision(retryDecision)
                .anomaly(anomaly)
                .nextAction(root.path("reason").asText(null)) // "reason" → nextAction
                .build();
        }
    }
}