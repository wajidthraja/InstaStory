package com.clouddesk.storymaker

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: StoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFF7A45), secondary = Color(0xFF9B6BFF))) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF121212)) { StoryScreen(vm) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.stopPreview()
    }
}

@Composable
private fun StoryScreen(vm: StoryViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onVideoPicked(uri)
    }
    val speak = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val said = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) vm.contextText = (vm.contextText + " " + said).trim()
        }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color(0xFF121212)) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Story Maker", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)

            // 1. Video
            Step(1, "Choose your video") {
                OutlinedButton(onClick = {
                    pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }) {
                    Icon(Icons.Default.VideoLibrary, null); Spacer(Modifier.width(8.dp))
                    Text(if (vm.videoUri == null) "Pick from gallery" else "Change video")
                }
                vm.videoInfo?.takeIf { it.durationMs > 0 }?.let {
                    Text(
                        "Length: ${it.durationMs / 1000}s" + if (it.durationMs > StorySpec.MAX_DURATION_MS) " (first 60s used)" else "",
                        color = Color.Gray, fontSize = 13.sp,
                    )
                }
                Text("Use video you shot yourself, or royalty-free footage.", color = Color.Gray, fontSize = 12.sp)
            }

            // 2. Context
            Step(2, "Explain the context") {
                OutlinedTextField(
                    value = vm.contextText,
                    onValueChange = { vm.contextText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("What happened, how you felt, what the Story should say") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your Story")
                            runCatching { speak.launch(i) }.onFailure { vm.message = "Voice input isn't available on this phone." }
                        }) { Icon(Icons.Default.Mic, "Speak") }
                    },
                )
                Text("Tone", color = Color.Gray, fontSize = 13.sp)
                Chips(Tone.entries, vm.tone, { it.label }) { vm.selectTone(it) }
                Button(onClick = vm::generate, enabled = vm.canGenerate) {
                    Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Build my Story")
                }
            }

            // 3. Review & adjust
            if (vm.lines.isNotEmpty()) {
                Step(3, "Review and adjust") {
                    Preview(vm)
                    Text("Mood (detected from your words, tap to change)", color = Color.Gray, fontSize = 13.sp)
                    Chips(Mood.entries, vm.mood, { it.label }) { vm.selectMood(it) }
                    Text("Text lines (shown one after another)", color = Color.Gray, fontSize = 13.sp)
                    vm.lines.forEachIndexed { i, line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = line, onValueChange = { vm.updateLine(i, it) },
                                modifier = Modifier.weight(1f), singleLine = false,
                            )
                            IconButton(onClick = { vm.removeLine(i) }) { Icon(Icons.Default.Close, "Remove line") }
                        }
                    }
                    if (vm.clipDurationMs in 1 until vm.neededMs) Text(
                        "Your text needs about ${vm.neededMs / 1000}s to read, but the clip is ${vm.clipDurationMs / 1000}s. " +
                            "Use a longer clip or remove a line, otherwise lines will go by fast.",
                        color = Color(0xFFFFB38A), fontSize = 12.sp,
                    )
                    Row {
                        TextButton(onClick = vm::addLine) { Icon(Icons.Default.Add, null); Text("Add line") }
                        TextButton(onClick = vm::tryAnotherLine) { Icon(Icons.Default.Shuffle, null); Text("Try another line") }
                    }
                    Text("Tone", color = Color.Gray, fontSize = 13.sp)
                    Chips(Tone.entries, vm.tone, { it.label }) { vm.selectTone(it) }

                    Text("Style", color = Color.Gray, fontSize = 13.sp)
                    Chips(TextTemplate.entries, vm.template, { it.label }) { vm.template = it }

                    Text("Music", color = Color.Gray, fontSize = 13.sp)
                    MusicSection(vm)
                    if (vm.videoInfo?.hasAudio == true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = vm.keepOriginalSound, onCheckedChange = { vm.keepOriginalSound = it })
                            Spacer(Modifier.width(10.dp))
                            Text("Keep the video's own sound", color = Color.White)
                        }
                        if (vm.keepOriginalSound) Text(
                            "Only if it has no copyrighted music playing in the background.",
                            color = Color.Gray, fontSize = 12.sp,
                        )
                    }
                }

                // 4. Export
                Step(4, "Create your Story") {
                    if (vm.rendering) {
                        if (vm.progress >= 0) LinearProgressIndicator(progress = { vm.progress / 100f }, Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Creating video… ${if (vm.progress >= 0) "${vm.progress}%" else ""}", color = Color.Gray)
                        TextButton(onClick = vm::cancelRender) { Text("Cancel") }
                    } else {
                        Button(onClick = vm::render, enabled = vm.canRender, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Movie, null); Spacer(Modifier.width(8.dp)); Text("Create 9:16 video")
                        }
                    }
                    vm.result?.let { file ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { StoryExport.shareToInstagram(context, file) }) {
                                Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share to Story")
                            }
                            OutlinedButton(onClick = vm::saveToGallery) {
                                Icon(Icons.Default.Download, null); Spacer(Modifier.width(6.dp)); Text("Save")
                            }
                        }
                        if (vm.music == MusicChoice.InInstagram) {
                            Text(
                                "In Instagram: tap the sticker icon → Music → search your song → pick the part to play.",
                                color = Color(0xFFFFB38A), fontSize = 13.sp,
                            )
                        }
                        if (vm.caption.isNotBlank()) {
                            Text("Suggested caption", color = Color.Gray, fontSize = 13.sp)
                            Text(vm.caption, color = Color.White)
                            TextButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("caption", vm.caption))
                                vm.message = "Caption copied"
                            }) { Icon(Icons.Default.ContentCopy, null); Text("Copy caption") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Step(n: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("$n. $title", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(label(item)) })
        }
    }
}

/** Still preview: first frame, cropped 9:16, with the first text line in the chosen style. */
@Composable
private fun Preview(vm: StoryViewModel) {
    val frame = vm.videoInfo?.firstFrame
    val overlay = remember(vm.lines.firstOrNull(), vm.template) { vm.previewOverlay() }
    Box(
        Modifier
            .fillMaxWidth(0.55f)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .then(Modifier),
    ) {
        frame?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        overlay?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}
