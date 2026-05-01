package com.company.workflowautomation.auth.api;

import com.company.workflowautomation.auth.service.AuthenticationService;
import com.company.workflowautomation.auth.service.LoginRequest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public static class LoginResponse {
        public String token;

        public LoginResponse(String token) {
            this.token = token;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        System.out.println("LOGIN START");
        String token = authenticationService.login(
                request.email(),
                request.password()
        );

        System.out.println("LOGIN SUCCESS");

        return ResponseEntity.ok(new LoginResponse(token));
    }
}