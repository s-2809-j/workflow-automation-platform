package com.company.workflowautomation.shared.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    @GetMapping
    public Map<String,Object> health()
        {
        Map<String, Object> response = new HashMap<>();
        response.put("status","Up");
        response.put("service","Workflow-automation-backend");
        response.put("timestamp", Instant.now().toString());
        return response;
    }


}
