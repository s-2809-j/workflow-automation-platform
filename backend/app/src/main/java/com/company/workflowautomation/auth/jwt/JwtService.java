package com.company.workflowautomation.auth.jwt;

import com.company.workflowautomation.user.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final Key signingKey;
    private final long expiration;

    public  JwtService(@Value("${jwt.secret}") String secret,@Value("${jwt.expiration}") long expiration)
    {
        this.signingKey= Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expiration=expiration;
    }

    public String generateToken(UserEntity user)
    {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime()+expiration);
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId",user.getId().toString())
                .claim("organizationId",user.getOrganizationId().toString())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        String userId = extractAllClaims(token)
                .get("userId", String.class);
        return UUID.fromString(userId);
    }

    public UUID extractOrganizationId(String token) {
        String orgId = extractAllClaims(token)
                .get("organizationId", String.class);
        return UUID.fromString(orgId);
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token)
                .getExpiration()
                .before(new Date());
    }

    public boolean validateToken(String token, String email) {
        return extractEmail(token).equals(email)
                && !isTokenExpired(token);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
