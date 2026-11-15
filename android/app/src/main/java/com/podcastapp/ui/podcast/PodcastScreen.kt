package com.podcastapp.ui.podcast

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.podcastapp.data.remote.EpisodeDto
import com.podcastapp.data.remote.PodcastDto
import com.podcastapp.data.repository.LibraryRepository
import com.podcastapp.data.repository.PodcastRepository
import com.podcastapp.data.repository.userMessage
import com.podcastapp.player.PlayerController
import com.podcastapp.ui.common.Cover
import com.podcastapp.ui.common.StateContent
import com.podcastapp.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastDetails(
    val podcast: PodcastDto,
    val subscribed: Boolean,
    val favorites: Set<String>,
    val progressSec: Map<String, Int>,
)

@HiltViewModel
class PodcastViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val podcasts: PodcastRepository,
    private val library: LibraryRepository,
    private val player: PlayerController,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["id"])
    val episodes = podcasts.episodesPager(id).cachedIn(viewModelScope)

    private val _state = MutableStateFlow<UiState<PodcastDetails>>(UiState.Loading)
    val state = _state.asStateFlow()
    val message = MutableStateFlow<String?>(null)

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val p = podcasts.podcast(id)
                val subs = runCatching { library.subscriptions().first() }.getOrDefault(emptyList())
                val favs = runCatching { library.favorites().first() }.getOrDefault(emptyList())
                val hist = runCatching { library.history().first() }.getOrDefault(emptyList())
                _state.value = UiState.Success(
                    PodcastDetails(
                        p, subs.any { it.id == id }, favs.map { it.id }.toSet(),
                        hist.associate { it.episode.id to it.positionSec },
                    )
                )
            } catch (e: Exception) {
                _state.value = UiState.Error(e.userMessage())
            }
        }
    }

    private fun update(block: (PodcastDetails) -> PodcastDetails) {
        (_state.value as? UiState.Success)?.let { _state.value = UiState.Success(block(it.data)) }
    }

    fun toggleSubscribe() {
        val cur = (_state.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            try {
                if (cur.subscribed) library.unsubscribe(id) else library.subscribe(id)
                update { it.copy(subscribed = !cur.subscribed) }
            } catch (e: Exception) { message.value = e.userMessage() }
        }
    }

    fun toggleFavorite(ep: EpisodeDto) {
        val cur = (_state.value as? UiState.Success)?.data ?: return
        val isFav = ep.id in cur.favorites
        viewModelScope.launch {
            try {
                if (isFav) library.unfavorite(ep.id) else library.favorite(ep.id)
                update { it.copy(favorites = if (isFav) it.favorites - ep.id else it.favorites + ep.id) }
            } catch (e: Exception) { message.value = e.userMessage() }
        }
    }

    fun play(ep: EpisodeDto) {
        val p = (_state.value as? UiState.Success)?.data?.podcast ?: return
        player.play(ep, p.title, p.coverUrl)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PodcastScreen(vm: PodcastViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val paging = vm.episodes.collectAsLazyPagingItems()

    StateContent(state, onRetry = vm::load) { d ->
        LazyColumn {
            item {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cover(d.podcast.coverUrl, Modifier.size(110.dp))
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(d.podcast.title, style = MaterialTheme.typography.titleLarge)
                            Text(d.podcast.author, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    var expanded by remember { mutableStateOf(false) }
                    d.podcast.description?.let {
                        Text(
                            it, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp).combinedClickable(onClick = { expanded = !expanded }),
                        )
                    }
                    if (d.subscribed) {
                        OutlinedButton(vm::toggleSubscribe, Modifier.padding(top = 12.dp)) { Text("Ви підписані") }
                    } else {
                        Button(vm::toggleSubscribe, Modifier.padding(top = 12.dp)) { Text("Підписатися") }
                    }
                    msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider()
            }
            items(count = paging.itemCount, key = paging.itemKey { it.id }) { i ->
                paging[i]?.let { ep ->
                    EpisodeRow(
                        ep, isFav = ep.id in d.favorites, progressSec = d.progressSec[ep.id],
                        onPlay = { vm.play(ep) }, onFavorite = { vm.toggleFavorite(ep) },
                    )
                }
            }
            if (paging.loadState.append is LoadState.Loading || paging.loadState.refresh is LoadState.Loading) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: EpisodeDto, isFav: Boolean, progressSec: Int?, onPlay: () -> Unit, onFavorite: () -> Unit) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onPlay, onLongClick = onFavorite).padding(16.dp, 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ep.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ep.publishedAt.take(10), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onFavorite) {
                Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "В обране")
            }
        }
        val dur = ep.durationSec
        if (progressSec != null && dur != null && dur > 0) {
            LinearProgressIndicator(progress = { (progressSec.toFloat() / dur).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
