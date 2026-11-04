package com.gmart.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Enables Spring caching. With the default profile the cache lives in Redis
 * (stored as readable JSON); with the "h2" profile it falls back to in-memory.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS = "products";
    public static final String PRODUCT_BY_ID = "productById";
    public static final String PRODUCTS_BY_CATEGORY = "productsByCategory";
    public static final String CATEGORIES = "categories";
    public static final String CATEGORY_BY_ID = "categoryById";

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .prefixCacheNameWith("gmart:")
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
