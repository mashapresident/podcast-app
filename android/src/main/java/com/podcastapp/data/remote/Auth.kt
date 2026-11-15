package com.podcastapp.data.remote

import com.podcastapp.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider

/** Додає access-токен до всіх запитів, крім /auth/*. */
class AuthInterceptor @Inject constructor(private val store: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.contains("/auth/")) return chain.proceed(request)
        val token = runBlocking { store.accessToken() }
        val withAuth = if (token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else request
        return chain.proceed(withAuth)
    }
}

/** При 401 один раз пробує обміняти refresh-токен на нову пару й повторити запит. */
class TokenAuthenticator(
    private val store: TokenStore,
    private val plainApi: Provider<PodcastApi>,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("X-Retried") != null) return null
        if (response.request.url.encodedPath.contains("/auth/")) return null
        return runBlocking {
            val refresh = store.refreshToken() ?: return@runBlocking null
            try {
                val tokens = plainApi.get().refresh(RefreshRequest(refresh))
                store.saveTokens(tokens.accessToken, tokens.refreshToken)
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${tokens.accessToken}")
                    .header("X-Retried", "1")
                    .build()
            } catch (e: Exception) {
                store.clearTokens() // сесія недійсна → UI поверне на екран входу
                null
            }
        }
    }
}
