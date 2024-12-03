package dev.raphaeldelio.service

import org.http4k.format.Jackson
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.search.IndexOptions
import redis.clients.jedis.search.Schema

class RedisService(private val jedisPooled: JedisPooled) {
    // String
    fun stringGet(key: String): String? {
        return jedisPooled.get(key)
    }

    fun stringSet(key: String, value: String, expireSeconds: Long? = null) {
        jedisPooled.set(key, value)
        if (expireSeconds != null) {
            jedisPooled.expire(key, expireSeconds)
        }
    }

    // Set
    fun setAdd(key: String, value: String) {
        jedisPooled.sadd(key, value)
    }

    fun setContains(key: String, value: String): Boolean {
        return jedisPooled.sismember(key, value)
    }

    fun setGetAll(key: String): Set<String> {
        return jedisPooled.smembers(key)
    }

    // Json
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

    // Redis Search (Full Text)
    fun ftList(): Set<String> {
        return jedisPooled.ftList()
    }

    fun ftCreateIndex(indexName: String, options: IndexOptions, schema: Schema) {
        jedisPooled.ftCreate(indexName, options, schema)
    }


}