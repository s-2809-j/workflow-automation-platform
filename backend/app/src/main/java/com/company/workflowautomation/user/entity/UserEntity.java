package com.company.workflowautomation.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @Column(name = "id",nullable = false,updatable = false)
    private UUID id;

    @Column(name="organization_id",nullable = false)
    private UUID organizationId;

    @Column(name = "email",nullable = false,unique = true)
    private String email;

    @Column(name = "password_hash",nullable = false)
    private String passwordHash;

    @Column(name = "status",nullable = false)
    private String status;

    @Column(name = "created_at",nullable = false,updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at",nullable = false)
    private Instant updatedAt;
    protected UserEntity(){

    }
    public UserEntity(
            UUID id,
            UUID organizationId,
            String email,
            String passwordHash,
            String status,
            Instant createdAt,
            Instant updatedAt
    )
    {
        this.id=id;
        this.organizationId=organizationId;
        this.email=email;
        this.passwordHash=passwordHash;
        this.status=status;
        this.createdAt=createdAt;
        this.updatedAt=updatedAt;
    }

    public UUID getId(){
        return id;
    }

    public UUID getOrganizationId(){
        return organizationId;

    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash(){
        return passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
