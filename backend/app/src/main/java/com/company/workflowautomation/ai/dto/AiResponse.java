package com.company.workflowautomation.ai.dto;

import lombok.*;

import java.util.Map;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiResponse {
   private Anomaly anomaly;
   private RetryDecision retryDecision;
   private String nextAction;

   @Getter
   @Setter
   @AllArgsConstructor
   @NoArgsConstructor
   @Builder
   public static class Anomaly{
       private String type;
       private String severity;
   }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
   public static class RetryDecision{
       private boolean shouldRetry;
       private String strategy;
       private int maxRetries;
       private long baseDelayMs;
   }
}
