package com.simplespider.dy.data

data class AuthorListQueryParams(
    val rate: Int? = null,
    val status: String? = null,
    val isFavor: Boolean? = null,
    val isValid: Boolean? = null,
) {
    fun hasActiveFilter(): Boolean =
        rate != null || status != null || isFavor != null || isValid != null
}
