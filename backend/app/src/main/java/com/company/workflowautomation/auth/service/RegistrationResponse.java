package com.company.workflowautomation.auth.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class RegistrationResponse {
    private final String token;
    private final String message;
    private final UUID userId;
    private final UUID organizationId;
}
