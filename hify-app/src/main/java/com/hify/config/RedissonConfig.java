package com.hify.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson configuration
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private String port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Bean
    public RedissonClient redissonClient() {
        String address = "redis://" + host + ":" + port;
        Config config = new Config();

        if (password == null || password.isEmpty()) {
            // No password
            config.useSingleServer()
                    .setAddress(address)
                    .setDatabase(database)
                    .setConnectionPoolSize(20)
                    .setConnectionMinimumIdleSize(5);
        } else {
            // With password
            config.useSingleServer()
                    .setAddress(address)
                    .setPassword(password)
                    .setDatabase(database)
                    .setConnectionPoolSize(20)
                    .setConnectionMinimumIdleSize(5);
        }

        return Redisson.create(config);
    }
}
