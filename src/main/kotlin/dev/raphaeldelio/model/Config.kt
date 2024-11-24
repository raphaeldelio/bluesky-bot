package dev.raphaeldelio.model

data class Config(
    val redis: RedisConfig,
    val bluesky: BlueskyConfig,
    val poster: PosterConfig
)

data class RedisConfig(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?
)

data class BlueskyConfig(
    val apiurl: String,
    val username: String,
    val password: String
)

data class PosterConfig(
    val since: String,
    val scheduler: SchedulerConfig,
    val actions: ActionsConfig,
    val tags: List<String>
)

data class SchedulerConfig(
    val frequencyminutes: Int
)

data class ActionsConfig(
    val repost: ActionDetailConfig,
    val like: ActionDetailConfig,
    val follow: ActionDetailConfig
)

data class ActionDetailConfig(
    val enabled: Boolean
)