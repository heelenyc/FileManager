package com.filemanager.web.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Configuration
public class Bucket4jConfig {

    @Bean
    public ConcurrentMap<String, Bucket> rateLimitBuckets() {
        return new ConcurrentHashMap<>();
    }

    public static Bucket createDefaultBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(100,
                        Refill.intervally(100, Duration.ofMinutes(1))))
                .build();
    }
}
