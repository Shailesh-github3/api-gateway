package com.gateway.ratelimit.filter;

import com.gateway.apikey.entity.ApiKey;
import com.gateway.ratelimit.entity.RateLimitTier;
import com.gateway.ratelimit.service.RateLimitService;
import com.gateway.ratelimit.service.TierService;
import io.github.bucket4j.ConsumptionProbe;
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
//@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final TierService tierService;
    private static final String API_KEY_ATTR = "API_KEY";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ApiKey apiKey = (ApiKey) request.getAttribute(API_KEY_ATTR);

        // Bypass rate limiting for unauthenticated requests (e.g., admin endpoints)
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe = rateLimitService.tryConsume(apiKey);

        if (probe.isConsumed()) {
            RateLimitTier tier = tierService.getTier(apiKey.getTier());
            long limit = tier.getRequestsPerMinute();
            long remaining = probe.getRemainingTokens();

            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            RateLimitTier tier = tierService.getTier(apiKey.getTier());

            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setHeader("X-RateLimit-Limit", String.valueOf(tier.getRequestsPerMinute()));
            response.setHeader("X-RateLimit-Remaining", "0");

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Rate limit exceeded\"}");
        }
    }
}