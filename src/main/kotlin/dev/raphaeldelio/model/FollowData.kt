package dev.raphaeldelio.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class FollowData(
    @JsonProperty("repo") val repo: String,
    @JsonProperty("collection") val collection: String = "app.bsky.graph.follow", // Namespace for follows
    @JsonProperty("rkey") val rkey: String? = "",
    @JsonProperty("validate") val validate: Boolean? = false,
    @JsonProperty("record") val record: FollowRecord
)

data class FollowRecord(
    @JsonProperty("subject") val subject: String, // DID of the user to follow
    @JsonProperty("createdAt") val createdAt: String = Instant.now().toString()
)
