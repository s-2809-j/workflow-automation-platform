package com.company.workflowautomation.auth;

import com.company.workflowautomation.auth.service.AuthenticationService;
import com.company.workflowautomation.auth.service.RegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AuthServiceTest {

    @Autowired
    private AuthenticationService authenticationService;

    @Test
    void testValidLogin() {

        String testId = UUID.randomUUID().toString();
        String email = "test-" + testId + "@company.com";
        String organization = "Test Organization " + testId;

        RegistrationRequest request = new RegistrationRequest(
                email,
                "password123",
                organization
        );

        authenticationService.register(request);

        String token = authenticationService.login(
                email,
                "password123"
        );

        assertNotNull(token);
        assertFalse(token.isEmpty());

        System.out.println("Login successful, token generated.");
    }

    @Test
    void testInvalidLogin() {

        Exception exception = assertThrows(Exception.class, () -> {
            authenticationService.login(
                    "wrong@example.com",
                    "wrongpassword"
            );
        });

        assertNotNull(exception);

        System.out.println(
                "Invalid login correctly rejected: "
                        + exception.getMessage()
        );
    }
}