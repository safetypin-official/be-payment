package com.safetypin.payment.security;


import com.safetypin.payment.exception.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JWTUtil {
    private final Key key;

    public JWTUtil(@Value("${jwt.secret}") String secretKey) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    /**
     * Verifies the JWT token and returns the claims.
     *
     * @param token the JWT token
     * @return the claims from the token
     * @throws InvalidCredentialsException if the token is invalid or expired
     */
    public Claims verifyAndGetClaims(String token) throws InvalidCredentialsException {
        JwtParser parser = Jwts.parserBuilder()
                .setSigningKey(key)
                .build();

        // Check if token is valid and not expired
        Claims claims;
        try {
            claims = parser.parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            throw new InvalidCredentialsException("Invalid JWT token: " + e.getMessage());
        }

        // Check if token has expiration date
        Date expirationDate = claims.getExpiration();
        if (expirationDate == null) {
            throw new InvalidCredentialsException("Invalid JWT token: No expiration time");
        }

        return claims;
    }
}
