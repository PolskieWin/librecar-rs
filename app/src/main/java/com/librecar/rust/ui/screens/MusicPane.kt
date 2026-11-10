package com.librecar.rust.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librecar.rust.CarUiState
import com.librecar.rust.MusicSourceTab
import com.librecar.rust.data.LocalPlayerState
import com.librecar.rust.data.SpotifyTrack
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.EcoGreen
import com.librecar.rust.ui.theme.ElevatedBlack
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MusicPane(
    ui: CarUiState,
    localState: LocalPlayerState,
    compactCard: Boolean,
    hasAudioPerm: Boolean,
    onSelectMusicTab: (MusicSourceTab) -> Unit,
    onToggleLocalPlay: () -> Unit,
    onLocalNext: () -> Unit,
    onLocalPrev: () -> Unit,
    onLocalSeek: (Long) -> Unit,
    onLocalSelectIndex: (Int) -> Unit,
    onRequestAudioPerm: () -> Unit,
    onScanLocal: () -> Unit,
    onConnectSpotify: () -> Unit,
    onSearchSpotify: (String) -> Unit,
    onPlaySpotifyTrack: (SpotifyTrack) -> Unit,
    onSpotifyToggle: () -> Unit,
    onSpotifyNext: () -> Unit,
    onSpotifyPrev: () -> Unit,
    onOpenLayoutSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalCarStrings.current
    val isSpotify = ui.musicTab == MusicSourceTab.SPOTIFY
    val isPlaying = if (isSpotify) ui.spotifyState.isPlaying else localState.isPlaying
    val title = if (isSpotify) {
        ui.spotifyState.track?.title ?: localState.currentTitle.ifBlank { "Spotify API" }
    } else {
        localState.currentTitle.ifBlank { if (localState.tracks.isEmpty()) s.noLocalSongs else localState.tracks.first().title }
    }
    val artist = if (isSpotify) {
        ui.spotifyState.track?.artist ?: ui.spotifyState.deviceName.ifBlank { "Connect / Search" }
    } else {
        localState.currentArtist.ifBlank { s.appTitle }
    }
    val progressMs = if (isSpotify) ui.spotifyState.progressMs else localState.positionMs
    val durationMs = (if (isSpotify) ui.spotifyState.durationMs else localState.durationMs).coerceAtLeast(1L)

    var visBars by remember { mutableStateOf(FloatArray(16) { 0.1f }) }
    LaunchedEffect(isPlaying, ui.lowEndMode) {
        while (isActive) {
            if (!ui.lowEndMode || isPlaying) {
                visBars = RustEngine.safeStepVisualizer(isPlaying, System.currentTimeMillis(), 0.06f)
            }
            delay(if (ui.lowEndMode) 120L else 60L)
        }
    }

    var searchBox by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardBlack)
            .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Source Switcher: Built-in Player vs Spotify API
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedBlack)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SourceChip(
                    label = s.musicBuiltIn,
                    selected = !isSpotify,
                    onClick = { onSelectMusicTab(MusicSourceTab.BUILT_IN) },
                )
                SourceChip(
                    label = s.musicSpotify,
                    selected = isSpotify,
                    accent = Color(0xFF1DB954),
                    onClick = { onSelectMusicTab(MusicSourceTab.SPOTIFY) },
                )
            }

            if (isSpotify && ui.spotifyConnected) {
                Text(
                    text = "● API",
                    color = Color(0xFF1DB954),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Now Playing Header + Rust Visualizer / Album Art
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (compactCard) 54.dp else 68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedBlack)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (isSpotify && ui.spotifyArt != null) {
                    Image(
                        bitmap = ui.spotifyArt.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val accentCol = if (isSpotify) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        val barW = size.width / 16f
                        for (i in 0 until 16) {
                            val h = (visBars.getOrElse(i) { 0.1f }) * size.height
                            drawRoundRect(
                                color = accentCol,
                                topLeft = Offset(i * barW + 1f, size.height - h),
                                size = Size((barW - 2f).coerceAtLeast(2f), h),
                                cornerRadius = CornerRadius(3f, 3f),
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compactCard) {
                    Text(
                        text = "${formatMs(progressMs)} / ${formatMs(durationMs)}",
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp,
                    )
                }
            }
        }

        // Progress Scrub Slider (Built-in)
        if (!compactCard) {
            Slider(
                value = (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                onValueChange = { frac ->
                    if (!isSpotify) onLocalSeek((frac * durationMs).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = if (isSpotify) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary,
                    activeTrackColor = if (isSpotify) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = HairlineBorder,
                ),
                modifier = Modifier.fillMaxWidth().height(24.dp),
            )
        }

        // Giant CarPlay Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { if (isSpotify) onSpotifyPrev() else onLocalPrev() },
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ElevatedBlack),
            ) {
                Icon(CarIcons.SkipPrev, contentDescription = "Previous", tint = Color.White)
            }

            IconButton(
                onClick = { if (isSpotify) onSpotifyToggle() else onToggleLocalPlay() },
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isSpotify) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = if (isPlaying) CarIcons.Pause else CarIcons.Play,
                    contentDescription = "Play/Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp),
                )
            }

            IconButton(
                onClick = { if (isSpotify) onSpotifyNext() else onLocalNext() },
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ElevatedBlack),
            ) {
                Icon(CarIcons.SkipNext, contentDescription = "Next", tint = Color.White)
            }
        }

        // Expanded Track List / Spotify API Search & Auth (shown in full tab or tall split)
        if (!compactCard) {
            if (isSpotify) {
                if (!ui.spotifyConnected) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ElevatedBlack)
                            .border(1.dp, Color(0xFF1DB954).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(s.spotifySetupHint, color = Color(0xFFD1D5DB), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionBtn(
                                text = if (ui.spotifyClientId.isBlank()) s.spotifyClientIdLabel else s.connectSpotify,
                                color = Color(0xFF1DB954),
                                onClick = {
                                    if (ui.spotifyClientId.isBlank()) onOpenLayoutSettings() else onConnectSpotify()
                                },
                            )
                        }
                    }
                } else {
                    // Spotify Search Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElevatedBlack)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(CarIcons.Search, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchBox.isEmpty()) {
                                Text(s.searchTracksHint, color = Color(0xFF6B7280), fontSize = 12.sp)
                            }
                            BasicTextField(
                                value = searchBox,
                                onValueChange = { searchBox = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(Color(0xFF1DB954)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSearchSpotify(searchBox) }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        ActionBtn(text = s.go, color = Color(0xFF1DB954), onClick = { onSearchSpotify(searchBox) })
                    }

                    ui.spotifyStatusMsg?.let { msg ->
                        Text(msg, color = EcoGreen, fontSize = 11.sp)
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(ui.spotifyTracks) { _, t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ElevatedBlack)
                                    .clickable { onPlaySpotifyTrack(t) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(CarIcons.Music, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t.artist, color = Color(0xFF9CA3AF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(formatMs(t.durationMs), color = Color(0xFF6B7280), fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                // Built-in Local Music List
                if (!hasAudioPerm) {
                    ActionBtn(
                        text = s.grantAudioPerm,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = onRequestAudioPerm,
                    )
                } else if (localState.tracks.isEmpty()) {
                    ActionBtn(
                        text = s.scanAudio,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = onScanLocal,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(localState.tracks) { idx, t ->
                            val active = idx == localState.currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else ElevatedBlack)
                                    .clickable { onLocalSelectIndex(idx) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    CarIcons.Music,
                                    contentDescription = null,
                                    tint = if (active) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t.artist, color = Color(0xFF9CA3AF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(formatMs(t.durationMs), color = Color(0xFF6B7280), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(
    label: String,
    selected: Boolean,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) Color.Black else Color(0xFFD1D5DB),
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun ActionBtn(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.Black,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(m, sec)
}
