package com.company.workflowautomation.workflow;

import com.company.workflowautomation.auth.service.AuthenticationService;
import com.company.workflowautomation.auth.service.RegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authenticationService;

    @Test
    void testGetWorkflowsWithoutToken() throws Exception {

        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isForbidden());

        System.out.println("Unauthenticated request correctly rejected");
    }

    @Test
    void testGetWorkflowsWithToken() throws Exception {

        String testId = UUID.randomUUID().toString();

        String email = "workflow-test-" + testId + "@company.com";
        String organization = "Workflow Test Organization " + testId;

        RegistrationRequest request = new RegistrationRequest(
                email,
                "password123",
                organization
        );

        authenticationService.register(request);

        String loginResponse = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"" + email
                                                + "\",\"password\":\"password123\"}"
                                )
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = loginResponse
                .split("\"token\":\"")[1]
                .replace("\"}", "");

        mockMvc.perform(
                        get("/api/workflows")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk());

        System.out.println("Authenticated request successful");
    }
}