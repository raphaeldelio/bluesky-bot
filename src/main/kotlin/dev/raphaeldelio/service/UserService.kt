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