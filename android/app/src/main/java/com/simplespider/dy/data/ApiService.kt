package com.simplespider.dy.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("token/")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("dy/author/")
    suspend fun listAuthors(
        @Query("limit") limit: Int,
        @Query("page") page: Int,
        @Query("search") search: String? = null,
        @Query("rate") rate: Int? = null,
        @Query("status") status: String? = null,
        @Query("is_favor") isFavor: Boolean? = null,
    ): PagedAuthors

    @GET("dy/author/{id}/")
    suspend fun getAuthor(@Path("id") id: Int): DyAuthorDto

    @GET("dy/video/")
    suspend fun listVideos(
        @Query("limit") limit: Int,
        @Query("page") page: Int,
        @Query("search") search: String? = null,
        @Query("rate") rate: Int? = null,
        @Query("is_like") isLike: Boolean? = null,
        @Query("is_favor") isFavor: Boolean? = null,
        @Query("author") authorId: Int? = null,
    ): PagedVideos
}
