package com.filemanager.service.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "file-manager:lock:";

    /**
     * 加锁执行操作
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockKey);
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
            if (!acquired) {
                log.warn("获取分布式锁失败: key={}", lockKey);
                throw new RuntimeException("操作繁忙，请稍后重试");
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 加锁执行无返回值操作
     */
    public void executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Runnable runnable) {
        executeWithLock(lockKey, waitTime, leaseTime, unit, () -> {
            runnable.run();
            return null;
        });
    }
}
