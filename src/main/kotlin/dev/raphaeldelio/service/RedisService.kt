package dev.raphaeldelio.service

import org.http4k.format.Jackson
import redis.clients.jedis.JedisPooled

class RedisService(private val jedisPooled: JedisPooled) {
    fun get(key: String): String? {
        return jedisPooled.get(key)
    }

    fun set(key: String, value: String, expireSeconds: Long? = null) {
        jedisPooled.set(key, value)
        if (expireSeconds != null) {
            jedisPooled.expire(key, expireSeconds)
        }
    }

    fun setAdd(key: String, value: String) {
        jedisPooled.sadd(key, value)
    }

    fun setContains(key: String, value: String): Boolean {
        return jedisPooled.sismember(key, value)
    }

    fun setGetAll(key: String): Set<String> {
        return jedisPooled.smembers(key)
    }

    fun jsonSet(key: String, json: Any) {
        jedisPooled.jsonSet(key, Jackson.asFormatString(json))
    }

    fun jsonGet(key: String): Any? {
        return jedisPooled.jsonGet(key)
    }

    inline fun <reified T: Any> jsonGetAs(key: String): T? {
        val json = jsonGet(key) ?: return null
        return Jackson.asA<T>(Jackson.asFormatString(json))
    }
}