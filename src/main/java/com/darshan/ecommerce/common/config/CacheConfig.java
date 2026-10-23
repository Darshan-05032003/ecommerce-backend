package com.darshan.ecommerce.common.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

// Kept separate from the application class so that web and JPA slice tests do not enable caching.
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    // Cached values are response records, stored as JSON with a type hint so that Spring Cache
    // can read them back as the right class. The validator limits which classes the hint may name,
    // because deserializing arbitrary class names from a shared store is a known attack surface.
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.darshan.ecommerce.")
                        .allowIfSubType("java.util.")
                        .build())
                .build();
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();
    }

    // transactionAware defers puts and evictions to after the surrounding transaction commits, so
    // a rolled-back admin update never evicts, and an eviction never runs before the new row is visible.
    // Unknown cache names fail fast instead of silently getting a cache without a TTL.
    @Bean
    public RedisCacheManagerBuilderCustomizer cacheTtlCustomizer(RedisCacheConfiguration defaults,
                                                                 CacheProperties properties) {
        return builder -> builder
                .transactionAware()
                .disableCreateOnMissingCache()
                .withCacheConfiguration(CacheNames.PRODUCTS, defaults.entryTtl(properties.ttl().products()))
                .withCacheConfiguration(CacheNames.CATEGORIES, defaults.entryTtl(properties.ttl().categories()));
    }
}
