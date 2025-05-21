package com.safetypin.payment.security;

import com.safetypin.payment.dto.UserDetails;
import com.safetypin.payment.exception.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class JWTFilter extends OncePerRequestFilter {
    private final JWTUtil jwtUtil;

    public JWTFilter(JWTUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // If token doesn't exist or is in wrong format, continue as unauthenticated
        if (authHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwtToken = authHeader.substring(7);

        // Verify the JWT token
        try {
            // Verify and get claims from token
            Claims claims = jwtUtil.verifyAndGetClaims(jwtToken);

            // Convert claims to UserDetails
            UserDetails userDetails = UserDetails.fromClaims(claims); // Create pre-authenticated token with authorities
                                                                      // based on user role
            PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
                    userDetails,
                    jwtToken, // credential (the token itself)
                    Collections.singletonList(new SimpleGrantedAuthority(userDetails.getRole().name())));

            // Set authentication in context
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (InvalidCredentialsException e) {
            // Log error if token is invalid but don't send error response - let Spring
            // Security handle it
            log.info("Invalid JWT token due to: {}", e.getMessage());
            // Clear any existing authentication
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            // Log other exceptions but don't send error response - let Spring Security
            // handle it
            log.error("Could not set user authentication in security context", e);
            // Clear any existing authentication
            SecurityContextHolder.clearContext();
        }

        // Continue the filter chain if token is processed (valid or not)
        filterChain.doFilter(request, response);
    }
}
