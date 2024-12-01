package dev.raphaeldelio.service

import com.github.tomakehurst.wiremock.client.WireMock.*
import dev.raphaeldelio.model.BlueskyConfig
import org.assertj.core.api.Assertions.assertThat
import org.http4k.format.Jackson
import org.junit.jupiter.api.Test

class AuthenticationServiceTest : BaseTest() {

    private fun createBlueskyService(): AuthenticationService {
        val redisService = createRedisService()
        val blueskyConfig = BlueskyConfig(
            apiurl = getWireMockBaseUrl(),
            username = "test-user",
            password = "test-password"
        )
        return AuthenticationService(blueskyConfig, redisService)
    }

    private fun stubAuthenticationResponse() {
        wireMockServer.stubFor(
            post(urlEqualTo("/com.atproto.server.createSession"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(Jackson.asFormatString(mapOf(
                            "accessJwt" to "test-access-token",
                            "refreshJwt" to "test-refresh-token",
                            "handle" to "example.handle",
                            "did" to "did:example:123",
                            "email" to "user@example.com",
                            "emailConfirmed" to true,
                            "active" to true,
                            "status" to "active"
                        )))
                )
        )
    }

    @Test
    fun `should authenticate and get access token with detailed response`() {
        // Arrange
        stubAuthenticationResponse()
        val blueskyService = createBlueskyService()

        // Act
        val accessToken = blueskyService.getAccessToken()

        // Assert
        assertThat(accessToken).isEqualTo("test-access-token")
        val redisService = createRedisService()
        assertThat(redisService.get("did")).isEqualTo("did:example:123")
    }
}