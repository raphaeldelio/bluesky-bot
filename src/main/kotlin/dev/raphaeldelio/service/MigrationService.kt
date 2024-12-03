package dev.raphaeldelio.service

import dev.raphaeldelio.Logger
import redis.clients.jedis.search.IndexDefinition
import redis.clients.jedis.search.IndexOptions
import redis.clients.jedis.search.Schema

class MigrationService(
    private val redisService: RedisService,
    private val userService: UserService,
    private val dynamicConfigService: DynamicConfigService
) {

    companion object {
        private const val LATEST_VERSION = "0.2.0"
    }

    fun migrate(token: String) {
        if (dynamicConfigService.getVersion() == LATEST_VERSION) {
            Logger.info("🎉 Migration not needed. Already on latest version: $LATEST_VERSION")
            return
        }

        val version = dynamicConfigService.getVersion()
        Logger.info("🚀 Starting migration from version: $version")

        when (version) {
            "0.1.0" -> {
                migrateUsers(token)
                createProfileSchema()
            }
            else -> Logger.info("🎉 No migration needed")
        }

        dynamicConfigService.setVersion(LATEST_VERSION)
        Logger.info("🎉 Migration completed. Version: 0.2.0")
    }

    /**
     * Version: 0.1.0 -> 0.2.0
     * Fetch profiles for all followed authors and store them in Redis
     */
    fun migrateUsers(token: String) {
        val followedAuthors = userService.getAllFollowedUsers()
        Logger.info("🚚 Migrating ${followedAuthors.size} followed authors")
        userService.getProfiles(token, followedAuthors)
    }

    /**
     * Creates a RedisSearch schema for the Profile data structure
     */
    private fun createProfileSchema() {
        val indexName = "profileIndex"
        Logger.info("📂 Checking if index '$indexName' exists")
        val indices = redisService.ftList()
        if (indices.contains(indexName)) {
            Logger.info("✅ Index '$indexName' already exists. Skipping creation.")
            return
        }

        Logger.info("📦 Creating RedisSearch schema for profiles")
        val schema = Schema()
            .addTextField("$.handle", 1.0).`as`("handle")
            .addTextField("$.displayName", 1.0).`as`("displayName")
            .addNumericField("$.followersCount").`as`("followersCount")
            .addNumericField("$.followsCount").`as`("followsCount")
            .addNumericField("$.postsCount").`as`("postsCount")

        redisService.ftCreateIndex(
            indexName,
            IndexOptions.defaultOptions()
                .setDefinition(IndexDefinition(IndexDefinition.Type.JSON).setPrefixes("profile:")),
            schema
        )
        Logger.info("✅ RedisSearch schema for '$indexName' created successfully")
    }
}