package com.gateway.apikey;

import com.gateway.BaseIntegrationTest;
import com.gateway.apikey.dto.ApiKeyCreateRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCrudIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void adminCrud_create_list_revoke() {
        // 1. Create a key
        ApiKeyCreateRequest createRequest = new ApiKeyCreateRequest();
        createRequest.setOwnerId(42L);
        createRequest.setTier("STARTER");
        createRequest.setScopes(new String[]{"read"});

        ResponseEntity<String> createResponse = restTemplate
                .withBasicAuth("admin", "admin")
                .postForEntity(url("/admin/api-keys"), createRequest, String.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String rawKey = JsonPath.read(createResponse.getBody(), "$.apiKey");

        // 2. List keys -> the created key must be present, WITHOUT the hash field
        ResponseEntity<List<Map<String, Object>>> listResponse = restTemplate
                .withBasicAuth("admin", "admin")
                .exchange(url("/admin/api-keys"), HttpMethod.GET, HttpEntity.EMPTY,
                        new ParameterizedTypeReference<>() {});

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotEmpty();

        Map<String, Object> createdKeyEntry = listResponse.getBody().stream()
                .filter(k -> "read".equals(((List<?>) k.get("scopes")).get(0).toString()))
                .filter(k -> ((Number) k.get("ownerId")).longValue() == 42L)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Created key not present in list"));

        // key_hash must never be serialized
        assertThat(createdKeyEntry).doesNotContainKey("keyHash");
        assertThat(createdKeyEntry).doesNotContainKey("key_hash");

        Long keyId = ((Number) createdKeyEntry.get("id")).longValue();

        // 3. Revoke the key -> 204 No Content
        ResponseEntity<Void> revokeResponse = restTemplate
                .withBasicAuth("admin", "admin")
                .exchange(url("/admin/api-keys/" + keyId), HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 4. The key must now be rejected on the protected endpoint
        HttpHeaders keyedHeaders = new HttpHeaders();
        keyedHeaders.set("X-API-Key", rawKey);
        HttpEntity<Void> keyedRequest = new HttpEntity<>(keyedHeaders);
        ResponseEntity<String> protectedResponse = restTemplate.exchange(
                url("/v1/example-resource"), HttpMethod.GET, keyedRequest, String.class);
        assertThat(protectedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminUnreachableWithoutBasicAuth() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/admin/api-keys"), new ApiKeyCreateRequest(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}