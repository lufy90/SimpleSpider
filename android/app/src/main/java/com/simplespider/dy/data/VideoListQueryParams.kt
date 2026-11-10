package com.simplespider.dy.data

/**
 * Optional filters applied to GET dy/video/ only.
 */
data class VideoListQueryParams(
    val rate: Int? = null,
    val minRate: Int? = null,
    val maxRate: Int? = null,
    val isLike: Boolean? = null,
    val isFavor: Boolean? = null,
    val status: String? = null,
) {
    fun hasActiveFilter(): Boolean =
        rate != null || minRate != null || maxRate != null ||
            isLike != null || isFavor != null || status != null
}
