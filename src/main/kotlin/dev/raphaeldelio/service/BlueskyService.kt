package dev.raphaeldelio.service

import dev.raphaeldelio.Logger
import dev.raphaeldelio.model.BlueskyConfig
import dev.raphaeldelio.model.FollowData
import dev.raphaeldelio.model.FollowRecord
import dev.raphaeldelio.model.LoginResponse
import dev.raphaeldelio.model.Post
import dev.raphaeldelio.model.RepostData
import dev.raphaeldelio.model.RepostRecord
import dev.raphaeldelio.model.SearchResponse
import dev.raphaeldelio.model.Subject
import org.http4k.client.ApacheClient
import org.http4k.client.ApacheClient.invoke
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Request.Companion.invoke
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Jackson
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class BlueskyService(
    blueskyConfig: BlueskyConfig,
    private val redisService: RedisService,
) {
    val client = ApacheClient()
    
    val apiUrl = blueskyConfig.apiurl
    val username = blueskyConfig.username
    val password = blueskyConfig.password

    fun getAccessToken(): String {
        val request = Request(Method.POST, "$apiUrl/com.atproto.server.createSession")
            .header("Content-Type", "application/json")
            .body("{\"identifier\": \"$username\", \"password\": \"$password\"}")

        val response: Response = client(request)
        return if (response.status == Status.OK) {
            val result = Jackson.asA(response.bodyString(), LoginResponse::class)

            redisService.set("did", result.did)

            Logger.info("✅ Login successful. DID: ${result.did}")
            result.accessJwt // Return the access token
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
                .let {
                    if (cursor != null) it.query("cursor", cursor) else it
                }

            val response: Response = client(request)
            if (response.status == Status.OK) {
                val result = response.bodyString().let { Jackson.asA<SearchResponse>(it) }
                val posts = result.posts
                Logger.info("✅ Retrieved ${posts.size} posts. Total so far: ${allPosts.size + posts.size}.")
                allPosts.addAll(posts)
                cursor = result.cursor
            } else {
                Logger.info("⚠️ Failed to fetch posts for tag: $tag. Status: ${response.status}")
                break
            }
        } while (cursor != null)

        Logger.info("🎉 Finished fetching posts for tag: $tag. Total retrieved: ${allPosts.size}.")
        return allPosts
    }

    fun repost(token: String, post: Post) {
        if (post.uri.isBlank() || post.cid.isBlank()) {
            Logger.info("⚠️ Invalid post: Missing 'uri' or 'cid'. Skipping repost.")
            return
        }

        val repostedPostsKey = "repostedPosts"
        if (redisService.setContains(repostedPostsKey, post.uri)) {
            Logger.info("🔁 Post already reposted: ${post.uri}. Skipping.")
            return
        }

        // Construct the repost data
        val repostData = RepostData(
            repo = redisService.get("did") ?: throw IllegalArgumentException("DID not found in Redis"),
            collection = "app.bsky.feed.repost",
            rkey = "",
            validate = false,
            record = RepostRecord(
                subject = Subject(
                    uri = post.uri,
                    cid = post.cid
                )
            )
        )

        val request = Request(Method.POST, "$apiUrl/com.atproto.repo.createRecord")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .body(Jackson.asFormatString(repostData))

        val response: Response = client(request)
        if (response.status == Status.OK) {
            Logger.info("✅ Successfully reposted: ${post.uri}")
            redisService.setAdd(repostedPostsKey, post.uri)
        } else {
            Logger.info("⚠️ Failed to repost: ${post.uri}. Error: ${response.bodyString()}")
        }
    }

    fun followUser(token: String, authorDid: String) {
        val followedAuthorsKey = "followedAuthors"
        if (redisService.setContains(followedAuthorsKey, authorDid)) {
            Logger.info("🔁 Already following: $authorDid. Skipping.")
            return
        }

        // Create the follow data
        val followData = FollowData(
            repo = redisService.get("did") ?: throw IllegalArgumentException("DID not found in Redis"),
            record = FollowRecord(subject = authorDid)
        )

        // Build the HTTP request
        val request = Request(Method.POST, "$apiUrl/com.atproto.repo.createRecord")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .body(Jackson.asFormatString(followData))

        // Send the request and handle the response
        val response: Response = client(request)

        // Handle the response
        if (response.status == Status.OK) {
            Logger.info("✅ Successfully followed: $authorDid")
            redisService.setAdd(followedAuthorsKey, authorDid)
        } else {
            Logger.info("⚠️ Failed to follow: $authorDid. Error: ${response.bodyString()}")
        }
    }
}