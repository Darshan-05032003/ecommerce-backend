package com.darshan.ecommerce;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.utility.DockerImageName;

// Separate from the MySQL configuration so that repository slices, which have no cache, do not start Redis.
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    RedisContainer redisContainer() {
        return new RedisContainer(DockerImageName.parse("redis:7-alpine"));
    }
}
