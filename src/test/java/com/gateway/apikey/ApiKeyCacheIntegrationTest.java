package com.gateway.apikey;

import com.gateway.BaseIntegrationTest;
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

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiKeyCacheIntegrationTest extends BaseIntegrationTest {

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

        String prefix = rawKey.substring(0, 12);
        assertThat(metadataCache.get(prefix)).isPresent();

        Long keyId = getKeyIdFromPrefix(prefix);
        assertThat(keyId).isNotNull();

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

        assertThat(metadataCache.get(prefix)).isEmpty();

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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String extractKeyFromResponse(String body) {
        return JsonPath.read(body, "$.apiKey");
    }

    private Long getKeyIdFromPrefix(String prefix) {
        return apiKeyRepository
                .findByKeyPrefix(prefix)
                .orElseThrow()
                .getId();
    }
}
