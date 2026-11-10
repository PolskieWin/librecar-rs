package com.librecar.rust.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librecar.rust.CarUiState
import com.librecar.rust.data.AssistantVoicePreset
import com.librecar.rust.data.CarModelPreset
import com.librecar.rust.data.LayoutMode
import com.librecar.rust.data.TaskbarPosition
import com.librecar.rust.data.TouchScale
import com.librecar.rust.data.VoiceUiState
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.nativeengine.TelemetrySnapshot
import com.librecar.rust.ui.theme.AccentPreset
import com.librecar.rust.ui.theme.AppLang
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.EcoAmber
import com.librecar.rust.ui.theme.EcoGreen
import com.librecar.rust.ui.theme.ElevatedBlack
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
import com.librecar.rust.ui.theme.MutedText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OverlayShell(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onClose)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBD000000))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        val maxModalH = maxHeight * 0.90f
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .heightIn(max = maxModalH)
                .clip(RoundedCornerShape(16.dp))
                .background(CardBlack)
                .border(0.75.dp, HairlineBorder, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp).clip(CircleShape).background(ElevatedBlack)) {
                    Icon(CarIcons.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun WeatherDrawer(
    ui: CarUiState,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalCarStrings.current
    val w = ui.weather
    OverlayShell(title = s.weatherTitle, onClose = onClose) {
        if (w == null) {
            Text(
                text = if (s.isPolish) "Pobieranie pogody z Open-Meteo..." else "Fetching Open-Meteo weather...",
                color = MutedText,
                fontSize = 13.sp,
            )
            ChipBtn(text = "Refresh", selected = true, onClick = onRefresh)
        } else {
            val (condLabel, roadTip) = describeWeather(w.weatherCode, w.tempC, w.windKmh, s.isPolish)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Column {
                        Text("${w.tempC.roundToInt()}°C · $condLabel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${s.feelsLike} ${w.apparentTempC.roundToInt()}°C · ${s.windLabel} ${w.windKmh.roundToInt()} km/h · ${s.rainLabel} ${w.precipProb}%", color = MutedText, fontSize = 11.sp)
                    }
                }
                ChipBtn(text = "Refresh", selected = false, onClick = onRefresh)
            }

            Text(
                text = roadTip,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElevatedBlack)
                    .padding(9.dp),
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                w.hourly.forEach { h ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElevatedBlack)
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    ) {
                        Text(h.timeLabel, color = MutedText, fontSize = 10.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                        Text("${h.tempC.roundToInt()}°", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                        Text("${h.rainProb}%", color = MutedText, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDrawer(
    ui: CarUiState,
    hasCalendarPerm: Boolean,
    onRequestCalendarPerm: () -> Unit,
    onAddReminder: (String, String, String) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onDriveToLocation: (String) -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalCarStrings.current
    var titleInput by remember { mutableStateOf("") }
    var locInput by remember { mutableStateOf("") }
    var timeInput by remember { mutableStateOf("18:00") }
    val dateHeader = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) }

    OverlayShell(title = s.calendarTitle, onClose = onClose) {
        Text(dateHeader, color = MutedText, fontWeight = FontWeight.Medium, fontSize = 12.sp)

        if (!hasCalendarPerm) {
            ChipBtn(text = s.grantCalendarPerm, selected = true, onClick = onRequestCalendarPerm)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactField(value = titleInput, hint = s.eventTitleHint, onValue = { titleInput = it }, modifier = Modifier.weight(1f))
            CompactField(value = locInput, hint = s.eventLocHint, onValue = { locInput = it }, modifier = Modifier.weight(1f))
            CompactField(value = timeInput, hint = "18:00", onValue = { timeInput = it }, modifier = Modifier.width(62.dp))
            ChipBtn(
                text = "+",
                selected = true,
                onClick = {
                    if (titleInput.isNotBlank()) {
                        onAddReminder(titleInput, locInput, timeInput)
                        titleInput = ""
                        locInput = ""
                    }
                },
            )
        }

        if (ui.calendarEvents.isEmpty()) {
            Text(s.noEvents, color = MutedText, fontSize = 12.sp)
        } else {
            LazyColumn(
                modifier = Modifier.height(150.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(ui.calendarEvents) { ev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(ElevatedBlack)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${ev.timeLabel} · ${ev.title}", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            if (ev.location.isNotBlank()) {
                                Text(ev.location, color = MutedText, fontSize = 10.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (ev.location.isNotBlank()) {
                                ChipBtn(text = s.driveToEvent, selected = true, onClick = { onDriveToLocation(ev.location) })
                            }
                            if (ev.isLocalReminder) {
                                ChipBtn(text = "✕", selected = false, onClick = { onDeleteReminder(ev.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LayoutSettingsDrawer(
    ui: CarUiState,
    telemetry: TelemetrySnapshot,
    onSetLayoutMode: (LayoutMode) -> Unit,
    onSetTaskbarPos: (TaskbarPosition) -> Unit,
    onSetSplitRatio: (Float) -> Unit,
    onSetMapHudScale: (Float) -> Unit,
    onSetTouchScale: (TouchScale) -> Unit,
    onSetLowEndMode: (Boolean) -> Unit,
    onSetTerrain3d: (Boolean) -> Unit,
    onSetHighRefresh120Hz: (Boolean) -> Unit,
    onSelectCarPreset: (CarModelPreset) -> Unit,
    onSetCustomCarName: (String) -> Unit,
    onSetTankCapacity: (Double) -> Unit,
    onSetAccent: (AccentPreset) -> Unit,
    onSetAppLang: (AppLang) -> Unit,
    onSetVoiceLang: (AppLang) -> Unit,
    onSetAssistantVoice: (AssistantVoicePreset) -> Unit,
    onSaveGeminiApiKey: (String) -> Unit,
    onAdjustFuelDelta: (Double) -> Unit,
    onFillTank: () -> Unit,
    onSaveSpotifyId: (String) -> Unit,
    onConnectSpotify: () -> Unit,
    onDisconnectSpotify: () -> Unit,
    onResetTripA: () -> Unit,
    onResetTripB: () -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalCarStrings.current
    val uriHandler = LocalUriHandler.current
    var spotifyIdBox by remember(ui.spotifyClientId) { mutableStateOf(ui.spotifyClientId) }
    var geminiKeyBox by remember(ui.geminiApiKey) { mutableStateOf(ui.geminiApiKey) }
    var customCarBox by remember(ui.carModelName) { mutableStateOf(ui.carModelName) }

    OverlayShell(title = s.layoutSettingsTitle, onClose = onClose) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ElevatedBlack)
                .padding(9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (RustEngine.isNativeLoaded) s.rustStatusReady else s.rustStatusFallback,
                    color = if (RustEngine.isNativeLoaded) EcoGreen else EcoAmber,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                Text("${ui.carModelName} · ${ui.vehicleMassKg.roundToInt()} kg · ${telemetry.tankCapacityLiters.roundToInt()} L", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                Text(s.privacyBadge, color = MutedText, fontSize = 10.sp)
                Text(
                    text = "Telemetry: ${telemetry.speedKmh.roundToInt()} km/h · Avg ${telemetry.avgSpeedKmh.roundToInt()} · Max ${telemetry.maxSpeedKmh.roundToInt()} · Alt ${telemetry.altitudeM.roundToInt()}m · ${telemetry.avgLPer100Km} L/100km",
                    color = MutedText,
                    fontSize = 10.sp,
                )
            }
        }

        // GitHub repo & creator attribution ("Made by Polskie")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ElevatedBlack)
                .border(0.75.dp, HairlineBorder, RoundedCornerShape(10.dp))
                .clickable { runCatching { uriHandler.openUri("https://github.com/PolskieWin/librecar-rs") } }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.madeByPolskieLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Text("MIT", color = EcoGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }

        SettingGroupTitle(s.carModelLabel)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CarModelPreset.entries.forEach { preset ->
                ChipBtn(
                    text = preset.displayName,
                    selected = ui.carModelId == preset.id,
                    onClick = { onSelectCarPreset(preset) },
                )
            }
        }
        if (ui.carModelId == CarModelPreset.CUSTOM.id) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactField(value = customCarBox, hint = "Custom Car Model", onValue = { customCarBox = it }, modifier = Modifier.weight(1f))
                ChipBtn(text = "Save", selected = true, onClick = { onSetCustomCarName(customCarBox) })
            }
        }

        SettingGroupTitle("${s.fuelLevelLabel}: ${telemetry.fuelLiters} / ${telemetry.tankCapacityLiters} L (${s.fuelRangeLabel} ~${telemetry.estimatedRangeKm.roundToInt()} km)")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipBtn(text = "-5 L", selected = false, onClick = { onAdjustFuelDelta(-5.0) })
            ChipBtn(text = "+5 L", selected = false, onClick = { onAdjustFuelDelta(5.0) })
            ChipBtn(text = s.fillTankLabel, selected = true, onClick = onFillTank)
        }

        SettingGroupTitle(s.tankCapacityLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(40.0, 45.0, 50.0, 60.0, 70.0).forEach { cap ->
                ChipBtn(
                    text = "${cap.roundToInt()} L",
                    selected = kotlin.math.abs(telemetry.tankCapacityLiters - cap) < 1.5,
                    onClick = { onSetTankCapacity(cap) },
                )
            }
        }

        SettingGroupTitle(s.layoutModeLabel)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LayoutMode.entries.forEach { mode ->
                ChipBtn(
                    text = if (s.isPolish) mode.labelPl else mode.labelEn,
                    selected = ui.layoutMode == mode,
                    onClick = { onSetLayoutMode(mode) },
                )
            }
        }

        SettingGroupTitle(s.taskbarPosLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TaskbarPosition.entries.forEach { pos ->
                ChipBtn(
                    text = if (s.isPolish) pos.labelPl else pos.labelEn,
                    selected = ui.taskbarPosition == pos,
                    onClick = { onSetTaskbarPos(pos) },
                )
            }
        }

        SettingGroupTitle("${s.splitRatioLabel}: ${(ui.splitRatio * 100).roundToInt()}%")
        Slider(
            value = ui.splitRatio,
            onValueChange = onSetSplitRatio,
            valueRange = 0.45f..0.75f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
        )

        SettingGroupTitle("${s.mapHudScaleLabel}: ${(ui.mapHudScale * 100).roundToInt()}%")
        Slider(
            value = ui.mapHudScale,
            onValueChange = onSetMapHudScale,
            valueRange = 0.50f..1.15f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipBtn(text = "55% Mini", selected = kotlin.math.abs(ui.mapHudScale - 0.55f) < 0.05f, onClick = { onSetMapHudScale(0.55f) })
            ChipBtn(text = "75% Compact", selected = kotlin.math.abs(ui.mapHudScale - 0.75f) < 0.05f, onClick = { onSetMapHudScale(0.75f) })
            ChipBtn(text = "100% Full", selected = kotlin.math.abs(ui.mapHudScale - 1.0f) < 0.05f, onClick = { onSetMapHudScale(1.0f) })
        }

        SettingGroupTitle(s.touchScaleLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TouchScale.entries.forEach { sc ->
                ChipBtn(
                    text = if (s.isPolish) sc.labelPl else sc.labelEn,
                    selected = ui.touchScale == sc,
                    onClick = { onSetTouchScale(sc) },
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(s.lowEndModeLabel, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(s.lowEndModeDesc, color = MutedText, fontSize = 11.sp)
            }
            Switch(checked = ui.lowEndMode, onCheckedChange = onSetLowEndMode)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(s.refresh120HzLabel, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text("Display hardware rate: ${ui.detectedDisplayHz} Hz · 120Hz target", color = MutedText, fontSize = 11.sp)
            }
            Switch(checked = ui.highRefresh120Hz, onCheckedChange = onSetHighRefresh120Hz)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.terrain3dLabel, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Switch(checked = ui.terrain3dEnabled, onCheckedChange = onSetTerrain3d)
        }

        SettingGroupTitle(s.accentThemeLabel)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AccentPreset.entries.forEach { preset ->
                ChipBtn(
                    text = if (s.isPolish) preset.labelPl else preset.labelEn,
                    selected = ui.accentPreset == preset,
                    onClick = { onSetAccent(preset) },
                )
            }
        }

        SettingGroupTitle(s.languageLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppLang.entries.forEach { l ->
                ChipBtn(text = l.label, selected = ui.appLang == l, onClick = { onSetAppLang(l) })
            }
        }

        SettingGroupTitle(s.voiceLangLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppLang.entries.forEach { l ->
                ChipBtn(text = l.label, selected = ui.voiceLang == l, onClick = { onSetVoiceLang(l) })
            }
        }

        SettingGroupTitle(s.aiVoiceLabel)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistantVoicePreset.entries.forEach { preset ->
                ChipBtn(
                    text = if (s.isPolish) preset.labelPl else preset.labelEn,
                    selected = ui.assistantVoice == preset,
                    onClick = { onSetAssistantVoice(preset) },
                )
            }
        }

        SettingGroupTitle(s.geminiKeyLabel)
        Text(s.geminiKeyHint, color = MutedText, fontSize = 11.sp)
        CompactField(
            value = geminiKeyBox,
            hint = "Gemini AI Studio Key (optional)",
            onValue = {
                geminiKeyBox = it
                onSaveGeminiApiKey(it)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        SettingGroupTitle(s.spotifyClientIdLabel)
        Text(s.spotifySetupHint, color = MutedText, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactField(
                value = spotifyIdBox,
                hint = "Spotify Client ID",
                onValue = {
                    spotifyIdBox = it
                    onSaveSpotifyId(it)
                },
                modifier = Modifier.weight(1f),
            )
            if (ui.spotifyConnected) {
                ChipBtn(text = "Disconnect", selected = false, onClick = onDisconnectSpotify)
            } else {
                ChipBtn(text = s.connectSpotify, selected = true, onClick = onConnectSpotify)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipBtn(text = "${s.resetTrip} ${s.tripA}", selected = false, onClick = onResetTripA)
            ChipBtn(text = "${s.resetTrip} ${s.tripB}", selected = false, onClick = onResetTripB)
        }
    }
}

@Composable
fun VoiceOverlay(
    ui: CarUiState,
    voiceState: VoiceUiState,
    onStartListening: () -> Unit,
    onSelectVoicePreset: (AssistantVoicePreset) -> Unit,
    onSubmitTextCommand: (String) -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalCarStrings.current
    var manualText by remember { mutableStateOf("") }
    val aiStatus = if (ui.geminiApiKey.isNotBlank()) "Gemini AI + ${s.aiEngineLabel}" else "${s.aiEngineLabel} · Offline"

    OverlayShell(title = "${s.voiceTitle} · $aiStatus", onClose = onClose) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistantVoicePreset.entries.forEach { preset ->
                    ChipBtn(
                        text = if (s.isPolish) preset.labelPl else preset.labelEn,
                        selected = ui.assistantVoice == preset,
                        onClick = { onSelectVoicePreset(preset) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (voiceState.isListening) EcoGreen else Color.White)
                    .clickable(onClick = onStartListening),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CarIcons.Mic, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
            }

            Text(
                text = if (voiceState.isListening) s.voiceListening else s.voiceTapMic,
                color = if (voiceState.isListening) EcoGreen else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )

            if (voiceState.partialText.isNotBlank()) {
                Text("\"${voiceState.partialText}\"", color = Color.White, fontSize = 13.sp)
            }
            voiceState.errorMsg?.let { err ->
                Text(err, color = EcoAmber, fontSize = 11.sp)
            }

            if (ui.aiIsThinking || ui.aiLastReply.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElevatedBlack)
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (ui.aiLastQuery.isNotBlank()) {
                        Text(ui.aiLastQuery, color = MutedText, fontSize = 11.sp)
                    }
                    Text(
                        text = if (ui.aiIsThinking) (if (s.isPolish) "AI analizuje..." else "AI thinking...") else ui.aiLastReply,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                }
            }

            Text(s.voiceHint, color = MutedText, fontSize = 11.sp)

            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipBtn(text = if (s.isPolish) "Czy starczy mi paliwa?" else "How is my eco score?", selected = false) {
                    onSubmitTextCommand(if (s.isPolish) "Czy starczy mi paliwa?" else "How is my eco score?")
                }
                s.voiceExamples.forEach { ex ->
                    ChipBtn(text = ex, selected = false, onClick = { onSubmitTextCommand(ex) })
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactField(value = manualText, hint = s.voiceTapMic, onValue = { manualText = it }, modifier = Modifier.weight(1f))
                ChipBtn(
                    text = s.go,
                    selected = true,
                    onClick = {
                        val submitted = manualText.trim()
                        if (submitted.isNotBlank()) {
                            manualText = ""
                            onSubmitTextCommand(submitted)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingGroupTitle(text: String) {
    Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
}

@Composable
private fun ChipBtn(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color.Black else Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else ElevatedBlack)
            .border(0.75.dp, if (selected) Color.White else HairlineBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun CompactField(value: String, hint: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ElevatedBlack)
            .border(0.75.dp, HairlineBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, color = MutedText, fontSize = 11.sp, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun describeWeather(code: Int, tempC: Double, windKmh: Double, isPl: Boolean): Pair<String, String> {
    val label = when (code) {
        0 -> if (isPl) "Bezchmurnie" else "Clear"
        1, 2, 3 -> if (isPl) "Zachmurzenie" else "Cloudy"
        45, 48 -> if (isPl) "Mgła" else "Fog"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> if (isPl) "Deszcz" else "Rain"
        71, 73, 75, 85, 86 -> if (isPl) "Śnieg" else "Snow"
        95, 96, 99 -> if (isPl) "Burza" else "Storm"
        else -> if (isPl) "Zmienna" else "Variable"
    }
    val tip = when {
        tempC <= 1.5 -> if (isPl) "Ryzyko gołoledzi — zachowaj większy odstęp" else "Freezing risk — watch for black ice"
        code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99) ->
            if (isPl) "Mokra nawierzchnia — wydłuż dystans hamowania" else "Wet roads — increase following distance"
        code in listOf(45, 48) ->
            if (isPl) "Ograniczona widoczność — włącz światła" else "Low visibility — reduce speed"
        windKmh > 45 ->
            if (isPl) "Silny wiatr boczny" else "Strong crosswinds"
        else ->
            if (isPl) "Dobre warunki drogowe" else "Clear driving conditions"
    }
    return label to tip
}
