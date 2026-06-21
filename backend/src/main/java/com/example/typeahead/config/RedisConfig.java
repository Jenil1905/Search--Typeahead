package com.example.typeahead.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * <h1>RedisConfig</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This class configures how Spring interacts with Redis. It defines a {@link RedisTemplate} bean
 * which allows programmatic access to Redis operations.
 * 
 * <h3>What problem it solves:</h3>
 * By default, Spring Boot auto-configures a RedisTemplate that uses JDK serialization (which produces
 * binary data prefixed with java class metadata like \xac\xed\x00\x05). This makes keys and values
 * unreadable inside the Redis CLI. This config solves that by overriding serializers to use human-readable UTF-8 Strings.
 * 
 * <h3>How it works internally:</h3>
 * <ul>
 *   <li>{@link StringRedisSerializer} is used for both Keys and Values, converting Java Strings to raw UTF-8 bytes in Redis.</li>
 *   <li>The <code>RedisConnectionFactory</code> is automatically injected by Spring Boot using the parameters
 *       from <code>application.yml</code>.</li>
 * </ul>
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: What is the default serializer in Spring Data Redis, and what is its drawback?</b><br>
 *       A: The default serializer is <code>JdkSerializationRedisSerializer</code>. Its drawback is that keys/values are saved
 *       as binary payloads, which are hard to inspect in Redis CLI and are not language-agnostic (cannot be read by Node/Python).</li>
 *   <li><b>Q: What are Redis data structures?</b><br>
 *       A: Redis supports Strings, Lists, Sets, Sorted Sets (ZSet), Hashes, HyperLogLogs, and Bitmaps. In this project,
 *       we mainly use Redis Strings to cache JSON suggestion arrays.</li>
 * </ol>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for both keys and values to ensure human-readability in Redis CLI
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}

/*
 ==================================================================================
 VIVA NOTES: REDIS CONFIGURATION AND SERIALIZATION
 ==================================================================================
 1. In production, caching must be fast. Serializers define the overhead of converting Java objects
    to network bytes. JSON serialization (Jackson) or String serialization are preferred.
 2. We use StringRedisSerializer here. Complex structures (like lists of suggestion objects)
    are serialized into JSON strings before saving and parsed back on retrieval.
 ==================================================================================
*/
