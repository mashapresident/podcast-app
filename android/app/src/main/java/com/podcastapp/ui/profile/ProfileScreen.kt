package com.podcastapp.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.podcastapp.data.local.ThemeMode
import com.podcastapp.data.local.TokenStore
import com.podcastapp.data.remote.UserDto
import com.podcastapp.data.repository.AuthRepository
import com.podcastapp.data.repository.LibraryRepository
import com.podcastapp.ui.common.StateContent
import com.podcastapp.ui.common.UiState
import com.podcastapp.ui.common.asUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    library: LibraryRepository,
    private val auth: AuthRepository,
    private val store: TokenStore,
) : ViewModel() {
    val user = library.me().asUiState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)
    val theme = store.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setTheme(m: ThemeMode) = viewModelScope.launch { store.setTheme(m) }
    fun logout() = viewModelScope.launch { auth.logout() }
}

@Composable
fun ProfileScreen(vm: ProfileViewModel = hiltViewModel()) {
    val user by vm.user.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp)) {
        StateContent(user, onRetry = {}) { u: UserDto ->
            Text(u.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(u.email, style = MaterialTheme.typography.bodyMedium)
        }
        Text("Тема", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        listOf(ThemeMode.SYSTEM to "Системна", ThemeMode.LIGHT to "Світла", ThemeMode.DARK to "Темна").forEach { (m, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = theme == m, onClick = { vm.setTheme(m) })
                Text(label)
            }
        }
        Button(onClick = { vm.logout() }, modifier = Modifier.padding(top = 24.dp)) { Text("Вийти") }
    }
}
