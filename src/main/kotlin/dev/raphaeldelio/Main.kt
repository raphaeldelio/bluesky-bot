package dev.raphaeldelio

import dev.raphaeldelio.model.PosterConfig
import dev.raphaeldelio.model.RedisConfig
import dev.raphaeldelio.service.AuthenticationService
import dev.raphaeldelio.service.ConfigService
import dev.raphaeldelio.service.PostService
import dev.raphaeldelio.service.RedisService
import dev.raphaeldelio.service.UserService
import redis.clients.jedis.JedisPooled
import java.time.OffsetDateTime
import kotlin.concurrent.timer

fun main() {
    val config = ConfigService().loadConfig()
    val jedisPooled = createJedisPooled(config.redis)
    val redisService = RedisService(jedisPooled)
    val authenticationService = AuthenticationService(config.bluesky, redisService)
    val postService = PostService(config.bluesky, redisService)
    val userService = UserService(config.bluesky, redisService)

    Runtime.getRuntime().addShutdownHook(Thread {
        Logger.info("⏼ Shutting down application. Closing Redis connections.")
        jedisPooled.close()
    })

    timer(
        name = "BlueskyBotScheduler",
        daemon = false,
        initialDelay = 0L,
        period = config.poster.scheduler.frequencyminutes * 60 * 1000L
    ) {
        runCatching {
            process(config.poster, redisService, authenticationService, postService, userService)
        }.onFailure {
            Logger.error("Failed to process task: ${it.message}")
            Logger.info("Closing Redis connections.")
            jedisPooled.close()
            throw it
        }
    }
}

fun createJedisPooled(redisConfig: RedisConfig): JedisPooled {
    Logger.info("Creating JedisPool for Redis at ${redisConfig.host}:${redisConfig.port}")
    return JedisPooled(
        redisConfig.host,
        redisConfig.port,
        redisConfig.username,
        redisConfig.password
    )
}

fun process(
    posterConfig: PosterConfig,
    redisService: RedisService,
    authenticationService: AuthenticationService,
    postService: PostService,
    userService: UserService) {
    Logger.info("🚀 Starting task.")
    val now = OffsetDateTime.now()
    val lastRun = OffsetDateTime.parse(
        redisService.get("lastRun") ?: posterConfig.since.toString()
    )
    Logger.info("Fetching posts since $lastRun")

    val token = authenticationService.getAccessToken()
    if (token.isEmpty()) {
        Logger.info("🔴 Failed to retrieve access token. Skipping task.")
        return
    }

    val posts = posterConfig.tags
        .flatMap { tag -> postService.searchPosts(token, lastRun, "#$tag") }

    posts.forEach { post ->
        if (posterConfig.actions.like.enabled) postService.handlePostAction(token, post, PostService.Action.LIKE)
        if (posterConfig.actions.repost.enabled) postService.handlePostAction(token, post, PostService.Action.REPOST)
        if (posterConfig.actions.follow.enabled) userService.followUser(token, post.author.did)

        userService.getProfile(token, post.author.did)
    }

    redisService.set("lastRun", now.toString())
    Logger.info("🎉 Task completed. Next run in ${posterConfig.scheduler.frequencyminutes} minutes.")
}