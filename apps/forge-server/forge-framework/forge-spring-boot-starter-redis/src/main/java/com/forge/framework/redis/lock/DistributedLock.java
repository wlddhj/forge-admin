package com.forge.framework.redis.lock;

import java.time.Duration;

/**
 * 分布式锁接口
 * 用于防止多实例重复执行任务
 *
 * @author forge-admin
 */
public interface DistributedLock {

    /**
     * 尝试获取锁
     *
     * @param lockKey 锁的唯一标识
     * @param timeout 锁超时时间
     * @return 是否成功获取锁
     */
    boolean tryLock(String lockKey, Duration timeout);

    /**
     * 释放锁
     *
     * @param lockKey 锁的唯一标识
     */
    void unlock(String lockKey);

    /**
     * 检查锁是否存在
     *
     * @param lockKey 锁的唯一标识
     * @return 锁是否存在
     */
    boolean isLocked(String lockKey);

    /**
     * 尝试获取锁（使用默认超时时间 5 分钟）
     *
     * @param lockKey 锁的唯一标识
     * @return 是否成功获取锁
     */
    default boolean tryLock(String lockKey) {
        return tryLock(lockKey, Duration.ofMinutes(5));
    }

    /**
     * 尝试获取锁并执行任务
     * 获取锁成功后执行任务，完成后自动释放锁
     *
     * @param lockKey 锁标识
     * @param timeout 锁超时时间
     * @param task 要执行的任务
     * @return 任务执行结果，如果获取锁失败返回 null
     */
    default <T> T executeWithLock(String lockKey, Duration timeout, java.util.function.Supplier<T> task) {
        if (!tryLock(lockKey, timeout)) {
            return null;
        }
        try {
            return task.get();
        } finally {
            unlock(lockKey);
        }
    }

    /**
     * 尝试获取锁并执行任务（无返回值）
     *
     * @param lockKey 锁标识
     * @param timeout 锁超时时间
     * @param task 要执行的任务
     * @return 是否成功执行任务
     */
    default boolean executeWithLock(String lockKey, Duration timeout, java.util.function.BooleanSupplier task) {
        if (!tryLock(lockKey, timeout)) {
            return false;
        }
        try {
            return task.getAsBoolean();
        } finally {
            unlock(lockKey);
        }
    }

    /**
     * 尝试获取锁并执行任务（Runnable）
     *
     * @param lockKey 锁标识
     * @param timeout 锁超时时间
     * @param task 要执行的任务
     * @return 是否成功获取锁并执行
     */
    default boolean runWithLock(String lockKey, Duration timeout, Runnable task) {
        if (!tryLock(lockKey, timeout)) {
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            unlock(lockKey);
        }
    }
}