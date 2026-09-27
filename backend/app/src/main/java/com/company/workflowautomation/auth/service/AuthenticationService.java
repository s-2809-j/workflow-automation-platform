package com.company.workflowautomation.auth.service;

import com.company.workflowautomation.auth.jwt.JwtService;
import com.company.workflowautomation.organization.entity.OrganizationEntity;
import com.company.workflowautomation.organization.repository.OrganizationRepository;
import com.company.workflowautomation.user.entity.UserEntity;
import com.company.workflowautomation.user.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OrganizationRepository organizationRepository;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                 JwtService jwtService, OrganizationRepository organizationRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.organizationRepository = organizationRepository;
    }

    public String login(String email, String password) {
        UserEntity user = userRepository.findByEmail(email).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        boolean match = passwordEncoder.matches(password, user.getPasswordHash());

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return jwtService.generateToken(user);
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new AccountAlreadyExistsException(
                    "An account with email " + request.email() + " already exists"
            );
        }

        Instant now = Instant.now();

        // Derive a simple slug from the organization name
        String slug = request.organizationName().toLowerCase().replaceAll("[^a-z0-9]+", "-");

        OrganizationEntity organization = new OrganizationEntity(UUID.randomUUID(), request.organizationName(), slug, now);
        OrganizationEntity savedOrganization = organizationRepository.save(organization);

        UserEntity user = new UserEntity(
                UUID.randomUUID(),
                savedOrganization.getId(),
                request.email(),
                passwordEncoder.encode(request.password()),
                "ACTIVE",
                now,
                now
        );
        UserEntity savedUser = userRepository.save(user);

        String token = jwtService.generateToken(savedUser);

        return new RegistrationResponse(token, "Account created successfully", savedUser.getId(), savedUser.getOrganizationId());
    }
}
