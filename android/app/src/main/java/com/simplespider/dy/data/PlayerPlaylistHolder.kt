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
    )

    data class PlaylistSnapshot(
        val entries: List<Entry>,
        val startIndex: Int,
        val openedFromAuthorId: Int?,
    )

    private var entries: List<Entry> = emptyList()
    private var startIndex: Int = 0
    private var openedFromAuthorId: Int? = null

    fun setFromVideos(
        playableVideos: List<DyVideoDto>,
        startIndex: Int,
        openedFromAuthorContextId: Int? = null,
    ) {
        openedFromAuthorId = openedFromAuthorContextId
        entries = playableVideos.map { v ->
            val url = v.playSrc?.trim().orEmpty()
            val fromApi = v.authorAvatarSrc?.trim()?.takeIf { it.isNotEmpty() }
            val avatar = fromApi ?: avatarUrlFromVideoPlayUrl(url)
            Entry(
                id = v.id,
                title = videoTitle(v),
                playUrl = url,
                authorId = v.author,
                authorAvatarSrc = avatar,
            )
        }
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
        )
        entries = emptyList()
        startIndex = 0
        openedFromAuthorId = null
        return copy
    }

    private fun videoTitle(v: DyVideoDto): String =
        v.desc?.trim()?.takeIf { it.isNotEmpty() } ?: v.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Video"
}
