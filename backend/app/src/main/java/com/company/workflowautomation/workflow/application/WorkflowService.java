package com.company.workflowautomation.workflow.application;

import com.company.workflowautomation.workflow.jpa.WorkflowEntity;
import com.company.workflowautomation.workflow.jpa.WorkflowJpaRepository;
import com.company.workflowautomation.workflow.dto.CreateWorkflowRequest;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {
    private final WorkflowJpaRepository workflowRepository;
    private final EntityManager entityManager;

    public List<WorkflowEntity> getWorkflows(UUID userId, UUID organizationId) {
        return workflowRepository.findByOrganizationId(organizationId);
    }
    public WorkflowService(WorkflowJpaRepository workflowRepository, EntityManager entityManager)
    {
        this.workflowRepository=workflowRepository;
        this.entityManager = entityManager;
    }

    public WorkflowEntity createWorkflow(CreateWorkflowRequest request, UUID userid, UUID organizationId)
    {
        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization', :orgId, false)"
                )
                .setParameter("orgId", organizationId.toString())
                .getSingleResult();
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

    public List<WorkflowEntity> getAllWorkflows(UUID organizationId) {
        return workflowRepository.findByOrganizationId(organizationId); // ✅ filter by org
    }


    public WorkflowEntity getWorkflow(UUID id)
    {
        return workflowRepository.findById(id).orElseThrow(()-> new RuntimeException("Workflow Not Found"));
    }

    public WorkflowEntity updateWorkflow(UUID id,CreateWorkflowRequest request)
    {
        WorkflowEntity workflow = getWorkflow(id);
        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setUpdatedAt(Instant.now());
        return workflowRepository.save(workflow);
    }


    public void deleteWorkflow(UUID id)
    {
        WorkflowEntity workflow = getWorkflow(id);
        workflowRepository.delete(workflow);
    }




}
