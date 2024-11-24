package dev.raphaeldelio.service

import dev.raphaeldelio.Logger
import dev.raphaeldelio.model.*
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Jackson
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class BlueskyService(
    blueskyConfig: BlueskyConfig,
    private val redisService: RedisService
) {
    private val client = ApacheClient()
    private val apiUrl = blueskyConfig.apiurl
    private val username = blueskyConfig.username
    private val password = blueskyConfig.password

    private fun sendRequest(request: Request): Response {
        return client(request)
    }

    fun getAccessToken(): String {
        val request = Request(Method.POST, "$apiUrl/com.atproto.server.createSession")
            .header("Content-Type", "application/json")
            .body(Jackson.asFormatString(mapOf("identifier" to username, "password" to password)))

        val response = sendRequest(request)
        return if (response.status == Status.OK) {
            val result = Jackson.asA(response.bodyString(), LoginResponse::class)
            redisService.set("did", result.did)
            Logger.info("✅ Login successful. DID: ${result.did}")
            result.accessJwt
        } else {
            Logger.info("⚠️ Authentication failed: ${response.status}")
            ""
        }
    }

    fun searchPosts(token: String, since: OffsetDateTime, tag: String): List<Post> {
        val allPosts = mutableListOf<Post>()
        var cursor: String? = null

        Logger.info("🔍 Searching posts with tag: $tag since: $since")
        do {
            val request = Request(Method.GET, "$apiUrl/app.bsky.feed.searchPosts")
                .header("Authorization", "Bearer $token")
                .query("q", tag)
                .query("sort", "latest")
                .query("limit", "100")
                .query("since", since.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .let { if (cursor != null) it.query("cursor", cursor) else it }

            val response = sendRequest(request)
            if (response.status == Status.OK) {
                val result = Jackson.asA<SearchResponse>(response.bodyString())
                allPosts.addAll(result.posts)
                cursor = result.cursor
                Logger.info("✅ Retrieved ${result.posts.size} posts. Total: ${allPosts.size}")
            } else {
                Logger.info("⚠️ Failed to fetch posts for tag: $tag. Status: ${response.status}")
                break
            }
        } while (cursor != null)

        Logger.info("🎉 Finished fetching posts for tag: $tag. Total retrieved: ${allPosts.size}")
        return allPosts
    }

    fun handlePostAction(token: String, post: Post, action: Action) {
        if (post.uri.isBlank() || post.cid.isBlank()) {
            Logger.info("⚠️ Invalid post: Missing 'uri' or 'cid'. Skipping $action.")
            return
        }

        val redisKey = action.redisKey
        if (redisService.setContains(redisKey, post.uri)) {
            Logger.info("🔁 Post already ${action.pastTense}: ${post.uri}. Skipping.")
            return
        }

        val data = when (action) {
            Action.REPOST -> createActionData("app.bsky.feed.repost", post)
            Action.LIKE -> createActionData("app.bsky.feed.like", post)
        }

        val request = Request(Method.POST, "$apiUrl/com.atproto.repo.createRecord")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .body(Jackson.asFormatString(data))

        val response = sendRequest(request)
        if (response.status == Status.OK) {
            Logger.info("✅ Successfully ${action.pastTense}: ${post.uri}")
            redisService.setAdd(redisKey, post.uri)
        } else {
            Logger.info("⚠️ Failed to ${action.presentTense}: ${post.uri}. Error: ${response.bodyString()}")
        }
    }

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

    private fun createActionData(collection: String, post: Post): RepostData {
        return RepostData(
            repo = redisService.get("did") ?: throw IllegalArgumentException("DID not found in Redis"),
            collection = collection,
            rkey = "",
            validate = false,
            record = RepostRecord(
                subject = Subject(
                    uri = post.uri,
                    cid = post.cid
                )
            )
        )
    }

    enum class Action(val presentTense: String, val pastTense: String, val redisKey: String) {
        REPOST("repost", "reposted", "repostedPosts"),
        LIKE("like", "liked", "likedPosts")
    }
}