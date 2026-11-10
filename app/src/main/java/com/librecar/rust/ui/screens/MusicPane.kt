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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.librecar.rust.data.RadioStation
import com.librecar.rust.data.SpotifyTrack
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.ui.theme.AlertRed
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.EcoAmber
import com.librecar.rust.ui.theme.EcoGreen
import com.librecar.rust.ui.theme.ElevatedBlack
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
import com.librecar.rust.ui.theme.MutedText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MusicPane(
    ui: CarUiState,
    localState: LocalPlayerState,
    compactCard: Boolean,
    hasAudioPerm: Boolean,
    onSelectMusicTab: (MusicSourceTab) -> Unit,
    onMediaToggle: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrev: () -> Unit,
    onLocalSeek: (Long) -> Unit,
    onLocalSelectIndex: (Int) -> Unit,
    onPlayRadioStation: (RadioStation) -> Unit,
    onAddCustomRadio: (String, String) -> Unit,
    onRequestAudioPerm: () -> Unit,
    onScanLocal: () -> Unit,
    onConnectSpotify: () -> Unit,
    onSearchSpotify: (String) -> Unit,
    onPlaySpotifyTrack: (SpotifyTrack) -> Unit,
    onOpenLayoutSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalCarStrings.current
    val isSpotify = ui.musicTab == MusicSourceTab.SPOTIFY
    val isRadio = ui.musicTab == MusicSourceTab.RADIO
    val isPlaying = if (isSpotify) ui.spotifyState.isPlaying else localState.isPlaying
    val isPreparing = !isSpotify && localState.isPreparing

    val title = when {
        isSpotify -> ui.spotifyState.track?.title ?: localState.currentTitle.ifBlank { "Spotify" }
        isRadio -> localState.currentTitle.ifBlank { localState.radioStations.firstOrNull()?.name ?: "Radio" }
        else -> localState.currentTitle.ifBlank { localState.tracks.firstOrNull()?.title ?: s.noLocalSongs }
    }
    val artist = when {
        isSpotify -> ui.spotifyState.track?.artist ?: ui.spotifyState.deviceName.ifBlank { s.connectSpotify }
        isRadio -> when {
            localState.lastError != null -> localState.lastError
            isPreparing -> "Buffering stream..."
            localState.isLiveRadio -> localState.currentArtist
            else -> localState.radioStations.firstOrNull()?.genre ?: "Internet Radio"
        }
        else -> localState.currentArtist.ifBlank { s.appTitle }
    }
    val progressMs = if (isSpotify) ui.spotifyState.progressMs else localState.positionMs
    val durationMs = (if (isSpotify) ui.spotifyState.durationMs else localState.durationMs).coerceAtLeast(1L)

    val visBars = remember { FloatArray(16) { 0.1f } }
    var visTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(isPlaying, ui.lowEndMode) {
        while (isActive) {
            if (isPlaying || visTick == 0) {
                RustEngine.safeStepVisualizerInto(isPlaying, System.currentTimeMillis(), 0.06f, visBars)
                visTick++
            }
            delay(if (ui.lowEndMode) 120L else 60L)
        }
    }

    var searchBox by remember { mutableStateOf("") }
    var radioNameBox by remember { mutableStateOf("") }
    var radioUrlBox by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardBlack)
            .border(0.75.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .padding(if (compactCard) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactCard) 6.dp else 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElevatedBlack)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                SourceChip(label = s.musicBuiltIn, selected = ui.musicTab == MusicSourceTab.BUILT_IN) {
                    onSelectMusicTab(MusicSourceTab.BUILT_IN)
                }
                SourceChip(label = s.musicRadio, selected = isRadio) {
                    onSelectMusicTab(MusicSourceTab.RADIO)
                }
                SourceChip(label = s.musicSpotify, selected = isSpotify) {
                    onSelectMusicTab(MusicSourceTab.SPOTIFY)
                }
            }

            when {
                isRadio && localState.isLiveRadio && localState.isPlaying -> {
                    Text(if (isPreparing) "SYNC" else "LIVE", color = EcoGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                isSpotify && ui.spotifyConnected -> {
                    Text("API", color = EcoGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (compactCard) 38.dp else 60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElevatedBlack)
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(10.dp)),
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
                    val accentCol = when {
                        isSpotify -> Color(0xFF1DB954)
                        isRadio -> EcoGreen
                        else -> Color.White
                    }
                    Canvas(modifier = Modifier.fillMaxSize().padding(if (compactCard) 5.dp else 8.dp)) {
                        val unused = visTick
                        val barW = size.width / 16f
                        for (i in 0 until 16) {
                            val h = visBars[i] * size.height
                            drawRoundRect(
                                color = accentCol,
                                topLeft = Offset(i * barW + 0.5f, size.height - h),
                                size = Size((barW - 1.5f).coerceAtLeast(1.5f), h),
                                cornerRadius = CornerRadius(2f, 2f),
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (compactCard) 13.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    artist,
                    color = if (localState.lastError != null && isRadio) EcoAmber else MutedText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compactCard) {
                    Text(
                        text = if (isRadio && localState.isLiveRadio) "LIVE STREAM · ${formatMs(progressMs)}" else "${formatMs(progressMs)} / ${formatMs(durationMs)}",
                        color = MutedText,
                        fontSize = 11.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }

            if (compactCard) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMediaPrev,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(ElevatedBlack),
                    ) {
                        Icon(CarIcons.SkipPrev, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onMediaToggle,
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) CarIcons.Pause else CarIcons.Play,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onMediaNext,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(ElevatedBlack),
                    ) {
                        Icon(CarIcons.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (!compactCard) {
            if (!(isRadio && localState.isLiveRadio)) {
                Slider(
                    value = (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { frac -> if (!isSpotify) onLocalSeek((frac * durationMs).toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = HairlineBorder,
                    ),
                    modifier = Modifier.fillMaxWidth().height(22.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onMediaPrev,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(ElevatedBlack),
                ) {
                    Icon(CarIcons.SkipPrev, contentDescription = "Previous", tint = Color.White)
                }

                IconButton(
                    onClick = onMediaToggle,
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White),
                ) {
                    Icon(
                        imageVector = if (isPlaying) CarIcons.Pause else CarIcons.Play,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp),
                    )
                }

                IconButton(
                    onClick = onMediaNext,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(ElevatedBlack),
                ) {
                    Icon(CarIcons.SkipNext, contentDescription = "Next", tint = Color.White)
                }
            }

            when {
                isRadio -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .weight(0.36f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElevatedBlack)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (radioNameBox.isEmpty()) Text("Station", color = MutedText, fontSize = 11.sp)
                                BasicTextField(
                                    value = radioNameBox,
                                    onValueChange = { radioNameBox = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .weight(0.64f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElevatedBlack)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (radioUrlBox.isEmpty()) Text("https://... mp3 stream", color = MutedText, fontSize = 11.sp)
                                BasicTextField(
                                    value = radioUrlBox,
                                    onValueChange = { radioUrlBox = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        ActionBtn(text = "Tune") {
                            onAddCustomRadio(radioNameBox, radioUrlBox)
                            radioNameBox = ""
                            radioUrlBox = ""
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        itemsIndexed(localState.radioStations) { _, st ->
                            val active = st.id == localState.currentRadioId && localState.isLiveRadio
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) Color(0xFF1D2820) else ElevatedBlack)
                                    .border(0.75.dp, if (active) EcoGreen else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable { onPlayRadioStation(st) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(CarIcons.Radio, contentDescription = null, tint = if (active) EcoGreen else MutedText, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(st.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${st.genre} · ${st.country}", color = MutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(if (active && isPlaying) "LIVE" else "Play", color = if (active) EcoGreen else MutedText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
                isSpotify -> {
                    if (!ui.spotifyConnected) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElevatedBlack)
                                .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(s.spotifySetupHint, color = MutedText, fontSize = 12.sp)
                            ActionBtn(
                                text = if (ui.spotifyClientId.isBlank()) s.spotifyClientIdLabel else s.connectSpotify,
                                onClick = { if (ui.spotifyClientId.isBlank()) onOpenLayoutSettings() else onConnectSpotify() },
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElevatedBlack)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(CarIcons.Search, contentDescription = null, tint = MutedText, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchBox.isEmpty()) Text(s.searchTracksHint, color = MutedText, fontSize = 12.sp)
                                BasicTextField(
                                    value = searchBox,
                                    onValueChange = { searchBox = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                    cursorBrush = SolidColor(Color.White),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { onSearchSpotify(searchBox) }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            ActionBtn(text = s.go, onClick = { onSearchSpotify(searchBox) })
                        }

                        ui.spotifyStatusMsg?.let { Text(it, color = MutedText, fontSize = 11.sp) }

                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                                    Icon(CarIcons.Music, contentDescription = null, tint = MutedText, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(t.title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(t.artist, color = MutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(formatMs(t.durationMs), color = MutedText, fontSize = 11.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (!hasAudioPerm) {
                        ActionBtn(text = s.grantAudioPerm, onClick = onRequestAudioPerm)
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            ActionBtn(text = s.scanAudio, onClick = onScanLocal)
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        itemsIndexed(localState.tracks) { idx, t ->
                            val active = !localState.isLiveRadio && idx == localState.currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) Color(0xFF23232A) else ElevatedBlack)
                                    .clickable { onLocalSelectIndex(idx) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(CarIcons.Music, contentDescription = null, tint = if (active) Color.White else MutedText, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t.artist, color = MutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(formatMs(t.durationMs), color = MutedText, fontSize = 11.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.Black else MutedText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ActionBtn(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.Black,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(m, sec)
}
