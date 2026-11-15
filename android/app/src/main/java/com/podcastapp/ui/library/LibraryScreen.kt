package com.podcastapp.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.podcastapp.data.remote.EpisodeDto
import com.podcastapp.data.remote.HistoryItemDto
import com.podcastapp.data.remote.PodcastDto
import com.podcastapp.data.repository.LibraryRepository
import com.podcastapp.ui.common.PodcastRow
import com.podcastapp.ui.common.StateContent
import com.podcastapp.ui.common.UiState
import com.podcastapp.ui.common.asUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(private val repo: LibraryRepository) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    private fun <T> section(source: () -> kotlinx.coroutines.flow.Flow<T>): StateFlow<UiState<T>> =
        refresh.flatMapLatest { source().asUiState() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val subscriptions = section { repo.subscriptions() }
    val favorites = section { repo.favorites() }
    val history = section { repo.history() }

    fun reload() { refresh.value++ }
}

@Composable
fun LibraryScreen(onPodcast: (String) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Підписки", "Обране", "Історія")
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> {
                val s by vm.subscriptions.collectAsStateWithLifecycle()
                StateContent(s, vm::reload) { list: List<PodcastDto> ->
                    if (list.isEmpty()) Empty("Ви ще ні на що не підписані")
                    else LazyColumn { items(list) { p -> PodcastRow(p) { onPodcast(p.id) } } }
                }
            }
            1 -> {
                val s by vm.favorites.collectAsStateWithLifecycle()
                StateContent(s, vm::reload) { list: List<EpisodeDto> ->
                    if (list.isEmpty()) Empty("Тут з'являться обрані епізоди")
                    else LazyColumn {
                        items(list) { e ->
                            Text(
                                e.title, style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().clickable { onPodcast(e.podcastId) }.padding(16.dp),
                            )
                        }
                    }
                }
            }
            else -> {
                val s by vm.history.collectAsStateWithLifecycle()
                StateContent(s, vm::reload) { list: List<HistoryItemDto> ->
                    if (list.isEmpty()) Empty("Історія порожня")
                    else LazyColumn {
                        items(list) { h ->
                            Column(Modifier.fillMaxWidth().clickable { onPodcast(h.episode.podcastId) }.padding(16.dp, 8.dp)) {
                                Text(h.episode.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (h.completed) "Прослухано" else "Зупинились на ${h.positionSec / 60} хв ${h.positionSec % 60} с",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) = Text(text, modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
