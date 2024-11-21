package dev.raphaeldelio.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginResponse(
    @JsonProperty("accessJwt") val accessJwt: String,
    @JsonProperty("refreshJwt") val refreshJwt: String,
    @JsonProperty("handle") val handle: String,
    @JsonProperty("did") val did: String,
    @JsonProperty("didDoc") val didDoc: DidDoc?,
    @JsonProperty("email") val email: String?,
    @JsonProperty("emailConfirmed") val emailConfirmed: Boolean?,
    @JsonProperty("emailAuthFactor") val emailAuthFactor: Boolean?,
    @JsonProperty("active") val active: Boolean,
    @JsonProperty("status") val status: String? // Possible values: takendown, suspended, deactivated
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DidDoc(
    @JsonProperty("id") val id: String?,
    @JsonProperty("service") val service: List<Map<String, Any>>? // Adjust based on actual structure
)