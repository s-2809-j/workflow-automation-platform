package com.company.workflowautomation.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class OrganizationEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationEntity() {
    }

    public OrganizationEntity(UUID id, String name, String slug, Instant now) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.status = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }
}
