package com.podcastapp.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface PodcastApi {
    @POST("auth/register") suspend fun register(@Body r: RegisterRequest): TokensDto
    @POST("auth/login") suspend fun login(@Body r: LoginRequest): TokensDto
    @POST("auth/refresh") suspend fun refresh(@Body r: RefreshRequest): TokensDto
    @GET("users/me") suspend fun me(): UserDto

    @GET("categories") suspend fun categories(): List<CategoryDto>

    @GET("podcasts") suspend fun podcasts(
        @Query("query") query: String?,
        @Query("category") category: Int?,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): PageDto<PodcastDto>

    @GET("podcasts/{id}") suspend fun podcast(@Path("id") id: String): PodcastDto

    @GET("podcasts/{id}/episodes") suspend fun episodes(
        @Path("id") id: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): PageDto<EpisodeDto>

    @POST("podcasts/{id}/subscribe") suspend fun subscribe(@Path("id") id: String)
    @DELETE("podcasts/{id}/subscribe") suspend fun unsubscribe(@Path("id") id: String)
    @GET("me/subscriptions") suspend fun subscriptions(
        @Query("page") page: Int, @Query("size") size: Int,
    ): PageDto<PodcastDto>

    @POST("episodes/{id}/favorite") suspend fun favorite(@Path("id") id: String)
    @DELETE("episodes/{id}/favorite") suspend fun unfavorite(@Path("id") id: String)
    @GET("me/favorites") suspend fun favorites(
        @Query("page") page: Int, @Query("size") size: Int,
    ): PageDto<EpisodeDto>

    @PUT("episodes/{id}/progress") suspend fun saveProgress(
        @Path("id") id: String, @Body body: ProgressRequest,
    )
    @GET("me/history") suspend fun history(
        @Query("page") page: Int, @Query("size") size: Int,
    ): PageDto<HistoryItemDto>
}
