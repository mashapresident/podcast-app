package com.podcastapp.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podcastapp.player.PlayerController
import com.podcastapp.ui.common.Cover
import com.podcastapp.ui.common.formatTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(val controller: PlayerController) : ViewModel()

@Composable
fun MiniPlayer(onOpen: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val s by vm.controller.state.collectAsStateWithLifecycle()
    val ep = s.episode ?: return
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(s.coverUrl, Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(ep.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(s.podcastTitle, maxLines = 1, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(vm.controller::togglePlayPause) {
                Icon(if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Пауза/Грати")
            }
        }
    }
}

@Composable
fun PlayerScreen(vm: PlayerViewModel = hiltViewModel()) {
    val s by vm.controller.state.collectAsStateWithLifecycle()
    val c = vm.controller
    val ep = s.episode
    if (ep == null) {
        Text("Оберіть епізод для прослуховування", modifier = Modifier.padding(24.dp))
        return
    }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = s.durationMs.coerceAtLeast(1)

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(s.coverUrl, Modifier.fillMaxWidth(0.8f).padding(vertical = 16.dp).size(260.dp))
        Text(ep.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(s.podcastTitle, style = MaterialTheme.typography.bodyMedium)

        Slider(
            value = dragging ?: (s.positionMs.toFloat() / duration),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { c.seekTo((it * duration).toLong()) }; dragging = null },
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(((dragging ?: (s.positionMs.toFloat() / duration)) * duration).toLong()))
            Text(formatTime(s.durationMs))
        }

        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(c::seekBack) { Text("−15 с") }
            FilledIconButton(c::togglePlayPause, Modifier.size(64.dp)) {
                Icon(if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Пауза/Грати", modifier = Modifier.size(36.dp))
            }
            TextButton(c::seekForward) { Text("+30 с") }
        }

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            var speedMenu by remember { mutableStateOf(false) }
            TextButton({ speedMenu = true }) { Text("Швидкість ${s.speed}×") }
            DropdownMenu(speedMenu, { speedMenu = false }) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                    DropdownMenuItem(text = { Text("$v×") }, onClick = { c.setSpeed(v); speedMenu = false })
                }
            }
            var sleepMenu by remember { mutableStateOf(false) }
            TextButton({ sleepMenu = true }) { Text(s.sleepMinutes?.let { "Сон: $it хв" } ?: "Таймер сну") }
            DropdownMenu(sleepMenu, { sleepMenu = false }) {
                listOf(null, 15, 30, 60).forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m?.let { "$it хвилин" } ?: "Вимкнути") },
                        onClick = { c.setSleepTimer(m); sleepMenu = false },
                    )
                }
            }
        }
    }
}
