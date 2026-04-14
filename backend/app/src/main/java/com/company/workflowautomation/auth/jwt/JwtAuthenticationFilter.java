package com.company.workflowautomation.auth.jwt;

import com.company.workflowautomation.user.entity.UserEntity;
import com.company.workflowautomation.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final  JwtService jwtService;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public JwtAuthenticationFilter (JwtService jwtService,UserRepository userRepository,JdbcTemplate jdbcTemplate)
    {
        this.jwtService=jwtService;
        this.userRepository=userRepository;
        this.jdbcTemplate=jdbcTemplate;

    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/api/auth")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);
        final String email = jwtService.extractEmail(token);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            Optional<UserEntity> optionalUser = userRepository.findByEmail(email);

            if (optionalUser.isPresent()) {
                UserEntity user = optionalUser.get();

                if (jwtService.validateToken(token, user.getEmail())) {

                    UUID organizationId = jwtService.extractOrganizationId(token);
                    log.debug("JWT resolved organizationId={}", organizationId);

                    jdbcTemplate.queryForObject(
                            "SELECT set_config('app.current_organization', ?, false)",
                            String.class,
                            organizationId.toString()
                    );
                    Map<String,Object> principal = new HashMap<>();
                    principal.put("email",user.getEmail());
                    principal.put("userId",user.getId());
                    principal.put("organizationId",organizationId);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                     principal,
                                    null,
                                    Collections.emptyList()
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }



        filterChain.doFilter(request, response);
    }

}
