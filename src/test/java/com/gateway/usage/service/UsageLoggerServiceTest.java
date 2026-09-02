package com.gateway.usage.service;

import com.gateway.usage.entity.UsageEvent;
import com.gateway.usage.repository.UsageEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsageLoggerServiceTest {

    @Mock
    private UsageEventRepository usageEventRepository;

    private UsageLoggerService usageLoggerService;

    @BeforeEach
    void setUp() {
        usageLoggerService = new UsageLoggerService(usageEventRepository);
    }

    @Test
    void logUsage_persistsEvent() {
        UsageEvent event = usageEvent(1L, "/v1/example-resource", 200, 12L);

        usageLoggerService.logUsage(event);

        verify(usageEventRepository).save(event);
    }

    @Test
    void logUsage_failureDoesNotPropagate() {
        UsageEvent event = usageEvent(2L, "/v1/example-resource", 200, 5L);
        doThrow(new DataAccessResourceFailureException("DB down"))
                .when(usageEventRepository).save(any(UsageEvent.class));

        // The client-facing path must never be broken by a logging failure.
        assertThatCode(() -> usageLoggerService.logUsage(event)).doesNotThrowAnyException();
    }

    private UsageEvent usageEvent(Long apiKeyId, String endpoint, int statusCode, long responseTimeMs) {
        UsageEvent event = new UsageEvent();
        event.setApiKeyId(apiKeyId);
        event.setEndpoint(endpoint);
        event.setHttpMethod("GET");
        event.setStatusCode(statusCode);
        event.setResponseTimeMs(responseTimeMs);
        return event;
    }
}