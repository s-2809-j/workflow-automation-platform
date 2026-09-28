package com.company.workflowautomation.workflow.application;

import com.company.workflowautomation.workflow.jpa.WorkflowEntity;
import com.company.workflowautomation.workflow.jpa.WorkflowJpaRepository;
import com.company.workflowautomation.workflow.dto.CreateWorkflowRequest;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.company.workflowautomation.util.SecurityUtils;
@Service
public class WorkflowService {
    private final WorkflowJpaRepository workflowRepository;
    private final EntityManager entityManager;

    @Transactional
    public List<WorkflowEntity> getWorkflows(UUID userId, UUID organizationId) {
        setOrganizationContext(organizationId);
        return workflowRepository.findByOrganizationId(organizationId);
    }
    public WorkflowService(WorkflowJpaRepository workflowRepository, EntityManager entityManager)
    {
        this.workflowRepository=workflowRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public WorkflowEntity createWorkflow(CreateWorkflowRequest request, UUID userid, UUID organizationId)
    {
        setOrganizationContext(organizationId);
        WorkflowEntity workflow= new WorkflowEntity();
        workflow.setOrganizationId(organizationId);
        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setStatus("ACTIVE");
        workflow.setCreatedBy(userid);
        workflow.setCreatedAt(Instant.now());
        workflow.setUpdatedAt(Instant.now());
        System.out.println("SERVICE ORG ID: " + organizationId);
        return workflowRepository.save(workflow);
    }

    @Transactional
    public List<WorkflowEntity> getAllWorkflows(UUID organizationId) {
        setOrganizationContext(organizationId);
        return workflowRepository.findByOrganizationId(organizationId); // ✅ filter by org
    }

    private void setOrganizationContext(UUID organizationId) {
        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization', :orgId, true)")
                .setParameter("orgId", organizationId.toString())
                .getSingleResult();
    }


    @Transactional
    public WorkflowEntity getWorkflow(UUID id)
    {
        UUID organizationId = SecurityUtils.getOrganizationId();
        setOrganizationContext(organizationId);
        return workflowRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new RuntimeException("Workflow Not Found"));
    }

    @Transactional
    public WorkflowEntity updateWorkflow(UUID id,CreateWorkflowRequest request)
    {
        UUID organizationId = SecurityUtils.getOrganizationId();
        setOrganizationContext(organizationId);
        WorkflowEntity workflow = workflowRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new RuntimeException("Workflow Not Found"));
        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setUpdatedAt(Instant.now());
        return workflowRepository.save(workflow);
    }


    @Transactional
    public void deleteWorkflow(UUID id)
    {
        UUID organizationId = SecurityUtils.getOrganizationId();
        setOrganizationContext(organizationId);
        WorkflowEntity workflow = workflowRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new RuntimeException("Workflow Not Found"));
        workflowRepository.delete(workflow);
    }




}
