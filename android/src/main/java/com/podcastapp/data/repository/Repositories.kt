package com.podcastapp.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.podcastapp.data.local.AppDatabase
import com.podcastapp.data.local.CacheEntry
import com.podcastapp.data.local.ProgressEntity
import com.podcastapp.data.local.TokenStore
import com.podcastapp.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val json = Json { ignoreUnknownKeys = true }

/** Перетворює мережеві винятки на зрозумілі користувачу повідомлення. */
fun Throwable.userMessage(): String = when (this) {
    is IOException -> "Немає з'єднання з інтернетом"
    is HttpException -> {
        val parsed = runCatching {
            json.decodeFromString<ErrorDto>(response()?.errorBody()?.string().orEmpty()).message
        }.getOrNull()
        parsed ?: when (code()) {
            401 -> "Сесія завершилась, увійдіть ще раз"
            404 -> "Не знайдено"
            in 500..599 -> "Сервер тимчасово недоступний"
            else -> "Помилка запиту (${code()})"
        }
    }
    else -> message ?: "Невідома помилка"
}

object Validators {
    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    fun isValidEmail(s: String) = email.matches(s.trim())
    fun isValidPassword(s: String) = s.length >= 8
}

/** Універсальний PagingSource для списків із REST API. */
class ApiPagingSource<T : Any>(
    private val load: suspend (page: Int, size: Int) -> PageDto<T>,
) : PagingSource<Int, T>() {
    override fun getRefreshKey(state: PagingState<Int, T>): Int? = null
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 1
        return try {
            val r = load(page, params.loadSize.coerceAtMost(50))
            val hasNext = page.toLong() * r.size < r.total
            LoadResult.Page(r.items, null, if (hasNext) page + 1 else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(Exception(e.userMessage()))
        }
    }
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: PodcastApi,
    private val store: TokenStore,
    private val db: AppDatabase,
) {
    suspend fun login(email: String, password: String) {
        val t = api.login(LoginRequest(email.trim(), password))
        store.saveTokens(t.accessToken, t.refreshToken)
    }

    suspend fun register(email: String, password: String, name: String) {
        val t = api.register(RegisterRequest(email.trim(), password, name.trim()))
        store.saveTokens(t.accessToken, t.refreshToken)
    }

    suspend fun logout() {
        store.clearTokens()
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }
}

@Singleton
class PodcastRepository @Inject constructor(private val api: PodcastApi, private val db: AppDatabase) {

    fun searchPager(query: String?, category: Int?): Flow<PagingData<PodcastDto>> =
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            ApiPagingSource { page, size -> api.podcasts(query?.takeIf { it.isNotBlank() }, category, page, size) }
        }.flow

    fun episodesPager(podcastId: String): Flow<PagingData<EpisodeDto>> =
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            ApiPagingSource { page, size -> api.episodes(podcastId, page, size) }
        }.flow

    suspend fun podcast(id: String): PodcastDto = api.podcast(id)
    suspend fun firstPage(size: Int = 12): List<PodcastDto> = api.podcasts(null, null, 1, size).items

    fun categories(): Flow<List<CategoryDto>> =
        cacheFirst(db, "categories", ListSerializer(CategoryDto.serializer())) { api.categories() }
}

@Singleton
class LibraryRepository @Inject constructor(private val api: PodcastApi, private val db: AppDatabase) {

    fun subscriptions(): Flow<List<PodcastDto>> =
        cacheFirst(db, "subscriptions", ListSerializer(PodcastDto.serializer())) { api.subscriptions(1, 50).items }

    fun favorites(): Flow<List<EpisodeDto>> =
        cacheFirst(db, "favorites", ListSerializer(EpisodeDto.serializer())) { api.favorites(1, 50).items }

    fun history(): Flow<List<HistoryItemDto>> =
        cacheFirst(db, "history", ListSerializer(HistoryItemDto.serializer())) { api.history(1, 50).items }

    fun me(): Flow<UserDto> = cacheFirst(db, "me", UserDto.serializer()) { api.me() }

    suspend fun subscribe(id: String) { api.subscribe(id); db.cacheDao().delete("subscriptions") }
    suspend fun unsubscribe(id: String) { api.unsubscribe(id); db.cacheDao().delete("subscriptions") }
    suspend fun favorite(id: String) { api.favorite(id); db.cacheDao().delete("favorites") }
    suspend fun unfavorite(id: String) { api.unfavorite(id); db.cacheDao().delete("favorites") }

    suspend fun localPositionSec(episodeId: String): Int = db.progressDao().get(episodeId)?.positionSec ?: 0

    /** Зберігає позицію локально завжди, а на сервер — за можливості. */
    suspend fun saveProgress(episodeId: String, positionSec: Int, completed: Boolean) {
        db.progressDao().put(ProgressEntity(episodeId, positionSec, System.currentTimeMillis()))
        try {
            api.saveProgress(episodeId, ProgressRequest(positionSec, completed))
            db.cacheDao().delete("history")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* офлайн: синхронізується при наступному збереженні */ }
    }
}

/** Стратегія «спершу кеш, потім мережа»: емітить збережене значення, а потім свіже. */
fun <T> cacheFirst(db: AppDatabase, key: String, ser: KSerializer<T>, fetch: suspend () -> T): Flow<T> = flow {
    val cached = db.cacheDao().get(key)?.let { runCatching { json.decodeFromString(ser, it.json) }.getOrNull() }
    if (cached != null) emit(cached)
    try {
        val fresh = fetch()
        db.cacheDao().put(CacheEntry(key, json.encodeToString(ser, fresh)))
        emit(fresh)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (cached == null) throw e
    }
}
