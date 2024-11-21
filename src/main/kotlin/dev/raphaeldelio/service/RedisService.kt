package dev.raphaeldelio.service

import redis.clients.jedis.JedisPool

class RedisService(private val jedisPool: JedisPool) {
    fun get(key: String): String? {
        jedisPool.resource.use { jedis ->
            return jedis.get(key)
        }
    }

    fun set(key: String, value: String, expireSeconds: Long? = null) {
        jedisPool.resource.use { jedis ->
            jedis.set(key, value)
            if (expireSeconds != null) {
                jedis.expire(key, expireSeconds)
            }
        }
    }

    fun setAdd(key: String, value: String) {
        jedisPool.resource.use { jedis ->
            jedis.sadd(key, value)
        }
    }

    fun setContains(key: String, value: String): Boolean {
        jedisPool.resource.use { jedis ->
            return jedis.sismember(key, value)
        }
    }
}