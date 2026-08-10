package com.gateway.auth.filter;

import com.gateway.apikey.entity.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Slf4j
//@Component
@RequiredArgsConstructor
public class ScopeValidationFilter extends OncePerRequestFilter {

    private static final String API_KEY_ATTR = "API_KEY";
    private static final String REQUIRED_SCOPE = "read";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only enforce scopes on protected /v1/ endpoints
        if (!request.getRequestURI().startsWith("/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        ApiKey apiKey = (ApiKey) request.getAttribute(API_KEY_ATTR);

        // Fail-safe: If AuthFilter somehow failed to attach the key, reject.
        if (apiKey == null) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Unauthenticated");
            return;
        }

        boolean hasScope = Arrays.asList(apiKey.getScopes()).contains(REQUIRED_SCOPE);

        if (!hasScope) {
            log.warn("Authorization failed: Missing scope '{}' for key prefix: {}",
                    REQUIRED_SCOPE, apiKey.getKeyPrefix());
            sendError(response, HttpStatus.FORBIDDEN, "Missing required scope: " + REQUIRED_SCOPE);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
    }
}