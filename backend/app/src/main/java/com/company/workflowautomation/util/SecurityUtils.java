package com.company.workflowautomation.util;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.UUID;

public class SecurityUtils {
    private static Map<String,Object> getPrincipalData(){
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("No authentication found in security context");
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof Map)) {
            throw new RuntimeException("Invalid authentication filter");
        }
        return (Map<String, Object>) principal;


    }

    public static UUID getUserId(){
        return UUID.fromString(getPrincipalData().get("userId").toString());
    }

    public static UUID getOrganizationId(){
        return UUID.fromString(getPrincipalData().get("organizationId").toString());
    }
}
