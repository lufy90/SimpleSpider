package com.simplespider.dy.data

import com.google.gson.annotations.SerializedName

data class PlaySrcSlideDto(
    val kind: String,
    val index: Int,
    val url: String,
)

data class PlaySrcDto(
    @SerializedName("content_type") val contentType: String,
    val video: String? = null,
    val music: String? = null,
    val slides: List<PlaySrcSlideDto>? = null,
)

fun PlaySrcDto?.isPlayable(): Boolean {
    val src = this ?: return false
    if (src.contentType == "video") return !src.video.isNullOrBlank()
    val slides = src.slides.orEmpty()
    return !src.music.isNullOrBlank() || slides.isNotEmpty()
}

fun PlaySrcDto?.isVideoContent(): Boolean =
    this?.contentType == "video" && !this.video.isNullOrBlank()

fun PlaySrcDto?.isSlidesContent(): Boolean {
    val src = this ?: return false
    return src.contentType == "photo_slides" || src.contentType == "video_slides"
}

fun PlaySrcDto?.primaryVideoUrl(): String? =
    if (isVideoContent()) this?.video?.trim()?.takeIf { it.isNotEmpty() } else null

fun PlaySrcDto?.prefetchUrl(): String? {
    val src = this ?: return null
    primaryVideoUrl()?.let { return it }
    src.music?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return src.slides.orEmpty().firstOrNull()?.url?.trim()?.takeIf { it.isNotEmpty() }
}

fun PlaySrcDto?.avatarSourceUrl(): String? {
    val src = this ?: return null
    primaryVideoUrl()?.let { return it }
    src.music?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return src.slides.orEmpty().firstOrNull()?.url?.trim()?.takeIf { it.isNotEmpty() }
}
