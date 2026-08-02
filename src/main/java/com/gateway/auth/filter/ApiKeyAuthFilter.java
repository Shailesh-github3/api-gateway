package com.gateway.auth.filter;

import com.gateway.apikey.entity.ApiKey;
import com.gateway.apikey.service.ApiKeyService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_ATTR = "API_KEY";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Extract the raw key from the header
        String rawKey = request.getHeader(API_KEY_HEADER);

        // 2. Short-circuit if missing or obviously malformed (length < 12)
        if (rawKey == null || rawKey.length() < 12) {
            log.warn("Authentication failed: Missing or invalid API key format");
            sendError(response, HttpStatus.UNAUTHORIZED, "Missing or invalid API key");
            return;
        }

        // 3. Verify the key (Prefix lookup + Hash comparison)
        ApiKey apiKey = apiKeyService.verify(rawKey).orElse(null);

        if (apiKey == null) {
            log.warn("Authentication failed: Invalid or revoked API key for prefix: {}", rawKey.substring(0, 8));
            sendError(response, HttpStatus.UNAUTHORIZED, "Invalid or revoked API key");
            return;
        }

        request.setAttribute(API_KEY_ATTR, apiKey);

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("Filter chain execution failed", e);
            sendError(response, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
    }
}