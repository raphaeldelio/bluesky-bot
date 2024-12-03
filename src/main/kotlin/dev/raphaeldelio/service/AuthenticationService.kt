package dev.raphaeldelio.service

import dev.raphaeldelio.Logger
import dev.raphaeldelio.model.*
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Jackson

class AuthenticationService(
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
            redisService.stringSet("did", result.did)
            Logger.info("✅ Login successful. DID: ${result.did}")
            result.accessJwt
        } else {
            Logger.info("⚠️ Authentication failed: ${response.status}")
            ""
        }
    }
}