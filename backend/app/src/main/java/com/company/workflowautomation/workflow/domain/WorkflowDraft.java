package com.company.workflowautomation.workflow.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_draft")
@Getter
@NoArgsConstructor
public class WorkflowDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID organizationId;

    // Stores the AI-generated workflow as a JSON string
    @Column(name = "json_content", nullable = false, columnDefinition = "TEXT")
    private String jsonContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // The only constructor — always requires both fields
    public WorkflowDraft(String jsonContent, UUID organizationId) {
        this.jsonContent = jsonContent;
        this.organizationId = organizationId;
        this.createdAt = Instant.now();
    }
}