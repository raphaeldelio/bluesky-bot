package dev.raphaeldelio.service

import dev.raphaeldelio.Logger
import dev.raphaeldelio.model.*
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Jackson

class UserService(
    private val blueskyConfig: BlueskyConfig,
    private val redisService: RedisService
) {
    private val client = ApacheClient()
    private val apiUrl = blueskyConfig.apiurl

    fun followUser(token: String, authorDid: String) {
        val repo = redisService.get("did") ?: error("DID not found in Redis")
        val followData = FollowData(repo = repo, record = FollowRecord(subject = authorDid))

        performAction(
            token = token,
            actionKey = "followedAuthors",
            uniqueId = authorDid,
            data = followData,
            actionName = "follow",
            endpoint = "com.atproto.repo.createRecord"
        )
    }

    fun getProfile(token: String, did: String): Profile {
        val cacheKey = "profile:$did"

        // Try fetching from cache
        return redisService.getJsonAs<Profile>(cacheKey)?.also {
            Logger.info("ℹ️ Profile for DID: $did retrieved from Redis cache")
        } ?: run {
            // Fetch from API if not cached
            fetchProfileFromApi(token, did).also { profile ->
                redisService.setJson(cacheKey, profile)
                Logger.info("🗄️ Profile for DID: $did stored in Redis")
            }
        }
    }

    private fun fetchProfileFromApi(token: String, did: String): Profile {
        val request = createGetRequest("$apiUrl/app.bsky.actor.getProfile/", token)
            .query("actor", did)

        val response = client(request)
        if (response.status != Status.OK) {
            handleError("Failed to retrieve profile for DID: $did", response)
        }

        return Jackson.asA(response.bodyString(), Profile::class).also {
            Logger.info("✅ Successfully retrieved profile for DID: $did")
        }
    }

    private fun performAction(
        token: String,
        actionKey: String,
        uniqueId: String,
        data: Any,
        actionName: String,
        endpoint: String
    ) {
        if (redisService.setContains(actionKey, uniqueId)) {
            Logger.info("🔁 Already ${actionName}ed: $uniqueId. Skipping.")
            return
        }

        val request = createPostRequest("$apiUrl/$endpoint", token, data)
        val response = client(request)

        if (response.status == Status.OK) {
            Logger.info("✅ Successfully ${actionName}ed: $uniqueId")
            redisService.setAdd(actionKey, uniqueId)
        } else {
            handleError("Failed to ${actionName}: $uniqueId", response)
        }
    }

    private fun createGetRequest(url: String, token: String): Request {
        return Request(Method.GET, url)
            .header("Authorization", "Bearer $token")
    }

    private fun createPostRequest(url: String, token: String, body: Any): Request {
        return Request(Method.POST, url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .body(Jackson.asFormatString(body))
    }

    private fun handleError(message: String, response: org.http4k.core.Response) {
        val errorMessage = "⚠️ $message. Error: ${response.bodyString()}"
        Logger.error(errorMessage)
        throw IllegalStateException(errorMessage)
    }
}