package com.simplespider.dy.data

/**
 * Converts user input (host or host:port) into API base URL ending with /api.
 * Uses https by default; prefix with http:// to force plain HTTP.
 * Empty input means use Gradle default (BuildConfig).
 */
fun hostPortInputToApiBaseUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    var scheme = "https"
    var core = trimmed
    when {
        core.startsWith("https://", ignoreCase = true) -> {
            scheme = "https"
            core = core.substring(8)
        }
        core.startsWith("http://", ignoreCase = true) -> {
            scheme = "http"
            core = core.substring(7)
        }
    }
    core = core.trim()
    while (core.endsWith("/")) core = core.dropLast(1)
    if (core.endsWith("/api")) {
        core = core.dropLast(4).trimEnd('/')
    }
    val hostPort = if (':' in core) {
        core
    } else {
        "$core:8000"
    }
    return "$scheme://$hostPort/api"
}
