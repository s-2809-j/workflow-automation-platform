package com.company.workflowautomation.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailRecipientConfigurationRequest(
        @NotBlank String stepId,
        @NotBlank String recipientSource,
        String recipient,
        String inputKey,
        String previousStepId,
        String outputField
) {
}
