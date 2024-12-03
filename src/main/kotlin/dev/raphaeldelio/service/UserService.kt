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

    fun getAllFollowedUsers(): Set<String> {
        return redisService.setGetAll("followedAuthors").also {
            Logger.info("🔍 Retrieved ${it.size} followed authors from Redis")
        }
    }

    fun followUser(token: String, authorDid: String) {
        val repo = redisService.stringGet("did") ?: error("DID not found in Redis")
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

    fun getProfiles(token: String, authorDids: Set<String>): List<Profile> {
        Logger.info("🔍 Fetching profiles for ${authorDids.size} authors")
        authorDids.filter { !isProfileAlreadyStored(it) }.chunked(25).forEach { chunk ->
            fetchProfilesFromApi(token, chunk.toSet())?.profiles?.forEach {
                storeProfile(it)
            }
            Logger.info("🕒 Waiting 1 sec before next call")
            Thread.sleep(1000)
        }
        Logger.info("🔍 Retrieved profiles for ${authorDids.size} authors")
        return authorDids.mapNotNull { redisService.jsonGetAs<Profile>("profile:$it") }
    }

    fun getProfile(token: String, did: String): Profile? {
        Logger.info("🔍 Fetching profile for DID: $did")
        return if (isProfileAlreadyStored(did)) {
            redisService.jsonGetAs<Profile>("profile:$did")
        } else {
            fetchProfileFromApi(token, did).also {
                storeProfile(it)
                Logger.info("🔍 Retrieved profile for DID: $did")
            }
        }
    }

    fun isProfileAlreadyStored(did: String): Boolean {
        return redisService.jsonGet("profile:$did") != null
    }

    fun storeProfile(profile: Profile) {
        val key = "profile:${profile.did}"
        redisService.jsonSet(key, profile)
        Logger.info("🗄️ Profile for DID: ${profile.did} stored in Redis")
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

    private fun fetchProfilesFromApi(token: String, dids: Set<String>): Profiles? {
        if (dids.isEmpty()) {
            return null
        }

        if (dids.size > 25) {
            throw IllegalArgumentException("Cannot fetch more than 25 profiles at once")
        }

        var request = createGetRequest("$apiUrl/app.bsky.actor.getProfiles/", token)
        dids.forEach { request = request.query("actors", it) }

        val response = client(request)
        if (response.status != Status.OK) {
            handleError("Failed to retrieve profile for DID: $dids", response)
        }

        return Jackson.asA(response.bodyString(), Profiles::class).also {
            Logger.info("✅ Successfully retrieved profiles for DID: $dids")
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