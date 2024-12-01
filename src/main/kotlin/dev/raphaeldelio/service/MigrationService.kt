package dev.raphaeldelio.service

import dev.raphaeldelio.Logger

class MigrationService(
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
            "0.1.0" -> migrateUsers(token)
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
}