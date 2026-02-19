package com.kvstore.server.config;

import com.kvstore.core.Cache;
import com.kvstore.core.LRUCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, String> cache(
            @Value("${kv.cache.capacity:1000}") int capacity) {
        return new LRUCache<>(capacity);
    }
}

