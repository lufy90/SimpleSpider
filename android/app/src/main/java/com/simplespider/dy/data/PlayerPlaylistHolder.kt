package com.simplespider.dy.data

/**
 * Holds the playlist when navigating to the video player (vertical swipe between items).
 */
object PlayerPlaylistHolder {
    data class Entry(
        val id: Int,
        val title: String,
        val playUrl: String,
        val authorId: Int?,
        val authorAvatarSrc: String?,
        val rate: Float? = null,
        val createTime: String? = null,
    )

    data class PlaylistPagination(
        val limit: Int,
        val nextCursor: String?,
        val previousCursor: String?,
        val search: String?,
        val authorId: Int?,
        val useRandomList: Boolean,
        val query: VideoListQueryParams = VideoListQueryParams(),
    )

    data class PlaylistSnapshot(
        val entries: List<Entry>,
        val startIndex: Int,
        val openedFromAuthorId: Int?,
        val pagination: PlaylistPagination? = null,
    )

    private var entries: List<Entry> = emptyList()
    private var startIndex: Int = 0
    private var openedFromAuthorId: Int? = null
    private var heldPagination: PlaylistPagination? = null

    fun entryFromVideo(v: DyVideoDto): Entry {
        val url = v.playSrc?.trim().orEmpty()
        val fromApi = v.authorAvatarSrc?.trim()?.takeIf { it.isNotEmpty() }
        val avatar = fromApi ?: avatarUrlFromVideoPlayUrl(url)
        return Entry(
            id = v.id,
            title = videoTitle(v),
            playUrl = url,
            authorId = v.author,
            authorAvatarSrc = avatar,
            rate = v.rate,
            createTime = v.createTime?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun setFromVideos(
        playableVideos: List<DyVideoDto>,
        startIndex: Int,
        openedFromAuthorContextId: Int? = null,
        pagination: PlaylistPagination? = null,
    ) {
        openedFromAuthorId = openedFromAuthorContextId
        heldPagination = pagination
        entries = playableVideos.map { entryFromVideo(it) }
        this.startIndex = if (entries.isEmpty()) {
            0
        } else {
            startIndex.coerceIn(0, entries.lastIndex)
        }
    }

    fun takeSnapshotAndClear(): PlaylistSnapshot? {
        if (entries.isEmpty()) return null
        val copy = PlaylistSnapshot(
            entries = entries.toList(),
            startIndex = startIndex,
            openedFromAuthorId = openedFromAuthorId,
            pagination = heldPagination,
        )
        entries = emptyList()
        startIndex = 0
        openedFromAuthorId = null
        heldPagination = null
        return copy
    }

    private fun videoTitle(v: DyVideoDto): String =
        v.desc?.trim()?.takeIf { it.isNotEmpty() } ?: v.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Video"
}
