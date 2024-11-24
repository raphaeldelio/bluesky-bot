package dev.raphaeldelio

import dev.raphaeldelio.model.PosterConfig
import dev.raphaeldelio.model.RedisConfig
import dev.raphaeldelio.service.BlueskyService
import dev.raphaeldelio.service.ConfigService
import dev.raphaeldelio.service.RedisService
import redis.clients.jedis.JedisPool
import java.time.OffsetDateTime
import kotlin.concurrent.timer

fun main() {
    val config = ConfigService().loadConfig()
    val jedisPool = createJedisPool(config.redis)
    val redisService = RedisService(jedisPool)
    val blueskyService = BlueskyService(config.bluesky, redisService)

    Runtime.getRuntime().addShutdownHook(Thread {
        Logger.info("⏼ Shutting down application. Closing Redis connections.")
        jedisPool.close()
    })

    timer(
        name = "BlueskyBotScheduler",
        daemon = false,
        initialDelay = 0L,
        period = config.poster.scheduler.frequencyminutes * 60 * 1000L
    ) {
        runCatching {
            process(config.poster, redisService, blueskyService)
        }.onFailure {
            Logger.error("Failed to process task: ${it.message}")
            Logger.info("Closing Redis connections.")
            jedisPool.close()
            throw it
        }
    }
}

fun createJedisPool(redisConfig: RedisConfig): JedisPool {
    Logger.info("Creating JedisPool for Redis at ${redisConfig.host}:${redisConfig.port}")
    return JedisPool(
        redisConfig.host,
        redisConfig.port,
        redisConfig.username,
        redisConfig.password
    )
}

fun process(posterConfig: PosterConfig, redisService: RedisService, blueskyService: BlueskyService) {
    Logger.info("🚀 Starting task.")
    val now = OffsetDateTime.now()
    val lastRun = OffsetDateTime.parse(
        redisService.get("lastRun") ?: posterConfig.since.toString()
    )
    Logger.info("Fetching posts since $lastRun")

    val token = blueskyService.getAccessToken()
    if (token.isEmpty()) {
        Logger.info("🔴 Failed to retrieve access token. Skipping task.")
        return
    }

    val posts = posterConfig.tags
        .flatMap { tag -> blueskyService.searchPosts(token, lastRun, "#$tag") }

    posts.forEach { post ->
        if (posterConfig.actions.like.enabled) blueskyService.handlePostAction(token, post, BlueskyService.Action.LIKE)
        if (posterConfig.actions.repost.enabled) blueskyService.handlePostAction(token, post, BlueskyService.Action.REPOST)
        if (posterConfig.actions.follow.enabled) blueskyService.followUser(token, post.author.did)
    }

    redisService.set("lastRun", now.toString())
    Logger.info("🎉 Task completed. Next run in ${posterConfig.scheduler.frequencyminutes} minutes.")
}