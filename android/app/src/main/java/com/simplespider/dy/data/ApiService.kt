package com.simplespider.dy.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("token/")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("dy/author-view/")
    suspend fun listAuthors(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String? = null,
        @Query("search") search: String? = null,
        @Query("rate") rate: Int? = null,
        @Query("status") status: String? = null,
        @Query("is_favor") isFavor: Boolean? = null,
    ): PagedAuthors

    @GET("dy/author/{id}/")
    suspend fun getAuthor(@Path("id") id: Int): DyAuthorDto

    @PATCH("dy/author/{id}/")
    suspend fun patchAuthor(@Path("id") id: Int, @Body body: RatePatchBody): DyAuthorDto

    @GET("dy/video-view/")
    suspend fun listVideos(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String? = null,
        @Query("search") search: String? = null,
        @Query("rate") rate: Int? = null,
        @Query("min_rate") minRate: Int? = null,
        @Query("max_rate") maxRate: Int? = null,
        @Query("is_like") isLike: Boolean? = null,
        @Query("is_favor") isFavor: Boolean? = null,
        @Query("status") status: String? = null,
        @Query("author") authorId: Int? = null,
        @Query("random") random: String? = null,
    ): PagedVideos

    @PATCH("dy/video/{id}/")
    suspend fun patchVideo(@Path("id") id: Int, @Body body: RatePatchBody): DyVideoDto

}
