package com.company.workflowautomation.ai.dto;

import lombok.*;
import java.util.Map;
import java.util.UUID;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiRequest {
    private UUID workflowId;
    private UUID runId;
    private String status;
    private String errorType;
    private long durationMs;
    private Map<String,Object> records;
    private Map<String,Object> meta;
}
