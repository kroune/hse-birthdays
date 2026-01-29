@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package io.github.kroune.bot.cache

import io.github.kroune.common.logging.Loggers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val logger = Loggers.bot

/**
 * TTL-based cache that automatically expires entries after a specified duration.
 * Sessions are lazily cleaned up when accessed or periodically by the cleanup job.
 *
 * @param K the type of keys in the cache
 * @param V the type of values in the cache
 * @param ttlMs the time-to-live duration in milliseconds (default: 1 hour)
 */
class TTLCache<K, V>(
    val ttlMs: Long = TimeUnit.HOURS.toMillis(1)
) {
    private inner class CacheEntry(
        val value: V,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - createdAt > ttlMs
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
    private val cache = ConcurrentHashMap<K, CacheEntry>()

    /**
     * Start a periodic cleanup task that removes expired entries.
     * This should be called once at application startup.
     *
     * @param cleanupIntervalMs the interval at which to run cleanup (default: 30 seconds)
     */
    fun startCleanupTask(cleanupIntervalMs: Long = TimeUnit.SECONDS.toMillis(30)) {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(cleanupIntervalMs)
                cleanup()
            }
        }
        logger.info { "TTL cache cleanup task started (TTL: ${ttlMs}ms, cleanup interval: ${cleanupIntervalMs}ms)" }
    }

    /**
     * Get a value from the cache.
     * Returns null if the key doesn't exist or the entry has expired.
     * Expired entries are automatically removed.
     */
    operator fun get(key: K): V? {
        val entry = cache[key] ?: return null
        return if (entry.isExpired()) {
            cache.remove(key)
            logger.debug { "Expired cache entry removed for key: $key" }
            null
        } else {
            entry.value
        }
    }

    /**
     * Put a value in the cache.
     * Overwrites any existing value for the key.
     */
    operator fun set(key: K, value: V) {
        cache[key] = CacheEntry(value)
    }

    /**
     * Put a value in the cache only if the key doesn't exist or is expired.
     * Returns the value that was in the cache (existing or new).
     */
    fun getOrPut(key: K, defaultValue: () -> V): V {
        val existing = get(key)
        return if (existing != null) {
            existing
        } else {
            val newValue = defaultValue()
            this[key] = newValue
            newValue
        }
    }

    /**
     * Remove a value from the cache.
     * Returns the removed value or null if the key didn't exist.
     */
    fun remove(key: K): V? {
        return cache.remove(key)?.value
    }


    /**
     * Manually run cleanup to remove all expired entries.
     * Returns the number of entries that were removed.
     */
    fun cleanup(): Int {
        val keysToRemove = cache.filter { (_, entry) -> entry.isExpired() }.keys
        keysToRemove.forEach { cache.remove(it) }

        if (keysToRemove.isNotEmpty()) {
            logger.debug { "Cleanup: removed ${keysToRemove.size} expired entries from cache. Remaining: ${cache.size}" }
        }

        return keysToRemove.size
    }
}

