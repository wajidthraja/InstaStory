package com.clouddesk.storymaker

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Bottom sheet: search free stock videos with words taken from the context, tap a clip to use it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineVideoSheet(vm: StoryViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(vm.suggestedVideoQuery()) }
    var results by remember { mutableStateOf<List<StockVideo>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runSearch(nextPage: Int) {
        if (query.isBlank() || loading) return
        loading = true; error = null
        scope.launch {
            runCatching { OnlineVideos.search(query, nextPage) }
                .onSuccess { found ->
                    results = if (nextPage == 1) found else results + found
                    page = nextPage
                    if (found.isEmpty() && nextPage == 1) error = "No videos for \"$query\". Try simpler words, e.g. \"rain\" or \"books\"."
                }
                .onFailure { error = it.message ?: "Couldn't search. Check your internet connection." }
            loading = false
        }
    }

    LaunchedEffect(Unit) { if (OnlineVideos.hasKey) runSearch(1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1C1C1C),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).fillMaxHeight(0.92f)) {
            Text("Find a video online", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Free stock clips from ${OnlineVideos.sourceName}, matched to your context", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))

            if (!OnlineVideos.hasKey) {
                Text(
                    "Online search needs a free Pixabay API key. Sign up at pixabay.com, open pixabay.com/api/docs to see " +
                        "your key, add it to your GitHub repo as a secret named PIXABAY_API_KEY, and run the build again.",
                    color = Color(0xFFFFB38A), fontSize = 14.sp,
                )
                return@Column
            }

            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("e.g. studying at night, rain, beach") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch(1) }),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            error?.let { Text(it, color = Color(0xFFFFB38A), fontSize = 13.sp) }
            if (loading && results.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(results, key = { it.id }) { video ->
                    VideoTile(video) { vm.useOnlineVideo(video); onDismiss() }
                }
                if (results.isNotEmpty()) item(span = { GridItemSpan(3) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { runSearch(page + 1) }, enabled = !loading) {
                            Text(if (loading) "Loading…" else "Show more")
                        }
                        // Pixabay and Pexels API terms: show where the results come from, with a link.
                        Text(
                            "Videos provided by ${OnlineVideos.sourceName}",
                            color = Color(0xFF9EC1FF), fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OnlineVideos.sourceUrl))) }
                                .padding(8.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoTile(video: StockVideo, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, video.thumbUrl) { value = OnlineVideos.thumbnail(video.thumbUrl) }
    Box(
        Modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onClick),
    ) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Text(
            "%d:%02d".format(video.durationSec / 60, video.durationSec % 60),
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
