package com.filemanager.web.config;

import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final ConcurrentMap<String, Bucket> rateLimitBuckets;

    /**
     * 获取或创建限流桶：每分钟允许 100 次请求
     */
    public Bucket resolveBucket(String key) {
        return rateLimitBuckets.computeIfAbsent(key,
                k -> Bucket4jConfig.createDefaultBucket());
    }
}
