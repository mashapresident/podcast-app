package com.podcastapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.podcastapp.data.local.TokenStore
import com.podcastapp.ui.auth.AuthScreen
import com.podcastapp.ui.home.HomeScreen
import com.podcastapp.ui.library.LibraryScreen
import com.podcastapp.ui.player.MiniPlayer
import com.podcastapp.ui.player.PlayerScreen
import com.podcastapp.ui.player.PlayerViewModel
import com.podcastapp.ui.podcast.PodcastScreen
import com.podcastapp.ui.profile.ProfileScreen
import com.podcastapp.ui.search.SearchScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(store: TokenStore) : ViewModel() {
    val isLoggedIn = store.isLoggedIn
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Головна", Icons.Filled.Home),
    Tab("search?category={category}", "Пошук", Icons.Filled.Search),
    Tab("library", "Бібліотека", Icons.Filled.LibraryMusic),
    Tab("profile", "Профіль", Icons.Filled.Person),
)

@Composable
fun AppRoot(vm: RootViewModel = hiltViewModel()) {
    val loggedIn by vm.isLoggedIn.collectAsStateWithLifecycle<Boolean?>(null)
    when (loggedIn) {
        null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        // Після входу/виходу прапорець змінюється сам (DataStore), тому onSuccess порожній
        false -> AuthScreen(onSuccess = {})
        true -> MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val playerVm: PlayerViewModel = hiltViewModel()
    val player by playerVm.controller.state.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (route != "player") {
                Column {
                    if (player.episode != null) MiniPlayer(onOpen = { nav.navigate("player") })
                    NavigationBar {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.route,
                                onClick = {
                                    nav.navigate(t.route.substringBefore("?").let { if (it == "search") "search?category=-1" else it }) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    onPodcast = { nav.navigate("podcast/$it") },
                    onCategory = { nav.navigate("search?category=$it") },
                    onEpisodePodcast = { nav.navigate("podcast/$it") },
                )
            }
            composable(
                "search?category={category}",
                arguments = listOf(navArgument("category") { type = NavType.IntType; defaultValue = -1 }),
            ) { SearchScreen(onPodcast = { nav.navigate("podcast/$it") }) }
            composable("library") { LibraryScreen(onPodcast = { nav.navigate("podcast/$it") }) }
            composable("profile") { ProfileScreen() }
            composable("podcast/{id}") { PodcastScreen() }
            composable("player") { PlayerScreen() }
        }
    }
}
