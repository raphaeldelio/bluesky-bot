package dev.raphaeldelio.service

class DynamicConfigService(private val redisService: RedisService) {

    fun getVersion(): String {
        return redisService.get("version") ?: "0.1.0"
    }

    fun setVersion(version: String) {
        redisService.set("version", version)
    }
}