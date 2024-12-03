package dev.raphaeldelio.service

import com.github.tomakehurst.wiremock.client.WireMock.*
import dev.raphaeldelio.model.Author
import dev.raphaeldelio.model.BlueskyConfig
import dev.raphaeldelio.model.Post
import dev.raphaeldelio.model.Record
import org.assertj.core.api.Assertions.assertThat
import org.http4k.format.Jackson
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class PostServiceTest : BaseTest() {

    private fun createPostService(): PostService {
        val redisService = createRedisService()
        val blueskyConfig = BlueskyConfig(
            apiurl = getWireMockBaseUrl(),
            username = "test-user",
            password = "test-password"
        )
        return PostService(blueskyConfig, redisService)
    }

    private fun stubSearchResponse(cursorRequest: String?, cursorResponse: String?, posts: List<Map<String, Any>>) {
        val stub = get(urlPathEqualTo("/app.bsky.feed.searchPosts"))
            .withQueryParam("q", equalTo("#java"))
            .withQueryParam("sort", equalTo("latest"))
            .withQueryParam("limit", equalTo("100"))
            .withQueryParam("since", matching(".*"))

        if (cursorRequest != null) stub.withQueryParam("cursor", equalTo(cursorRequest))

        val responseBody = Jackson.asFormatString(mapOf(
            "posts" to posts,
            "cursor" to cursorResponse
        ))

        wireMockServer.stubFor(stub.willReturn(aResponse().withStatus(200).withBody(responseBody.toString())))
    }

    @Test
    fun `should search posts by tag and return a list of posts`() {
        // Arrange
        val postsPage1 = listOf(
            mapOf(
                "uri" to "post1",
                "cid" to "cid1",
                "author" to mapOf("did" to "did1", "handle" to "handle1"),
                "indexedAt" to "2024-11-22T19:13:26-05:00",
                "record" to mapOf("type" to "app.bsky.feed.post", "createdAt" to "2024-11-22T19:13:26-05:00", "text" to "Post 1 text"),
                "replyCount" to 0,
                "repostCount" to 0,
                "likeCount" to 0,
                "quoteCount" to 0
            ),
            mapOf(
                "uri" to "post2",
                "cid" to "cid2",
                "author" to mapOf("did" to "did2", "handle" to "handle2"),
                "indexedAt" to "2024-11-22T19:13:20-05:00",
                "record" to mapOf("type" to "app.bsky.feed.post", "createdAt" to "2024-11-22T19:13:20-05:00", "text" to "Post 2 text"),
                "replyCount" to 0,
                "repostCount" to 0,
                "likeCount" to 0,
                "quoteCount" to 0
            )
        )
        val postsPage2 = listOf(
            mapOf(
                "uri" to "post3",
                "cid" to "cid3",
                "author" to mapOf("did" to "did3", "handle" to "handle3"),
                "indexedAt" to "2024-11-22T19:13:14-05:00",
                "record" to mapOf("type" to "app.bsky.feed.post", "createdAt" to "2024-11-22T19:13:14-05:00", "text" to "Post 3 text"),
                "replyCount" to 0,
                "repostCount" to 0,
                "likeCount" to 0,
                "quoteCount" to 0
            )
        )

        stubSearchResponse(null, "cursor123", postsPage1)
        stubSearchResponse("cursor123", null, postsPage2)

        val blueskyService = createPostService()
        val token = "test-access-token"
        val since = OffsetDateTime.now().minusDays(1)

        // Act
        val posts = blueskyService.searchPosts(token, since, "#java")

        // Assert
        assertThat(posts).hasSize(3)
        assertThat(posts.map { it.uri }).containsExactly("post1", "post2", "post3")
        assertThat(posts.map { it.cid }).containsExactly("cid1", "cid2", "cid3")
    }

    @Test
    fun `should like a post and add it to Redis`() {
        // Arrange
        val post = Post(
            uri = "post1",
            cid = "cid1",
            author = Author(did = "did1", handle = "handle1", avatar = "", displayName = "Author 1"),
            indexedAt = "2024-11-22T19:13:26-05:00",
            record = Record(createdAt = "2024-11-22T19:13:26-05:00", text = "Post text", embed = null),
            replyCount = 0,
            repostCount = 0,
            likeCount = 0,
            quoteCount = 0
        )
        wireMockServer.stubFor(
            post(urlPathEqualTo("/com.atproto.repo.createRecord"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(Jackson.asFormatString(mapOf("success" to true)))
                )
        )

        val redisService = createRedisService()
        val blueskyService = createPostService()
        redisService.stringSet("did", "did:example:123")

        // Act
        blueskyService.handlePostAction("test-access-token", post, PostService.Action.LIKE)

        // Assert
        assertThat(redisService.setContains("likedPosts", post.uri)).isTrue

        // Verify the API call was made
        wireMockServer.verify(
            postRequestedFor(urlPathEqualTo("/com.atproto.repo.createRecord"))
                .withHeader("Authorization", equalTo("Bearer test-access-token"))
                .withRequestBody(matchingJsonPath("$.record.subject.uri", equalTo(post.uri)))
                .withRequestBody(matchingJsonPath("$.record.subject.cid", equalTo(post.cid)))
        )
    }

    @Test
    fun `should repost a post and add it to Redis`() {
        // Arrange
        val post = Post(
            uri = "post1",
            cid = "cid1",
            author = Author(did = "did1", handle = "handle1", avatar = "", displayName = "Author 1"),
            indexedAt = "2024-11-22T19:13:26-05:00",
            record = Record(createdAt = "2024-11-22T19:13:26-05:00", text = "Post text", embed = null),
            replyCount = 0,
            repostCount = 0,
            likeCount = 0,
            quoteCount = 0
        )
        wireMockServer.stubFor(
            post(urlPathEqualTo("/com.atproto.repo.createRecord"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("""
                        {
                            "success": true
                        }
                    """.trimIndent())
                )
        )

        val redisService = createRedisService()
        val blueskyService = createPostService()
        redisService.stringSet("did", "did:example:123")

        // Act
        blueskyService.handlePostAction("test-access-token", post, PostService.Action.REPOST)

        // Assert
        assertThat(redisService.setContains("repostedPosts", post.uri)).isTrue

        // Verify the API call was made
        wireMockServer.verify(
            postRequestedFor(urlPathEqualTo("/com.atproto.repo.createRecord"))
                .withHeader("Authorization", equalTo("Bearer test-access-token"))
                .withRequestBody(matchingJsonPath("$.record.subject.uri", equalTo(post.uri)))
                .withRequestBody(matchingJsonPath("$.record.subject.cid", equalTo(post.cid)))
        )
    }

    @Test
    fun `should skip liking a post if already liked`() {
        // Arrange
        val post = Post(
            uri = "post1",
            cid = "cid1",
            author = Author(did = "did1", handle = "handle1", avatar = "", displayName = "Author 1"),
            indexedAt = "2024-11-22T19:13:26-05:00",
            record = Record(createdAt = "2024-11-22T19:13:26-05:00", text = "Post text", embed = null),
            replyCount = 0,
            repostCount = 0,
            likeCount = 0,
            quoteCount = 0
        )
        val redisService = createRedisService()
        redisService.stringSet("did", "did:example:123")

        redisService.setAdd("likedPosts", post.uri) // Simulate post already liked

        val blueskyService = createPostService()

        // Act
        blueskyService.handlePostAction("test-access-token", post, PostService.Action.LIKE)

        // Assert
        wireMockServer.verify(
            0, // No calls should be made
            postRequestedFor(urlPathEqualTo("/com.atproto.repo.createRecord"))
        )
    }
}