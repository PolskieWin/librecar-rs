package com.librecar.rust.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librecar.rust.CarTab
import com.librecar.rust.CarUiState
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
    val scale = ui.touchScale.scale
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
                .height((64 * scale).dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardBlack)
                .border(0.75.dp, HairlineBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (ui.showCalendarDrawer) Color(0xFF23232A) else ElevatedBlack)
                    .clickable(onClick = onToggleCalendar)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(CarIcons.Calendar, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Column {
                    Text(timeText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                    Text(nextEvent?.title ?: dateText, color = MutedText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (ui.showWeatherDrawer) Color(0xFF23232A) else ElevatedBlack)
                    .clickable(onClick = onToggleWeather)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(weatherTemp, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            }

            TaskbarIconBtn(CarIcons.Navigation, ui.activeTab == CarTab.DASHBOARD, scale) { onSelectTab(CarTab.DASHBOARD) }
            TaskbarIconBtn(CarIcons.Music, ui.activeTab == CarTab.MUSIC, scale) { onSelectTab(CarTab.MUSIC) }
            TaskbarIconBtn(CarIcons.YouTube, ui.activeTab == CarTab.YOUTUBE, scale) { onSelectTab(CarTab.YOUTUBE) }
            TaskbarIconBtn(CarIcons.GamePad, ui.activeTab == CarTab.FLAPPY_BIRD, scale) { onSelectTab(CarTab.FLAPPY_BIRD) }
            TaskbarIconBtn(CarIcons.LayoutGrid, ui.showLayoutDrawer, scale) { onToggleLayout() }

            Box(
                modifier = Modifier
                    .size((44 * scale).dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onOpenVoice),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CarIcons.Mic, contentDescription = "Voice", tint = Color.Black, modifier = Modifier.size(20.dp))
            }
        }
    } else {
        Column(
            modifier = modifier
                .width((74 * scale).dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBlack)
                .border(0.75.dp, HairlineBorder, RoundedCornerShape(16.dp))
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (ui.showCalendarDrawer) Color(0xFF23232A) else ElevatedBlack)
                    .clickable(onClick = onToggleCalendar)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Text(timeText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                Text(dateText, color = MutedText, fontSize = 9.sp, maxLines = 1)
                if (nextEvent != null) {
                    Text(nextEvent.title, color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (ui.showWeatherDrawer) Color(0xFF23232A) else ElevatedBlack)
                    .clickable(onClick = onToggleWeather)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Text(" $weatherTemp", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                TaskbarLabeledBtn(CarIcons.Navigation, s.navMap, ui.activeTab == CarTab.DASHBOARD, scale) {
                    onSelectTab(CarTab.DASHBOARD)
                }
                TaskbarLabeledBtn(CarIcons.Music, s.navMusic, ui.activeTab == CarTab.MUSIC, scale) {
                    onSelectTab(CarTab.MUSIC)
                }
                TaskbarLabeledBtn(CarIcons.YouTube, s.navYouTube, ui.activeTab == CarTab.YOUTUBE, scale) {
                    onSelectTab(CarTab.YOUTUBE)
                }
                TaskbarLabeledBtn(CarIcons.GamePad, s.navGame, ui.activeTab == CarTab.FLAPPY_BIRD, scale) {
                    onSelectTab(CarTab.FLAPPY_BIRD)
                }
                TaskbarLabeledBtn(CarIcons.LayoutGrid, s.navLayout, ui.showLayoutDrawer, scale) {
                    onToggleLayout()
                }
            }

            Box(
                modifier = Modifier
                    .size((46 * scale).dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onOpenVoice),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CarIcons.Mic, contentDescription = "Voice", tint = Color.Black, modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun TaskbarIconBtn(icon: ImageVector, selected: Boolean, scale: Float, onClick: () -> Unit) {
    val bg = if (selected) Color.White else ElevatedBlack
    val fg = if (selected) Color.Black else Color(0xFFD4D4D8)
    Box(
        modifier = Modifier
            .size((40 * scale).dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun TaskbarLabeledBtn(icon: ImageVector, label: String, selected: Boolean, scale: Float, onClick: () -> Unit) {
    val bg = if (selected) Color.White else ElevatedBlack
    val fg = if (selected) Color.Black else Color(0xFFD4D4D8)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = (5 * scale).dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(17.dp))
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
