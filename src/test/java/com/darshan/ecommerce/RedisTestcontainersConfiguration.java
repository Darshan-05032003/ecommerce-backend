package com.darshan.ecommerce;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.utility.DockerImageName;

// Shared like the MySQL container, but kept separate so that repository slices, which have no
// cache, never start Redis.
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestcontainersConfiguration {

    private static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    static {
        REDIS.start();
    }

    @Bean
    DynamicPropertyRegistrar redisProperties() {
        return registry -> {
            registry.add("spring.data.redis.host", REDIS::getHost);
            registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        };
    }
}
