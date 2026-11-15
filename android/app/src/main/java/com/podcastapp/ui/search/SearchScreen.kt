package com.podcastapp.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.podcastapp.data.repository.PodcastRepository
import com.podcastapp.ui.common.ErrorBox
import com.podcastapp.ui.common.PodcastRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    repo: PodcastRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val query = MutableStateFlow("")
    private val category = MutableStateFlow(savedState.get<Int>("category")?.takeIf { it >= 0 })

    /** Запит надсилається через 400 мс після останнього символу, щоб не навантажувати сервер. */
    val results = combine(query.debounce(400).distinctUntilChanged(), category) { q, c -> q to c }
        .flatMapLatest { (q, c) -> repo.searchPager(q, c) }
        .cachedIn(viewModelScope)
}

@Composable
fun SearchScreen(onPodcast: (String) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val q by vm.query.collectAsStateWithLifecycle()
    val paging = vm.results.collectAsLazyPagingItems()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            q, { vm.query.value = it }, singleLine = true, label = { Text("Назва або автор") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        val refresh = paging.loadState.refresh
        when {
            refresh is LoadState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            refresh is LoadState.Error -> ErrorBox(refresh.error.message ?: "Помилка") { paging.retry() }
            paging.itemCount == 0 -> Text("Нічого не знайдено", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            else -> LazyColumn {
                items(count = paging.itemCount, key = paging.itemKey { it.id }, contentType = paging.itemContentType { "p" }) { i ->
                    paging[i]?.let { p -> PodcastRow(p) { onPodcast(p.id) } }
                }
                if (paging.loadState.append is LoadState.Loading) {
                    item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
}
