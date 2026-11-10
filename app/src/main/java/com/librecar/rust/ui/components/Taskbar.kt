package com.librecar.rust.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librecar.rust.CarTab
import com.librecar.rust.CarUiState
import com.librecar.rust.data.LayoutMode
import com.librecar.rust.data.TaskbarPosition
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.ElevatedBlack
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
import com.librecar.rust.ui.theme.MutedText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CarTaskbar(
    ui: CarUiState,
    onSelectTab: (CarTab) -> Unit,
    onToggleWeather: () -> Unit,
    onToggleCalendar: () -> Unit,
    onToggleLayout: () -> Unit,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalCarStrings.current
    val isUltra = ui.layoutMode == LayoutMode.ULTRA_COMPACT
    val scale = if (isUltra) 0.88f else ui.touchScale.scale
    val isHorizontal = ui.taskbarPosition == TaskbarPosition.BOTTOM

    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val df = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        while (isActive) {
            val now = Date()
            timeText = tf.format(now)
            dateText = df.format(now)
            delay(15_000L)
        }
    }

    val nextEvent = ui.calendarEvents.firstOrNull()
    val weatherTemp = ui.weather?.tempC?.roundToInt()?.let { "$it°" } ?: "--°"

    if (isHorizontal) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height((if (isUltra) 48f else 56f * scale).dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CardBlack)
                .border(0.75.dp, HairlineBorder, RoundedCornerShape(14.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TaskbarIconBtn(CarIcons.SettingsGear, ui.showLayoutDrawer, scale, "Settings", onToggleLayout)

            Row(
                modifier = Modifier
                    .semantics { contentDescription = "Calendar" }
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (ui.showCalendarDrawer) Color(0xFF23232A) else ElevatedBlack)
                    .clickable(onClick = onToggleCalendar)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(CarIcons.Calendar, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Column {
                    Text(timeText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                    Text(nextEvent?.title ?: dateText, color = MutedText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                modifier = Modifier
                    .semantics { contentDescription = "Weather" }
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (ui.showWeatherDrawer) Color(0xFF23232A) else ElevatedBlack)
                    .clickable(onClick = onToggleWeather)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Text(weatherTemp, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            }

            TaskbarIconBtn(CarIcons.Navigation, ui.activeTab == CarTab.DASHBOARD, scale, s.navMap) { onSelectTab(CarTab.DASHBOARD) }
            TaskbarIconBtn(CarIcons.Music, ui.activeTab == CarTab.MUSIC, scale, s.navMusic) { onSelectTab(CarTab.MUSIC) }
            TaskbarIconBtn(CarIcons.Radio, ui.activeTab == CarTab.RADIO, scale, s.navRadio) { onSelectTab(CarTab.RADIO) }
            TaskbarIconBtn(CarIcons.YouTube, ui.activeTab == CarTab.YOUTUBE, scale, s.navYouTube) { onSelectTab(CarTab.YOUTUBE) }
            TaskbarIconBtn(CarIcons.GamePad, ui.activeTab == CarTab.FLAPPY_BIRD, scale, s.navGame) { onSelectTab(CarTab.FLAPPY_BIRD) }

            Box(
                modifier = Modifier
                    .size((38 * scale).dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onOpenVoice),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CarIcons.Mic, contentDescription = "Voice", tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        }
    } else {
        // Vertical Left/Right Taskbar with scrollable app switcher + down-left Gear button for Settings & Fuel
        Column(
            modifier = modifier
                .width((if (isUltra) 58f else 70f * scale).dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(CardBlack)
                .border(0.75.dp, HairlineBorder, RoundedCornerShape(14.dp))
                .padding(vertical = 7.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Calendar" }
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (ui.showCalendarDrawer) Color(0xFF23232A) else ElevatedBlack)
                    .clickable(onClick = onToggleCalendar)
                    .padding(vertical = 5.dp, horizontal = 3.dp),
            ) {
                Text(timeText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (isUltra) 12.sp else 14.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                if (!isUltra) {
                    Text(dateText, color = MutedText, fontSize = 9.sp, maxLines = 1)
                    if (nextEvent != null) {
                        Text(nextEvent.title, color = Color.White, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Weather" }
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (ui.showWeatherDrawer) Color(0xFF23232A) else ElevatedBlack)
                    .clickable(onClick = onToggleWeather)
                    .padding(vertical = 5.dp, horizontal = 3.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(" $weatherTemp", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            }

            // Middle scrollable app stack (Map, Music, Radio, YouTube, Flappy)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                TaskbarLabeledBtn(CarIcons.Navigation, s.navMap, ui.activeTab == CarTab.DASHBOARD, scale, isUltra) {
                    onSelectTab(CarTab.DASHBOARD)
                }
                TaskbarLabeledBtn(CarIcons.Music, s.navMusic, ui.activeTab == CarTab.MUSIC, scale, isUltra) {
                    onSelectTab(CarTab.MUSIC)
                }
                TaskbarLabeledBtn(CarIcons.Radio, s.navRadio, ui.activeTab == CarTab.RADIO, scale, isUltra) {
                    onSelectTab(CarTab.RADIO)
                }
                TaskbarLabeledBtn(CarIcons.YouTube, s.navYouTube, ui.activeTab == CarTab.YOUTUBE, scale, isUltra) {
                    onSelectTab(CarTab.YOUTUBE)
                }
                TaskbarLabeledBtn(CarIcons.GamePad, s.navGame, ui.activeTab == CarTab.FLAPPY_BIRD, scale, isUltra) {
                    onSelectTab(CarTab.FLAPPY_BIRD)
                }
            }

            // Voice button + Down-Left Gear Settings button (Layouts, Car Model, Tank Fuel, 120Hz, Polskie Repo)
            Box(
                modifier = Modifier
                    .size((if (isUltra) 34f else 38f * scale).dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onOpenVoice),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CarIcons.Mic, contentDescription = "Voice", tint = Color.Black, modifier = Modifier.size(18.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((if (isUltra) 32f else 36f * scale).dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (ui.showLayoutDrawer) Color.White else ElevatedBlack)
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleLayout),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CarIcons.SettingsGear,
                    contentDescription = "Settings",
                    tint = if (ui.showLayoutDrawer) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TaskbarIconBtn(icon: ImageVector, selected: Boolean, scale: Float, desc: String, onClick: () -> Unit) {
    val bg = if (selected) Color.White else ElevatedBlack
    val fg = if (selected) Color.Black else Color(0xFFD4D4D8)
    Box(
        modifier = Modifier
            .size((36 * scale).dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = fg, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TaskbarLabeledBtn(icon: ImageVector, label: String, selected: Boolean, scale: Float, iconOnly: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color.White else ElevatedBlack
    val fg = if (selected) Color.Black else Color(0xFFD4D4D8)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = (if (iconOnly) 7f else 5f * scale).dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(16.dp))
        if (!iconOnly) {
            Text(
                text = label,
                color = fg,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
