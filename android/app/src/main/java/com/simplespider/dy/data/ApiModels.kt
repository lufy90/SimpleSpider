package com.simplespider.dy.data

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val username: String,
    val password: String,
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
)

data class RatePatchBody(
    val rate: Int,
)

data class PagedAuthors(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<DyAuthorDto>?,
)

data class PagedVideos(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<DyVideoDto>?,
)

data class DyAuthorDto(
    val id: Int,
    val name: String?,
    val path: String?,
    val url: String?,
    @SerializedName("unique_id") val uniqueId: String?,
    @SerializedName("avatar_src") val avatarSrc: String?,
    val rate: Float?,
    val status: String?,
    @SerializedName("is_favor") val isFavor: Boolean?,
    @SerializedName("is_valid") val isValid: Boolean?,
    val desc: String?,
)

data class DyVideoDto(
    val id: Int,
    val name: String?,
    val desc: String?,
    val path: String?,
    @SerializedName("cover_src") val coverSrc: String?,
    @SerializedName("play_src") val playSrc: String?,
    @SerializedName("author_name") val authorName: String?,
    val author: Int?,
    val rate: Float?,
    @SerializedName("is_like") val isLike: Boolean?,
    @SerializedName("is_favor") val isFavor: Boolean?,
    @SerializedName("author_avatar_src") val authorAvatarSrc: String? = null,
    @JsonAdapter(FlexibleDateTimeAdapter::class)
    @SerializedName("create_time") val createTime: String? = null,
)
