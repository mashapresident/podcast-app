package com.podcastapp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.podcastapp.data.remote.CategoryDto
import com.podcastapp.data.remote.HistoryItemDto
import com.podcastapp.data.remote.PodcastDto
import com.podcastapp.data.repository.LibraryRepository
import com.podcastapp.data.repository.PodcastRepository
import com.podcastapp.data.repository.userMessage
import com.podcastapp.ui.common.Cover
import com.podcastapp.ui.common.PodcastRow
import com.podcastapp.ui.common.StateContent
import com.podcastapp.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeData(
    val categories: List<CategoryDto>,
    val continueListening: List<HistoryItemDto>,
    val podcasts: List<PodcastDto>,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val podcasts: PodcastRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val data = coroutineScope {
                    val cats = async { podcasts.categories().first() }
                    val hist = async { runCatching { library.history().first() }.getOrDefault(emptyList()) }
                    val list = async { podcasts.firstPage() }
                    HomeData(cats.await(), hist.await().filter { !it.completed }.take(10), list.await())
                }
                _state.value = UiState.Success(data)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.userMessage())
            }
        }
    }
}

@Composable
fun HomeScreen(
    onPodcast: (String) -> Unit,
    onCategory: (Int) -> Unit,
    onEpisodePodcast: (String) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    StateContent(state, onRetry = vm::load) { d ->
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            item { Text("Категорії", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 8.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(d.categories) { c ->
                        AssistChip(onClick = { onCategory(c.id) }, label = { Text(c.name) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
            if (d.continueListening.isNotEmpty()) {
                item { Text("Продовжити слухати", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(d.continueListening) { h ->
                            Column(
                                Modifier.width(140.dp).padding(end = 12.dp)
                                    .clickable { onEpisodePodcast(h.episode.podcastId) },
                            ) {
                                Text(h.episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text("з ${h.positionSec / 60} хв", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item { Text("Каталог", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)) }
            items(d.podcasts) { p -> PodcastRow(p) { onPodcast(p.id) } }
            if (d.podcasts.isEmpty()) {
                item { Text("Каталог порожній. Імпортуйте подкаст через API (POST /podcasts/import).", modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}
