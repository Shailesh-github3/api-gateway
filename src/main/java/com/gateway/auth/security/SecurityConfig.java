package com.gateway.auth.security;

import com.gateway.apikey.service.ApiKeyService;
import com.gateway.auth.filter.ApiKeyAuthFilter;
import com.gateway.auth.filter.ScopeValidationFilter;
import com.gateway.ratelimit.filter.RateLimitFilter;
import com.gateway.ratelimit.service.RateLimitService;
import com.gateway.ratelimit.service.TierService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ApiKeyService apiKeyService;
    private final RateLimitService rateLimitService;
    private final TierService tierService;

    public SecurityConfig(ApiKeyService apiKeyService,
                          RateLimitService rateLimitService,
                          TierService tierService) {
        this.apiKeyService = apiKeyService;
        this.rateLimitService = rateLimitService;
        this.tierService = tierService;
    }

    // 1. Admin Chain: Matches ONLY /admin/**, uses Basic Auth
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/admin/**") // CRITICAL: Restricts this chain to /admin/ paths
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    // 2. API Chain: Matches everything else, uses custom API Key filters
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {

        // Instantiate filters directly here. DO NOT make them @Beans.
        // This prevents Spring Boot from globally registering them with Tomcat.
        ApiKeyAuthFilter apiKeyAuthFilter = new ApiKeyAuthFilter(apiKeyService);
        ScopeValidationFilter scopeValidationFilter = new ScopeValidationFilter();
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitService, tierService);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // Wire the instances directly into the chain
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(scopeValidationFilter, ApiKeyAuthFilter.class)
                .addFilterAfter(rateLimitFilter, ScopeValidationFilter.class);

        return http.build();
    }
}

/**

==================================================================================================



//package com.gateway.auth.security;
//
//import com.gateway.auth.filter.ApiKeyAuthFilter;
//import com.gateway.auth.filter.ScopeValidationFilter;
//import com.gateway.ratelimit.filter.RateLimitFilter;
//import lombok.RequiredArgsConstructor;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.annotation.Order;
//import org.springframework.security.config.Customizer;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
//
//@Configuration
//@EnableWebSecurity
//@RequiredArgsConstructor
//public class SecurityConfig {
//
//    private final ApiKeyAuthFilter apiKeyAuthFilter;
//    private final ScopeValidationFilter scopeValidationFilter;
//    private final RateLimitFilter rateLimitFilter;
//
//    @Bean
//    @Order(1) // Evaluated first
//    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
//        http
//                .securityMatcher("/admin/**")
//                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
//                .httpBasic(Customizer.withDefaults())
//                .csrf(AbstractHttpConfigurer::disable)
//                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
//        return http.build();
//    }
//
//    @Bean
//    @Order(2)
//    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
//        http
//                .csrf(AbstractHttpConfigurer::disable)
//                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
//                .formLogin(AbstractHttpConfigurer::disable)
//                .httpBasic(AbstractHttpConfigurer::disable)
//                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
//                .addFilterAfter(scopeValidationFilter, ApiKeyAuthFilter.class)
//                .addFilterAfter(rateLimitFilter, ScopeValidationFilter.class);
//        return http.build();
//    }
//}


 **/