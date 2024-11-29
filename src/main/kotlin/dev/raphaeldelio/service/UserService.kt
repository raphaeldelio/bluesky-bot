package dev.raphaeldelio.service

import dev.raphaeldelio.Logger
import dev.raphaeldelio.model.*
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Jackson

class UserService(
    blueskyConfig: BlueskyConfig,
    private val redisService: RedisService
) {
    private val client = ApacheClient()
    private val apiUrl = blueskyConfig.apiurl

    fun followUser(token: String, authorDid: String) {
        handleUserAction(
            token = token,
            actionKey = "followedAuthors",
            uniqueId = authorDid,
            data = FollowData(
                repo = redisService.get("did") ?: throw IllegalArgumentException("DID not found in Redis"),
                record = FollowRecord(subject = authorDid)
            ),
            actionName = "follow",
            collection = "com.atproto.repo.createRecord"
        )
    }

    fun getProfile(token: String, did: String): Profile {
        val cacheKey = "profile:$did"
        // Check if the profile is already in Redis
        val cachedProfile = redisService.getJsonAs<Profile>(cacheKey)
        if (cachedProfile != null) {
            Logger.info("ℹ️ Profile for DID: $did retrieved from Redis cache")
            return cachedProfile
        }

        // Fetch the profile from the API if not in cache
        val request = Request(Method.GET, "$apiUrl/app.bsky.actor.getProfile/")
            .header("Authorization", "Bearer $token")
            .query("actor", did)

        val response = client(request)
        if (response.status == Status.OK) {
            Logger.info("✅ Successfully retrieved profile for DID: $did")

            val profile = Jackson.asA(response.bodyString(), Profile::class)
            redisService.setJson(cacheKey, profile)
            Logger.info("🗄️ Profile for DID: $did stored in Redis")

            return profile
        } else {
            val errorMessage = "⚠️ Failed to retrieve profile for DID: $did. Error: ${response.bodyString()}"
            Logger.error(errorMessage)
            throw IllegalStateException(errorMessage)
        }
    }

    private fun handleUserAction(
        token: String,
        actionKey: String,
        uniqueId: String,
        data: Any,
        actionName: String,
        collection: String
    ) {
        if (redisService.setContains(actionKey, uniqueId)) {
            Logger.info("🔁 Already ${actionName}ed: $uniqueId. Skipping.")
            return
        }

        val request = Request(Method.POST, "$apiUrl/$collection")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .body(Jackson.asFormatString(data))

        val response = client(request)
        if (response.status == Status.OK) {
            Logger.info("✅ Successfully ${actionName}ed: $uniqueId")
            redisService.setAdd(actionKey, uniqueId)
        } else {
            Logger.info("⚠️ Failed to ${actionName}: $uniqueId. Error: ${response.bodyString()}")
        }
    }
}