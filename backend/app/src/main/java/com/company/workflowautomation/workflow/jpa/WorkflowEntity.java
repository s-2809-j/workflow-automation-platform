package com.company.workflowautomation.workflow.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name ="workflow")
public class WorkflowEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name="organization_id",nullable = false)
    private UUID organizationId;

    @Column(name="name",nullable = false)
    private String name;

    @Column(name="description",nullable = false)
    private String description;

    @Column(name="status",nullable = false)
    private String status;

    @Column(name="created_by",nullable = false)
    private UUID createdBy;
    @Column(name="created_at",nullable = false)
    private Instant createdAt;

    @Column(name="updated_at",nullable = false)
    private Instant updatedAt;


}

