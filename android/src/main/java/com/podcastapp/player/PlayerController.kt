package com.podcastapp.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.podcastapp.data.remote.EpisodeDto
import com.podcastapp.data.repository.LibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerUiState(
    val episode: EpisodeDto? = null,
    val podcastTitle: String = "",
    val coverUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val sleepMinutes: Int? = null,
)

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var sleepJob: Job? = null
    private var lastSyncMs = 0L

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private suspend fun controller(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = MediaController.Builder(context, token).buildAsync().await()
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (!isPlaying) syncProgress(completed = false)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(durationMs = c.duration.coerceAtLeast(0)) }
                }
                if (playbackState == Player.STATE_ENDED) syncProgress(completed = true)
            }
        })
        controller = c
        startTicker()
        return c
    }

    private fun startTicker() {
        scope.launch {
            while (isActive) {
                delay(500)
                val c = controller ?: continue
                if (c.isPlaying) {
                    _state.update { it.copy(positionMs = c.currentPosition, durationMs = c.duration.coerceAtLeast(0)) }
                    val now = System.currentTimeMillis()
                    if (now - lastSyncMs >= 15_000) syncProgress(completed = false)
                }
            }
        }
    }

    fun play(episode: EpisodeDto, podcastTitle: String, coverUrl: String?) {
        scope.launch {
            val c = controller()
            if (_state.value.episode?.id == episode.id) { c.play(); return@launch }
            val startMs = library.localPositionSec(episode.id) * 1000L
            val item = MediaItem.Builder()
                .setMediaId(episode.id)
                .setUri(episode.audioUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(episode.title)
                        .setArtist(podcastTitle)
                        .setArtworkUri(coverUrl?.let { Uri.parse(it) })
                        .build()
                )
                .build()
            c.setMediaItem(item, startMs)
            c.setPlaybackSpeed(_state.value.speed)
            c.prepare()
            c.play()
            _state.update {
                it.copy(episode = episode, podcastTitle = podcastTitle, coverUrl = coverUrl, positionMs = startMs)
            }
        }
    }

    fun togglePlayPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun seekBack() = seekBy(-15_000)
    fun seekForward() = seekBy(30_000)

    private fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val target = (c.currentPosition + deltaMs).coerceIn(0, c.duration.coerceAtLeast(0))
        c.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms); _state.update { it.copy(positionMs = ms) } }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    /** Таймер сну: зупиняє відтворення через [minutes] хвилин; null — вимкнути. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        _state.update { it.copy(sleepMinutes = minutes) }
        if (minutes == null) return
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _state.update { it.copy(sleepMinutes = null) }
        }
    }

    private fun syncProgress(completed: Boolean) {
        val s = _state.value
        val ep = s.episode ?: return
        val posSec = ((controller?.currentPosition ?: s.positionMs) / 1000).toInt()
        lastSyncMs = System.currentTimeMillis()
        scope.launch { library.saveProgress(ep.id, posSec, completed) }
    }
}
