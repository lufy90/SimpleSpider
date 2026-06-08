package com.simplespider.dy.data

import android.net.Uri

fun cursorFromPaginationLink(link: String?): String? {
    if (link.isNullOrBlank()) return null
    return Uri.parse(link).getQueryParameter("cursor")?.takeIf { it.isNotEmpty() }
}

fun hasMoreFromPaginationLink(link: String?): Boolean = !link.isNullOrBlank()

@Deprecated("Use cursorFromPaginationLink", ReplaceWith("cursorFromPaginationLink(link)"))
fun cursorFromNextLink(next: String?): String? = cursorFromPaginationLink(next)

@Deprecated("Use hasMoreFromPaginationLink", ReplaceWith("hasMoreFromPaginationLink(link)"))
fun hasMoreFromNextLink(next: String?): Boolean = hasMoreFromPaginationLink(next)
