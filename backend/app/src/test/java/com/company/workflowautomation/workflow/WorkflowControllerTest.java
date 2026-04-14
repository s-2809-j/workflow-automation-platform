package com.company.workflowautomation.workflow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class WorkflowControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetWorkflowsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isForbidden());
        System.out.println("✅ Unauthenticated request correctly rejected");
    }

    @Test
    void testGetWorkflowsWithToken() throws Exception {
        // First get a token
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@company\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract token (simple string parsing)
        String token = loginResponse.split("\"token\":\"")[1].replace("\"}", "");

        // Use token to get workflows
        mockMvc.perform(get("/api/workflows")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        System.out.println("✅ Authenticated request successful");
    }
}
