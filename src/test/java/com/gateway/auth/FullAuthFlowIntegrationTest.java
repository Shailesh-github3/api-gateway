package com.gateway.auth;

import com.gateway.BaseIntegrationTest;
import com.gateway.apikey.dto.ApiKeyCreateRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class FullAuthFlowIntegrationTest extends BaseIntegrationTest {

    private static final int BURST_CAPACITY = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void fullFlow_auth_scope_ratelimit() {
        // 1. Create a STARTER key (60 req/min, burst 10)
        ApiKeyCreateRequest createRequest = new ApiKeyCreateRequest();
        createRequest.setOwnerId(1L);
        createRequest.setTier("STARTER");
        createRequest.setScopes(new String[]{"read"});

        ResponseEntity<String> createResponse = restTemplate
                .withBasicAuth("admin", "admin")
                .postForEntity("http://localhost:" + port + "/admin/api-keys", createRequest, String.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String rawKey = extractKeyFromResponse(createResponse.getBody());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", rawKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. Consume the full burst (BURST_CAPACITY requests) — every one must succeed.
        for (int i = 1; i <= BURST_CAPACITY; i++) {
            ResponseEntity<String> response = callResource(entity);
            assertThat(response.getStatusCode())
                    .as("request %d of %d within burst capacity should succeed", i, BURST_CAPACITY)
                    .isEqualTo(HttpStatus.OK);
        }

        // 3. The next request exceeds the burst capacity → 429 Too Many Requests
        ResponseEntity<String> rateLimitedResponse = callResource(entity);
        assertThat(rateLimitedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rateLimitedResponse.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(rateLimitedResponse.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
    }

    private ResponseEntity<String> callResource(HttpEntity<String> entity) {
        return restTemplate.exchange(
                "http://localhost:" + port + "/v1/example-resource",
                HttpMethod.GET,
                entity,
                String.class
        );
    }

    private String extractKeyFromResponse(String body) {
        return JsonPath.read(body, "$.apiKey");
    }
}