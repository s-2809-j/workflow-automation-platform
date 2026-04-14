package com.company.workflowautomation.auth.service;

import com.company.workflowautomation.auth.jwt.JwtService;
import com.company.workflowautomation.user.entity.UserEntity;
import com.company.workflowautomation.user.repository.UserRepository;
import org.apache.catalina.User;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(UserRepository userRepository , PasswordEncoder passwordEncoder, JwtService jwtService)
    {
        this.userRepository=userRepository;
        this.passwordEncoder=passwordEncoder;
        this.jwtService=jwtService;
    }

    public String login(String email,String password){

        UserEntity user = userRepository.findByEmail(email).orElseThrow(()-> new BadCredentialsException("Invalid credentials"));
        boolean match = passwordEncoder.matches(password, user.getPasswordHash());

        if(!passwordEncoder.matches(password, user.getPasswordHash()))
        {
            throw new BadCredentialsException("Invalid credentials");
        }
        return jwtService.generateToken(user);
    }
}
