package com.podcastapp.di

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.podcastapp.BuildConfig
import com.podcastapp.data.local.AppDatabase
import com.podcastapp.data.local.TokenStore
import com.podcastapp.data.remote.AuthInterceptor
import com.podcastapp.data.remote.PodcastApi
import com.podcastapp.data.remote.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private val json = Json { ignoreUnknownKeys = true }

    private fun retrofit(client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun baseClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)

    /** API без авторизатора — використовується лише для оновлення токена. */
    @Provides @Singleton @Named("plain")
    fun plainApi(): PodcastApi = retrofit(baseClient().build()).create(PodcastApi::class.java)

    @Provides @Singleton
    fun api(
        store: TokenStore,
        @Named("plain") plain: Provider<PodcastApi>,
    ): PodcastApi {
        val client = baseClient()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(TokenAuthenticator(store, plain))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            })
            .build()
        return retrofit(client).create(PodcastApi::class.java)
    }

    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "podcasts.db").build()
}
