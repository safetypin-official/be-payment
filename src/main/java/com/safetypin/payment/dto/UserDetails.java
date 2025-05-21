package com.safetypin.payment.dto;

import com.safetypin.payment.model.Role;
import io.jsonwebtoken.Claims;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@AllArgsConstructor
@Data
public class UserDetails {

    @Enumerated(EnumType.STRING)
    private Role role;
    private boolean isVerified;
    private UUID userId;
    private String name;

    public static UserDetails fromClaims(Claims claims) {
        String roleStr = claims.get("role", String.class);
        Role role = (roleStr != null) ? Role.valueOf(roleStr) : Role.REGISTERED_USER;

        Boolean isVerified = claims.get("isVerified", Boolean.class);
        if (isVerified == null) {
            isVerified = false;
        }
        String userIdStr = claims.get("userId", String.class);
        UUID userId;
        try {
            userId = (userIdStr != null) ? UUID.fromString(userIdStr) : UUID.randomUUID();
        } catch (IllegalArgumentException e) {
            // If the userId is not a valid UUID format, throw an exception
            throw new IllegalArgumentException("Invalid UUID format: " + userIdStr);
        }

        String name = claims.get("name", String.class);
        if (name == null) {
            name = "Anonymous User";
        }

        return new UserDetails(role, isVerified, userId, name);
    }
}

