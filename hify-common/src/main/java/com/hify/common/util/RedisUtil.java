package com.hify.common.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Redis utility class
 */
@Component
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== Basic Operations ====================

    /**
     * Get value by key
     */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    /**
     * Set value
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * Set value with expiration time
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * Delete key
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * Delete multiple keys
     */
    public long delete(Collection<String> keys) {
        Long count = redisTemplate.delete(keys);
        return count != null ? count : 0;
    }

    /**
     * Check if key exists
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Set expiration time
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    /**
     * Get expiration time
     */
    public long getExpire(String key) {
        Long expire = redisTemplate.getExpire(key);
        return expire != null ? expire : -1;
    }

    /**
     * Remove expiration time (persist key)
     */
    public boolean persist(String key) {
        return Boolean.TRUE.equals(redisTemplate.persist(key));
    }

    // ==================== String Operations ====================

    /**
     * Increment
     */
    public long increment(String key) {
        Long result = redisTemplate.opsForValue().increment(key);
        return result != null ? result : 0;
    }

    /**
     * Increment by delta
     */
    public long increment(String key, long delta) {
        Long result = redisTemplate.opsForValue().increment(key, delta);
        return result != null ? result : 0;
    }

    /**
     * Decrement
     */
    public long decrement(String key) {
        Long result = redisTemplate.opsForValue().decrement(key);
        return result != null ? result : 0;
    }

    /**
     * Decrement by delta
     */
    public long decrement(String key, long delta) {
        Long result = redisTemplate.opsForValue().decrement(key, delta);
        return result != null ? result : 0;
    }

    // ==================== Hash Operations ====================

    /**
     * Get hash value
     */
    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /**
     * Set hash value
     */
    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * Delete hash field
     */
    public long hDelete(String key, Object... fields) {
        return redisTemplate.opsForHash().delete(key, fields);
    }

    /**
     * Check if hash field exists
     */
    public boolean hHasKey(String key, String field) {
        return redisTemplate.opsForHash().hasKey(key, field);
    }

    // ==================== List Operations ====================

    /**
     * Push to list left
     */
    public long lLeftPush(String key, Object value) {
        Long size = redisTemplate.opsForList().leftPush(key, value);
        return size != null ? size : 0;
    }

    /**
     * Pop from list left
     */
    public Object lLeftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * Push to list right
     */
    public long lRightPush(String key, Object value) {
        Long size = redisTemplate.opsForList().rightPush(key, value);
        return size != null ? size : 0;
    }

    /**
     * Pop from list right
     */
    public Object lRightPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    /**
     * Get list size
     */
    public long lSize(String key) {
        Long size = redisTemplate.opsForList().size(key);
        return size != null ? size : 0;
    }

    // ==================== Set Operations ====================

    /**
     * Add to set
     */
    public long sAdd(String key, Object... values) {
        Long count = redisTemplate.opsForSet().add(key, values);
        return count != null ? count : 0;
    }

    /**
     * Remove from set
     */
    public long sRemove(String key, Object... values) {
        Long count = redisTemplate.opsForSet().remove(key, values);
        return count != null ? count : 0;
    }

    /**
     * Check if set contains value
     */
    public boolean sIsMember(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    /**
     * Get set size
     */
    public long sSize(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        return size != null ? size : 0;
    }
}
