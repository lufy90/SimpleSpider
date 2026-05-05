package com.simplespider.dy.data

import android.net.Uri

/**
 * Builds author avatar URL from a video play URL when it matches
 * .../dydata/{authorDir}/{videoDir}/video.mp4 -> .../dydata/{authorDir}/avatar.jpg
 */
fun avatarUrlFromVideoPlayUrl(playUrl: String): String? {
    val trimmed = playUrl.trim()
    if (trimmed.isEmpty()) return null
    val u = Uri.parse(trimmed)
    if (u.scheme.isNullOrBlank() || u.host.isNullOrBlank()) return null
    val segs = u.pathSegments
    if (segs.size < 4) return null
    if (!segs[0].equals("dydata", ignoreCase = true)) return null
    val authorDir = segs[1]
    if (authorDir.isEmpty()) return null
    val fileName = segs.last()
    if (!fileName.equals("video.mp4", ignoreCase = true) &&
        !fileName.endsWith(".mp4", ignoreCase = true)
    ) {
        return null
    }
    return Uri.Builder()
        .scheme(u.scheme)
        .encodedAuthority(u.encodedAuthority)
        .appendPath("dydata")
        .appendPath(authorDir)
        .appendPath("avatar.jpg")
        .build()
        .toString()
}
