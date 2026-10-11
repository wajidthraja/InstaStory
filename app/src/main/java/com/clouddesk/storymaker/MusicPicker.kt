package com.clouddesk.storymaker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The music row on the main screen: current song, change button, and the "which part plays" slider. */
@Composable
fun MusicSection(vm: StoryViewModel) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2A2A2A))
            .clickable { showPicker = true }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(iconFor(vm.music))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(vm.music.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitleFor(vm.music), color = Color.Gray, fontSize = 12.sp, maxLines = 2)
        }
        if (vm.musicUri(vm.music) != null) {
            PlayButton(playing = vm.previewing == vm.music) { vm.togglePreview(vm.music) }
        }
        Icon(Icons.Default.ChevronRight, "Change music", tint = Color.Gray)
    }

    // Like Instagram: drag to choose which 15 s of the song plays.
    if (vm.musicUri(vm.music) != null && vm.maxMusicStartMs() > 0) {
        val max = vm.maxMusicStartMs()
        Text("Choose the part that plays", color = Color.Gray, fontSize = 13.sp)
        Slider(
            value = vm.musicStartMs.toFloat(),
            onValueChange = { vm.setMusicStart(it.toLong()) },
            onValueChangeFinished = {
                if (vm.previewing == vm.music) { vm.togglePreview(vm.music); vm.togglePreview(vm.music) }
            },
            valueRange = 0f..max.toFloat(),
        )
        Text(
            "Starts at ${mmss(vm.musicStartMs)} · plays ${mmss(vm.musicStartMs)}–${mmss(vm.musicStartMs + MUSIC_PREVIEW_MS)} in preview",
            color = Color.Gray, fontSize = 12.sp,
        )
    }

    if (showPicker) {
        MusicPickerSheet(vm, onDismiss = { showPicker = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicPickerSheet(vm: StoryViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.onDeviceAudioPicked(uri); onDismiss() }
    }

    fun choose(choice: MusicChoice) { vm.stopPreview(); vm.selectMusic(choice); onDismiss() }

    ModalBottomSheet(
        onDismissRequest = { vm.stopPreview(); onDismiss() },
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1C),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).fillMaxHeight(0.9f)) {
            Text("Music", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search music") },
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(12.dp))

            val q = query.trim().lowercase()
            val tracks = MusicTrack.entries.filter {
                q.isEmpty() || it.label.lowercase().contains(q) || it.mood.lowercase().contains(q)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Popular songs: only Instagram can legally add these.
                item {
                    SongRow(
                        icon = Icons.Default.LibraryMusic,
                        title = if (q.isEmpty()) "Popular & film songs" else "Find \"$query\" in Instagram",
                        subtitle = "Add with Instagram's Music sticker after sharing. Licensed by Instagram, so it won't be muted.",
                        selected = vm.music == MusicChoice.InInstagram,
                        onClick = { choose(MusicChoice.InInstagram) },
                    )
                }

                item { SectionLabel("Copyright-free · made for this app") }
                items(tracks) { track ->
                    val choice = MusicChoice.Library(track)
                    SongRow(
                        icon = Icons.Default.MusicNote,
                        title = track.label,
                        subtitle = track.mood,
                        selected = vm.music == choice,
                        playing = vm.previewing == choice,
                        onPlay = { vm.togglePreview(choice) },
                        onClick = { choose(choice) },
                    )
                }
                if (tracks.isEmpty()) item {
                    Text("No matching tracks.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                }

                item { SectionLabel("More") }
                item {
                    SongRow(
                        icon = Icons.Default.FolderOpen,
                        title = "From my phone",
                        subtitle = "Only royalty-free tracks you have the right to use (e.g. YouTube Audio Library, Pixabay).",
                        selected = vm.music is MusicChoice.Device,
                        onClick = { pickAudio.launch(arrayOf("audio/*")) },
                    )
                }
                item {
                    SongRow(
                        icon = Icons.Default.MusicOff,
                        title = "No music",
                        subtitle = "Keep it silent, or use only the video's own sound.",
                        selected = vm.music == MusicChoice.None,
                        onClick = { choose(MusicChoice.None) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SongRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    playing: Boolean = false,
    onPlay: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF33261F) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        if (onPlay != null) PlayButton(playing, onPlay)
        if (selected) Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AlbumArt(icon: ImageVector) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFF7A45), Color(0xFF7B3FE4)))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White) }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF3A3A3A)), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, if (playing) "Stop" else "Play", tint = Color.White)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
}

private fun iconFor(choice: MusicChoice): ImageVector = when (choice) {
    MusicChoice.None -> Icons.Default.MusicOff
    MusicChoice.InInstagram -> Icons.Default.LibraryMusic
    is MusicChoice.Device -> Icons.Default.FolderOpen
    is MusicChoice.Library -> Icons.Default.MusicNote
}

private fun subtitleFor(choice: MusicChoice): String = when (choice) {
    MusicChoice.None -> "Tap to add music"
    MusicChoice.InInstagram -> "Video is made without music; add the song in Instagram"
    is MusicChoice.Device -> "From your phone · make sure it's royalty-free"
    is MusicChoice.Library -> "Copyright-free · ${choice.track.mood}"
}

private fun mmss(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
