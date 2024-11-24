package dev.raphaeldelio.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseTest {
    protected lateinit var redisContainer: GenericContainer<*>
    protected lateinit var wireMockServer: WireMockServer

    @BeforeEach
    fun setUp() {
        // Start Redis container
        redisContainer = GenericContainer(DockerImageName.parse("redis:alpine"))
            .withExposedPorts(6379)
        redisContainer.start()

        // Start WireMock server
        wireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMockServer.start()
    }

    @AfterEach
    fun tearDown() {
        redisContainer.stop()
        wireMockServer.stop()
    }

    protected fun getRedisHost(): String = redisContainer.host
    protected fun getRedisPort(): Int = redisContainer.getMappedPort(6379)
    protected fun getWireMockBaseUrl(): String = wireMockServer.baseUrl()
}