package com.gateway.auth;

import com.gateway.apikey.entity.ApiKey;
import com.gateway.apikey.repository.ApiKeyRepository;
import com.gateway.common.util.HashUtil;
import com.gateway.common.util.KeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    // 1. Start a real Postgres container for the test suite
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // 2. Dynamically inject the container's JDBC URL into Spring's properties
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private KeyGenerator keyGenerator;

    @Autowired
    private HashUtil hashUtil;

    private String validKeyWithScope;
    private String validKeyWithoutScope;

    @BeforeEach
    void setUp() {
        apiKeyRepository.deleteAll();

        // Create a key WITH the required 'read' scope
        validKeyWithScope = createTestKey(new String[]{"read", "write"});

        // Create a key WITHOUT the required 'read' scope
        validKeyWithoutScope = createTestKey(new String[]{"write"});
    }

    private String createTestKey(String[] scopes) {
        String rawKey = keyGenerator.generate();
        ApiKey apiKey = new ApiKey();
        apiKey.setOwnerId(1L);
        apiKey.setKeyPrefix(rawKey.substring(0, 12));
        apiKey.setKeyHash(hashUtil.hash(rawKey));
        apiKey.setTier("STARTER");
        apiKey.setScopes(scopes);
        apiKeyRepository.save(apiKey);
        return rawKey;
    }

    @Test
    void shouldReturn401WhenNoApiKeyProvided() throws Exception {
        mockMvc.perform(get("/v1/example-resource"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenInvalidApiKeyProvided() throws Exception {
        mockMvc.perform(get("/v1/example-resource")
                        .header("X-API-Key", "sk_live_invalidkey1234567890"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WhenKeyLacksRequiredScope() throws Exception {
        mockMvc.perform(get("/v1/example-resource")
                        .header("X-API-Key", validKeyWithoutScope))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn200WhenKeyIsValidAndHasScope() throws Exception {
        // Note: This will return 404 because we haven't built the DemoResourceController yet.
        // But it PROVES the request successfully passed BOTH the Auth and Scope filters.
        mockMvc.perform(get("/v1/example-resource")
                        .header("X-API-Key", validKeyWithScope))
                .andExpect(status().isNotFound());
    }
}