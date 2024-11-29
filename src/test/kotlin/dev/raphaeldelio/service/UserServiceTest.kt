package dev.raphaeldelio.service

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import dev.raphaeldelio.model.BlueskyConfig
import dev.raphaeldelio.model.Profile
import org.assertj.core.api.Assertions.assertThat
import org.http4k.format.Jackson
import org.junit.jupiter.api.Test

class UserServiceTest : BaseTest() {

    private fun createUserService(): UserService {
        val redisService = createRedisService()
        val blueskyConfig = BlueskyConfig(
            apiurl = getWireMockBaseUrl(),
            username = "test-user",
            password = "test-password"
        )
        return UserService(blueskyConfig, redisService)
    }

    @Test
    fun `should follow a user and add to Redis`() {
        // Arrange
        val authorDid = "did:example:123"
        val token = "test-access-token"
        stubApiPost("/com.atproto.repo.createRecord", mapOf("success" to true))

        val userService = createUserService()
        val redisService = createRedisService()
        redisService.set("did", authorDid)

        // Act
        userService.followUser(token, authorDid)

        // Assert
        assertThat(redisService.setContains("followedAuthors", authorDid)).isTrue
        verifyApiPost("/com.atproto.repo.createRecord", token, mapOf("record.subject" to authorDid))
    }

    @Test
    fun `should fetch profile from API and store in Redis`() {
        // Arrange
        val did = "did:example:123"
        val token = "test-access-token"
        val expectedProfile = createTestProfile(did)
        stubApiGet("/app.bsky.actor.getProfile/", token, expectedProfile, mapOf("actor" to equalTo(did)))

        val userService = createUserService()
        val redisService = createRedisService()

        // Act
        val actualProfile = userService.getProfile(token, did)

        // Assert
        verifyProfile(actualProfile, expectedProfile)
        verifyProfileInRedis(redisService, did, expectedProfile)
        verifyApiGet("/app.bsky.actor.getProfile/", token, mapOf("actor" to equalTo(did)))
    }

    @Test
    fun `should fetch profile from Redis cache when available`() {
        // Arrange
        val did = "did:example:123"
        val expectedProfile = createTestProfile(did, handle = "cached-handle", displayName = "Cached User")
        val redisService = createRedisService()
        cacheProfileInRedis(redisService, did, expectedProfile)

        val userService = createUserService()

        // Act
        val actualProfile = userService.getProfile("unused-token", did)

        // Assert
        verifyProfile(actualProfile, expectedProfile)
        verifyNoApiCall("/profile/$did")
    }

    // Helper Functions
    private fun createTestProfile(
        did: String,
        handle: String = "test-handle",
        displayName: String = "Test User",
        description: String = "Test Description"
    ): Profile {
        return Profile(
            did = did,
            handle = handle,
            displayName = displayName,
            description = description,
            avatar = "https://example.com/avatar.png",
            banner = "https://example.com/banner.png",
            followersCount = 100,
            followsCount = 50,
            postsCount = 10,
            associated = Profile.Associated(
                lists = 0, feedgens = 1, starterPacks = 2, labeler = true,
                chat = Profile.Associated.Chat(allowIncoming = "all")
            ),
            joinedViaStarterPack = null,
            indexedAt = "2024-11-07T00:27:25.289Z",
            createdAt = "2024-11-01T00:00:00.000Z",
            viewer = Profile.Viewer(
                muted = false, mutedByList = null, blockedBy = false,
                blocking = null, blockingByList = null, following = null,
                followedBy = null, knownFollowers = null
            ),
            labels = emptyList(),
            pinnedPost = null
        )
    }

    private fun stubApiPost(endpoint: String, responseBody: Any) {
        wireMockServer.stubFor(
            post(urlPathEqualTo(endpoint))
                .willReturn(aResponse().withStatus(200).withBody(Jackson.asFormatString(responseBody)))
        )
    }

    private fun stubApiGet(endpoint: String, token: String, responseBody: Any, params: Map<String, StringValuePattern> = emptyMap()) {
        wireMockServer.stubFor(
            get(urlPathEqualTo(endpoint))
                .withHeader("Authorization", equalTo("Bearer $token"))
                .withQueryParams(params)
                .willReturn(aResponse().withStatus(200).withBody(Jackson.asFormatString(responseBody)))
        )
    }

    private fun cacheProfileInRedis(redisService: RedisService, did: String, profile: Profile) {
        redisService.setJson("profile:$did", profile)
    }

    private fun verifyProfile(actual: Profile, expected: Profile) {
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    private fun verifyProfileInRedis(redisService: RedisService, did: String, expected: Profile) {
        val cachedProfile = redisService.getJsonAs<Profile>("profile:$did")
        assertThat(cachedProfile).isNotNull
        assertThat(Jackson.asFormatString(cachedProfile!!)).isEqualTo(Jackson.asFormatString(expected))
    }

    private fun verifyApiPost(endpoint: String, token: String, bodyChecks: Map<String, String>) {
        val verifier = postRequestedFor(urlPathEqualTo(endpoint))
            .withHeader("Authorization", equalTo("Bearer $token"))
        bodyChecks.forEach { (path, value) -> verifier.withRequestBody(matchingJsonPath("\$.$path", equalTo(value))) }
        wireMockServer.verify(verifier)
    }

    private fun verifyApiGet(endpoint: String, token: String, params: Map<String, StringValuePattern> = emptyMap()) {
        val request = getRequestedFor(urlPathEqualTo(endpoint))
            .withHeader("Authorization", equalTo("Bearer $token"))
        for ((key, value) in params) {
            request.withQueryParam(key, value)
        }

        wireMockServer.verify(request)
    }

    private fun verifyNoApiCall(endpoint: String) {
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo(endpoint)))
    }
}