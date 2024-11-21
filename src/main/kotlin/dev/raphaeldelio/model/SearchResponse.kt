package dev.raphaeldelio.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchResponse(
    @JsonProperty("cursor") val cursor: String?,
    @JsonProperty("hitsTotal") val hitsTotal: Int?,
    @JsonProperty("posts") val posts: List<Post>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Post(
    @JsonProperty("uri") val uri: String,
    @JsonProperty("cid") val cid: String,
    @JsonProperty("author") val author: Author,
    @JsonProperty("indexedAt") val indexedAt: String,
    @JsonProperty("record") val record: Record?,
    @JsonProperty("replyCount") val replyCount: Int?,
    @JsonProperty("repostCount") val repostCount: Int?,
    @JsonProperty("likeCount") val likeCount: Int?,
    @JsonProperty("quoteCount") val quoteCount: Int?,

)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Author(
    @JsonProperty("did") val did: String,
    @JsonProperty("handle") val handle: String,
    @JsonProperty("displayName") val displayName: String?,
    @JsonProperty("avatar") val avatar: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Record(
    @JsonProperty("text") val text: String?,
    @JsonProperty("embed") val embed: Embed?,
    @JsonProperty("createdAt") val createdAt: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Embed(
    @JsonProperty("images") val images: List<Image>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Image(
    @JsonProperty("thumb") val thumb: String? = null, // Nullable to handle missing values
    @JsonProperty("fullsize") val fullsize: String? = null,
    @JsonProperty("alt") val alt: String? = null // Alt text is also optional
)