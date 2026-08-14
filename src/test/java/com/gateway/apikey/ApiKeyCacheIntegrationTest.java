package com.gateway.apikey;

import com.gateway.apikey.dto.ApiKeyCreateRequest;
import com.gateway.apikey.repository.ApiKeyRepository;
import com.gateway.apikey.service.ApiKeyMetadataCache;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiKeyCacheIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApiKeyMetadataCache metadataCache;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Test
    void revokeShouldImmediatelyEvictCache() {

        // ---------------------------------------------------------
        // 1. Create an API key via admin endpoint
        // ---------------------------------------------------------

        ApiKeyCreateRequest createRequest = new ApiKeyCreateRequest();

        createRequest.setOwnerId(1L);
        createRequest.setTier("STARTER");
        createRequest.setScopes(new String[]{"read"});

        ResponseEntity<String> createResponse = restTemplate
                .withBasicAuth("admin", "admin")
                .postForEntity(
                        url("/admin/api-keys"),
                        createRequest,
                        String.class
                );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(createResponse.getBody()).isNotNull();

        String rawKey = extractKeyFromResponse(createResponse.getBody());

        assertThat(rawKey).isNotBlank();


        // ---------------------------------------------------------
        // 2. Use the API key once to populate the cache
        // ---------------------------------------------------------

        HttpHeaders apiKeyHeaders = new HttpHeaders();
        apiKeyHeaders.set("X-API-Key", rawKey);

        HttpEntity<Void> apiKeyRequest = new HttpEntity<>(apiKeyHeaders);

        ResponseEntity<String> authResponse =
                restTemplate.exchange(
                        url("/v1/example-resource"),
                        HttpMethod.GET,
                        apiKeyRequest,
                        String.class
                );

        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);


        // ---------------------------------------------------------
        // 3. Verify that the cache entry exists
        // ---------------------------------------------------------

        String prefix = rawKey.substring(0, 12);

        assertThat(metadataCache.get(prefix)).isPresent();


        // ---------------------------------------------------------
        // 4. Get the API key ID from the database
        // ---------------------------------------------------------

        Long keyId = getKeyIdFromPrefix(prefix);

        assertThat(keyId).isNotNull();


        // ---------------------------------------------------------
        // 5. Revoke the API key
        // ---------------------------------------------------------

        ResponseEntity<Void> revokeResponse =
                restTemplate
                        .withBasicAuth("admin", "admin")
                        .exchange(
                                url("/admin/api-keys/" + keyId),
                                HttpMethod.DELETE,
                                HttpEntity.EMPTY,
                                Void.class
                        );

        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);


        // ---------------------------------------------------------
        // 6. CRITICAL:
        //    Cache entry must be removed immediately
        // ---------------------------------------------------------

        assertThat(metadataCache.get(prefix)).isEmpty();


        // ---------------------------------------------------------
        // 7. Verify that the revoked key is rejected
        // ---------------------------------------------------------

        HttpHeaders revokedKeyHeaders = new HttpHeaders();
        revokedKeyHeaders.set("X-API-Key", rawKey);

        HttpEntity<Void> revokedKeyRequest = new HttpEntity<>(revokedKeyHeaders);

        ResponseEntity<String> revokedResponse =
                restTemplate.exchange(
                        url("/v1/example-resource"),
                        HttpMethod.GET,
                        revokedKeyRequest,
                        String.class
                );

        assertThat(revokedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }


    // -------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String extractKeyFromResponse(String body) {
        // Navigates directly to $.apiKey
        return JsonPath.read(body, "$.apiKey");
    }

    private Long getKeyIdFromPrefix(String prefix) {
        return apiKeyRepository
                .findByKeyPrefix(prefix)
                .orElseThrow()
                .getId();
    }
}