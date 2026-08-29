package com.gateway.ratelimit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.JedisPool;

@Configuration
public class RedisConfig {

    @Bean
    public JedisPool jedisPool(RedisConnectionFactory connectionFactory) {
        // Extract the JedisPool from Spring's auto-configured factory
        // This avoids creating a second connection pool
        if (connectionFactory instanceof JedisConnectionFactory jcf) {
            return (JedisPool) jcf.getPool();
        }
        throw new IllegalStateException("Expected JedisConnectionFactory but got: "
                + connectionFactory.getClass().getName()
                + ". Ensure spring.data.redis.client-type=jedis is set.");
    }
}