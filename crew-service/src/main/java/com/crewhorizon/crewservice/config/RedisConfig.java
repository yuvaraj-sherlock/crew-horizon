package com.crewhorizon.crewservice.config;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
/**
 * WHY custom RedisConfig:
 * Spring Boot auto-configures a basic RedisTemplate with JDK serialization.
 * JDK serialization is not human-readable and creates coupling between
 * cache and Java class versions. We configure JSON serialization for:
 * 1. Human-readable cache values (debuggable with redis-cli)
 * 2. Compatibility across service versions (no serialVersionUID coupling)
 * 3. Cross-language compatibility if needed
 */
@Configuration
@EnableCaching
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("crew-members", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("crew-members-list", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        return RedisCacheManager.builder(factory).cacheDefaults(defaultConfig).withInitialCacheConfigurations(cacheConfigs).build();
    }
}
