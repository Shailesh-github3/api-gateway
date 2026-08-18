package com.gateway.demo.controller;

import com.gateway.apikey.entity.ApiKey;
import com.gateway.usage.entity.UsageEvent;
import com.gateway.usage.service.UsageLoggerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/example-resource")
@RequiredArgsConstructor
public class DemoResourceController {

    private final UsageLoggerService usageLoggerService;
    private static final String API_KEY_ATTR = "API_KEY";

    @GetMapping
    public ResponseEntity<String> getExample(HttpServletRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Execute the actual business logic
        String response = "Success";

        long endTime = System.currentTimeMillis();
        long responseTimeMs = endTime - startTime;

        // 2. Fire-and-forget async logging
        ApiKey apiKey = (ApiKey) request.getAttribute(API_KEY_ATTR);
        if (apiKey != null) {
            UsageEvent event = new UsageEvent();
            event.setApiKeyId(apiKey.getId());
            event.setEndpoint(request.getRequestURI());
            event.setHttpMethod(request.getMethod());
            event.setStatusCode(200);
            event.setResponseTimeMs(responseTimeMs);

            usageLoggerService.logUsage(event);
        }

        return ResponseEntity.ok(response);
    }
}