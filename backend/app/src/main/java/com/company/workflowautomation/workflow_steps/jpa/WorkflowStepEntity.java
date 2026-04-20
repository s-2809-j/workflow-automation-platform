package com.company.workflowautomation.workflow_steps.jpa;


import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "workflow_steps")
@Getter
@Setter
public class WorkflowStepEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    private UUID id;

    @Column(name="organization_id",nullable = false)
    private UUID organizationId;

    @Column(name = "workflow_id",nullable = false)
    private UUID workflowId;

    @Column(name = "step_order",nullable = false)
    private int stepOrder;

    private String name;
    @Column(name = "step_type",nullable = false)
    private String stepType;

    @JdbcTypeCode((SqlTypes.JSON))
    @Column(name="config",columnDefinition = "jsonb")
    private JsonNode config;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode dependsOn;

    @Column(name = "should_fail")
    private boolean shouldFail;

    public List<String> getDependsOnList() {
    if (dependsOn == null || !dependsOn.isArray()) {
        return Collections.emptyList();
    }
    List<String> list = new ArrayList<>();
    for (JsonNode node : dependsOn) {
        list.add(node.asText());
    }
    return list;
}
public List<UUID> getDependsOnUUIDs() {
    if (dependsOn == null || !dependsOn.isArray()) {
        return Collections.emptyList();
    }
    List<UUID> list = new ArrayList<>();
    for (JsonNode node : dependsOn) {
        try {
            list.add(UUID.fromString(node.asText()));
        } catch (IllegalArgumentException e) {
            // skip AI-generated placeholder IDs like "step-1"
        }
    }
    return list;
}
}
