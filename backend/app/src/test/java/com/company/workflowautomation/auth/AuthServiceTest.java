package com.company.workflowautomation.auth;
import com.company.workflowautomation.auth.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AuthServiceTest {
    @Autowired
    private AuthenticationService authenticationService;

    @Test
    void testValidLogin() {
        // Replace with your actual test user credentials
        String token = authenticationService.login("test@company", "password");
        assertNotNull(token);
        assertFalse(token.isEmpty());
        System.out.println("✅ Login successful, token: " + token);
    }

    @Test
    void testInvalidLogin() {
        Exception exception = assertThrows(Exception.class, () -> {
            authenticationService.login("wrong@example.com", "wrong password");
        });
        assertNotNull(exception);
        System.out.println("✅ Invalid login correctly rejected: " + exception.getMessage());
    }
}
