package dev.raphaeldelio.model

data class Profile(
    val did: String?,
    val handle: String?,
    val displayName: String?,
    val description: String?,
    val avatar: String?,
    val banner: String?,
    val followersCount: Int?,
    val followsCount: Int?,
    val postsCount: Int?,
    val associated: Associated?,
    val joinedViaStarterPack: JoinedViaStarterPack?,
    val indexedAt: String?,
    val createdAt: String?,
    val viewer: Viewer?,
    val labels: List<Label>?,
    val pinnedPost: PinnedPost?
) {
    data class Associated(
        val lists: Int?,
        val feedgens: Int?,
        val starterPacks: Int?,
        val labeler: Boolean?,
        val chat: Chat?
    ) {
        data class Chat(
            val allowIncoming: String?
        )
    }

    data class JoinedViaStarterPack(
        val uri: String?,
        val cid: String?,
        val record: Map<String?, Any>, // Generic map for unstructured data
        val creator: Creator?,
        val listItemCount: Int?,
        val joinedWeekCount: Int?,
        val joinedAllTimeCount: Int?,
        val labels: List<Label>,
        val indexedAt: String
    ) {
        data class Creator(
            val did: String?,
            val handle: String?,
            val displayName: String?,
            val avatar: String?,
            val associated: Associated?,
            val viewer: Viewer?,
            val labels: List<Label>?,
            val createdAt: String?
        )
    }

    data class Viewer(
        val muted: Boolean?,
        val mutedByList: ViewerList?,
        val blockedBy: Boolean?,
        val blocking: String?,
        val blockingByList: ViewerList?,
        val following: String?,
        val followedBy: String?,
        val knownFollowers: KnownFollowers?
    ) {
        data class ViewerList(
            val uri: String?,
            val cid: String?,
            val name: String?,
            val purpose: String?,
            val avatar: String?,
            val listItemCount: Int?,
            val labels: List<Label>?,
            val viewer: ViewerState?,
            val indexedAt: String?
        ) {
            data class ViewerState(
                val muted: Boolean?,
                val blocked: String?
            )
        }

        data class KnownFollowers(
            val count: Int?,
            val followers: List<Follower?>
        ) {
            data class Follower(
                val did: String?,
                val handle: String?,
                val displayName: String?,
                val avatar: String?,
                val associated: Associated?,
                val labels: List<Label>?,
                val createdAt: String?
            )
        }
    }

    data class Label(
        val ver: Int?,
        val src: String?,
        val uri: String?,
        val cid: String?,
        val `val`: String?,
        val neg: Boolean?,
        val cts: String?,
        val exp: String?,
        val sig: String?
    )

    data class PinnedPost(
        val uri: String?,
        val cid: String?
    )
}