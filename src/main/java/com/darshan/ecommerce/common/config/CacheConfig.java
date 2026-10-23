package com.darshan.ecommerce.common.config;

import com.darshan.ecommerce.category.CategoryResponse;
import com.darshan.ecommerce.product.ProductResponse;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

// Kept separate from the application class so that web and JPA slice tests do not enable caching.
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    // Each cache holds exactly one value type, so its serializer is bound to that type and the JSON
    // carries no class names. Polymorphic typing would be needed for a single serializer shared by all
    // caches, and reading class names from a shared store is a known deserialization attack surface.
    // The cache mapper is deliberately not the web mapper, so that changing the API's JSON settings
    // cannot make existing cache entries unreadable.
    @Bean
    public RedisCacheManagerBuilderCustomizer cacheConfigurationCustomizer(CacheProperties properties,
                                                                           RedisConnectionFactory connectionFactory) {
        JsonMapper mapper = JsonMapper.builder().build();
        JavaType categoryList = mapper.getTypeFactory().constructCollectionType(List.class, CategoryResponse.class);
        // With Lettuce, Spring Data Redis sends puts and evictions asynchronously by default, so a read
        // right after an admin change could still see the old entry. Immediate writes finish the Redis
        // command before the request returns. transactionAware defers puts and evictions until the
        // surrounding transaction commits, so a rolled-back admin update never touches the cache.
        // Unknown cache names fail fast instead of silently getting a cache without a TTL.
        return builder -> builder
                .cacheWriter(RedisCacheWriter.create(connectionFactory, writer -> writer.immediateWrites()))
                .transactionAware()
                .disableCreateOnMissingCache()
                .withCacheConfiguration(CacheNames.PRODUCTS, cache(properties.ttl().products(),
                        new JacksonJsonRedisSerializer<>(mapper, mapper.constructType(ProductResponse.class))))
                .withCacheConfiguration(CacheNames.CATEGORIES, cache(properties.ttl().categories(),
                        new JacksonJsonRedisSerializer<>(mapper, categoryList)));
    }

    private static RedisCacheConfiguration cache(Duration ttl, JacksonJsonRedisSerializer<?> serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeValuesWith(SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();
    }
}
