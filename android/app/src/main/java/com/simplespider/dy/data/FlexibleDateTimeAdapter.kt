package com.simplespider.dy.data

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.Instant

/**
 * Accepts ISO date strings or Unix epoch seconds from the API for create_time.
 */
class FlexibleDateTimeAdapter : TypeAdapter<String?>() {
    override fun write(out: JsonWriter, value: String?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value)
        }
    }

    override fun read(`in`: JsonReader): String? {
        return when (`in`.peek()) {
            JsonToken.NULL -> {
                `in`.nextNull()
                null
            }
            JsonToken.STRING -> `in`.nextString()?.trim()?.takeIf { it.isNotEmpty() }
            JsonToken.NUMBER -> {
                val epoch = `in`.nextLong()
                Instant.ofEpochSecond(epoch).toString()
            }
            else -> {
                `in`.skipValue()
                null
            }
        }
    }
}
