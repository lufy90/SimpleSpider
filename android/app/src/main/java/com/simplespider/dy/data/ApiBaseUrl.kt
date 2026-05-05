package com.simplespider.dy.data

/**
 * Converts user input (host or host:port) into http API base URL ending with /api.
 * Empty input means use Gradle default (BuildConfig).
 */
fun hostPortInputToApiBaseUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    var core = trimmed
        .removePrefix("http://")
        .removePrefix("https://")
        .trim()
    while (core.endsWith("/")) core = core.dropLast(1)
    if (core.endsWith("/api")) {
        core = core.dropLast(4).trimEnd('/')
    }
    val hostPort = if (':' in core) {
        core
    } else {
        "$core:8000"
    }
    return "http://$hostPort/api"
}
