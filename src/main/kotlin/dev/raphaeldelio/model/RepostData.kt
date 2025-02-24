package dev.raphaeldelio.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class RepostData(
    @JsonProperty("repo") val repo: String,
    @JsonProperty("collection") val collection: String,
    //@JsonProperty("rkey") val rkey: String? = null, // Optional
    @JsonProperty("validate") val validate: Boolean? = false, // Optional
    @JsonProperty("record") val record: RepostRecord
)

data class RepostRecord(
    @JsonProperty("subject") val subject: Subject,
    @JsonProperty("createdAt") val createdAt: String = Instant.now().toString()
)

data class Subject(
    @JsonProperty("uri") val uri: String,
    @JsonProperty("cid") val cid: String
)