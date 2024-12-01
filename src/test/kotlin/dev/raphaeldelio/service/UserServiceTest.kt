package dev.raphaeldelio.service

import com.github.tomakehurst.wiremock.client.WireMock.*
import dev.raphaeldelio.model.BlueskyConfig
import dev.raphaeldelio.model.Profile
import dev.raphaeldelio.model.Profiles
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
        stubApiGet("/app.bsky.actor.getProfile/", token, expectedProfile, mapOf("actor" to did))

        val userService = createUserService()
        val redisService = createRedisService()

        // Act
        val actualProfile = userService.getProfile(token, did)

        // Assert
        verifyProfile(actualProfile, expectedProfile)
        verifyProfileInRedis(redisService, did, expectedProfile)
        verifyApiGet("/app.bsky.actor.getProfile/", token, mapOf("actor" to did))
    }

    @Test
    fun `should fetch profile from Redis when available`() {
        // Arrange
        val did = "did:example:123"
        val expectedProfile = createTestProfile(did, handle = "stored-handle", displayName = "Stored User")
        val redisService = createRedisService()
        storeProfileInRedis(redisService, did, expectedProfile)

        val userService = createUserService()

        // Act
        val actualProfile = userService.getProfile("unused-token", did)

        // Assert
        verifyProfile(actualProfile, expectedProfile)
        verifyNoApiCall("/profile/$did")
    }


    @Test
    fun `should fetch profiles from API and store in Redis`() {
        // Arrange
        val dids = listOf("raphaeldelio.dev", "did:plc:evzf2dhqjtmtxcotqz56sdia")
        val token = "test-access-token"
        val expectedProfiles = Profiles(dids.map { createTestProfile(it) })

        stubApiGet(
            endpoint = "/app.bsky.actor.getProfiles",
            token = token,
            responseBody = expectedProfiles,
            params = mapOf("actors" to dids)
        )

        val userService = createUserService()
        val redisService = createRedisService()

        // Act
        val profiles = userService.getProfiles(token, dids.toSet())

        // Assert
        assertThat(profiles).hasSameSizeAs(dids)
        profiles.forEach { verifyProfile(it, expectedProfiles.profiles.first { profile -> profile.did == it.did }) }
        dids.forEach { verifyProfileInRedis(redisService, it, createTestProfile(it)) }
        verifyApiGet("/app.bsky.actor.getProfiles", token, mapOf("actors" to dids))
    }

    @Test
    fun `should fetch profiles from Redis when already stored`() {
        // Arrange
        val dids = setOf("did:example:1", "did:example:2")
        val redisService = createRedisService()
        dids.forEach {
            storeProfileInRedis(redisService, it, createTestProfile(it, handle = "stored-handle"))
        }

        val userService = createUserService()

        // Act
        val profiles = userService.getProfiles("unused-token", dids)

        // Assert
        profiles.forEach { verifyProfile(it, createTestProfile(it.did!!, handle = "stored-handle")) }
        verifyNoApiCall("/app.bsky.actor.getProfiles/")
    }

    // Helper Functions
    private fun createTestProfile(
        did: String,
        handle: String = "test-handle",
        displayName: String = "Test User"
    ): Profile {
        return Profile(
            did = did,
            handle = handle,
            displayName = displayName,
            description = "Test Description",
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

    private fun stubApiGet(
        endpoint: String,
        token: String,
        responseBody: Any,
        params: Map<String, Any> = emptyMap()
    ) {
        val stub = get(
            urlPathMatching("$endpoint.*"))
            .withHeader("Authorization", equalTo("Bearer $token"))

        params.forEach { (key, value) ->
            if (value !is Collection<*>) {
                stub.withQueryParam(key, equalTo(value.toString()))
            }
        }

        wireMockServer.stubFor(
            stub.willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(Jackson.asFormatString(responseBody))
            )
        )
    }

    private fun storeProfileInRedis(redisService: RedisService, did: String, profile: Profile) {
        redisService.jsonSet("profile:$did", profile)
    }

    private fun verifyProfile(actual: Profile?, expected: Profile) {
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    private fun verifyProfileInRedis(redisService: RedisService, did: String, expected: Profile) {
        val storedProfile = redisService.jsonGetAs<Profile>("profile:$did")
        assertThat(storedProfile).isNotNull
        assertThat(Jackson.asFormatString(storedProfile!!)).isEqualTo(Jackson.asFormatString(expected))
    }

    private fun verifyApiPost(endpoint: String, token: String, bodyChecks: Map<String, String>) {
        val verifier = postRequestedFor(urlPathEqualTo(endpoint))
            .withHeader("Authorization", equalTo("Bearer $token"))
        bodyChecks.forEach { (path, value) -> verifier.withRequestBody(matchingJsonPath("\$.$path", equalTo(value))) }
        wireMockServer.verify(verifier)
    }

    private fun verifyApiGet(endpoint: String, token: String, params: Map<String, Any> = emptyMap()) {
        val request = getRequestedFor(urlPathMatching("$endpoint.*"))
            .withHeader("Authorization", equalTo("Bearer $token"))

        params.forEach { (key, value) ->
            if (value is List<*>) {
                request.withQueryParam(key, havingExactly(*value.map { equalTo(it.toString()) }.toTypedArray()))
            } else {
                request.withQueryParam(key, equalTo(value.toString()))
            }
        }

        wireMockServer.verify(request)
    }

    private fun verifyNoApiCall(endpoint: String) {
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo(endpoint)))
    }
}