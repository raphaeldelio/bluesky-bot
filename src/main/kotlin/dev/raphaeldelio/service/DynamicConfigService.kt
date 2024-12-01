package dev.raphaeldelio.service

class DynamicConfigService(private val redisService: RedisService) {

    companion object {
        private const val PREFIX = "config:"
    }

    fun getVersion(): String {
        return redisService.get("${PREFIX}version") ?: "0.1.0"
    }

    fun setVersion(version: String) {
        redisService.set("${PREFIX}version", version)
    }
}