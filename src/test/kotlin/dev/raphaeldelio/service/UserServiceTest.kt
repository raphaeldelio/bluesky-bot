package dev.raphaeldelio.service

import com.github.tomakehurst.wiremock.client.WireMock.*
import dev.raphaeldelio.model.BlueskyConfig
import org.assertj.core.api.Assertions.assertThat
import org.http4k.format.Jackson
import org.junit.jupiter.api.Test
import redis.clients.jedis.JedisPool

class UserServiceTest : BaseTest() {

    private fun createRedisService(): RedisService {
        val jedisPool = JedisPool(getRedisHost(), getRedisPort())
        return RedisService(jedisPool)
    }

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
        wireMockServer.stubFor(
            post(urlPathEqualTo("/com.atproto.repo.createRecord"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(Jackson.asFormatString(mapOf("success" to true)))
                )
        )

        val userService = createUserService()
        val redisService = createRedisService()
        redisService.set("did", "did:example:123")

        // Act
        userService.followUser("test-access-token", authorDid)

        // Assert
        assertThat(redisService.setContains("followedAuthors", authorDid)).isTrue

        // Verify the API call was made
        wireMockServer.verify(
            postRequestedFor(urlPathEqualTo("/com.atproto.repo.createRecord"))
                .withHeader("Authorization", equalTo("Bearer test-access-token"))
                .withRequestBody(matchingJsonPath("$.record.subject", equalTo(authorDid)))
        )
    }
}