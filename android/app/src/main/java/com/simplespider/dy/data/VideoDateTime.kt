package com.simplespider.dy.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private val flexibleParser = DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE)
    .appendLiteral('T')
    .appendValue(ChronoField.HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .optionalStart()
    .appendLiteral(':')
    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
    .optionalEnd()
    .optionalStart()
    .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
    .optionalEnd()
    .optionalStart()
    .appendOffset("+HH:MM", "Z")
    .optionalEnd()
    .toFormatter()

fun formatVideoDateTime(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    val zone = ZoneId.systemDefault()

    parseToZoned(trimmed, zone)?.let { return it.format(displayFormatter) }
    return fallbackDisplay(trimmed)
}

private fun parseToZoned(raw: String, zone: ZoneId): ZonedDateTime? {
    try {
        return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .atZoneSameInstant(zone)
    } catch (_: Exception) {
    }
    try {
        return Instant.parse(raw).atZone(zone)
    } catch (_: Exception) {
    }
    try {
        return ZonedDateTime.parse(raw, DateTimeFormatter.ISO_ZONED_DATE_TIME)
            .withZoneSameInstant(zone)
    } catch (_: Exception) {
    }
    try {
        return flexibleParser.parse(raw) { accessor ->
            if (accessor.isSupported(ChronoField.OFFSET_SECONDS)) {
                OffsetDateTime.from(accessor).atZoneSameInstant(zone)
            } else {
                LocalDateTime.from(accessor).atZone(zone)
            }
        }
    } catch (_: Exception) {
    }
    try {
        return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone)
    } catch (_: Exception) {
    }
    return null
}

private fun fallbackDisplay(raw: String): String? {
    val cleaned = raw
        .replace('T', ' ')
        .replace(Regex("\\.\\d+"), "")
        .replace(Regex("[Zz]$"), "")
        .replace(Regex("[+-]\\d{2}:\\d{2}$"), "")
        .trim()
    return cleaned.takeIf { it.length >= 10 }
}
