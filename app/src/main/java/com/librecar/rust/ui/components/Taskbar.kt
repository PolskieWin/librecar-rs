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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librecar.rust.CarTab
import com.librecar.rust.CarUiState
import com.librecar.rust.data.TaskbarPosition
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.EcoGreen
import com.librecar.rust.ui.theme.ElevatedBlack
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
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
    val weatherTemp = ui.weather?.tempC?.roundToInt()?.let { "$it°C" } ?: "--°"

    if (isHorizontal) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height((66 * scale).dp)
                .clip(RoundedCornerShape(18.dp))
                .background(CardBlack)
                .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Clock + Calendar Taskbar Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (ui.showCalendarDrawer) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else ElevatedBlack)
                    .clickable(onClick = onToggleCalendar)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(CarIcons.Calendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Column {
                    Text(timeText, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text(
                        text = nextEvent?.title ?: dateText,
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Weather Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (ui.showWeatherDrawer) MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f) else ElevatedBlack)
                    .clickable(onClick = onToggleWeather)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(15.dp))
                Text(weatherTemp, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // App Switcher Icons
            TaskbarIconBtn(CarIcons.Navigation, ui.activeTab == CarTab.DASHBOARD, scale) { onSelectTab(CarTab.DASHBOARD) }
            TaskbarIconBtn(CarIcons.Music, ui.activeTab == CarTab.MUSIC, scale) { onSelectTab(CarTab.MUSIC) }
            TaskbarIconBtn(CarIcons.YouTube, ui.activeTab == CarTab.YOUTUBE, scale, tint = Color(0xFFFF0033)) { onSelectTab(CarTab.YOUTUBE) }
            TaskbarIconBtn(CarIcons.GamePad, ui.activeTab == CarTab.FLAPPY_BIRD, scale, tint = EcoGreen) { onSelectTab(CarTab.FLAPPY_BIRD) }
            TaskbarIconBtn(CarIcons.LayoutGrid, ui.showLayoutDrawer, scale) { onToggleLayout() }

            // Mic Button
            Box(
                modifier = Modifier
                    .size((46 * scale).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onOpenVoice),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CarIcons.Mic, contentDescription = "Voice", tint = Color.Black, modifier = Modifier.size(22.dp))
            }
        }
    } else {
        // Vertical CarPlay Dock (Left or Right)
        Column(
            modifier = modifier
                .width((76 * scale).dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(CardBlack)
                .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Clock + Calendar Trigger
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (ui.showCalendarDrawer) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else ElevatedBlack)
                    .clickable(onClick = onToggleCalendar)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Text(timeText, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(dateText, color = Color(0xFF9CA3AF), fontSize = 9.sp, maxLines = 1)
                if (nextEvent != null) {
                    Text(
                        text = nextEvent.title,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Weather Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (ui.showWeatherDrawer) MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f) else ElevatedBlack)
                    .clickable(onClick = onToggleWeather)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(14.dp))
                Text(" $weatherTemp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            // Navigation / App Icons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TaskbarLabeledBtn(CarIcons.Navigation, s.navMap, ui.activeTab == CarTab.DASHBOARD, scale) {
                    onSelectTab(CarTab.DASHBOARD)
                }
                TaskbarLabeledBtn(CarIcons.Music, s.navMusic, ui.activeTab == CarTab.MUSIC, scale) {
                    onSelectTab(CarTab.MUSIC)
                }
                TaskbarLabeledBtn(CarIcons.YouTube, s.navYouTube, ui.activeTab == CarTab.YOUTUBE, scale, activeTint = Color(0xFFFF0033)) {
                    onSelectTab(CarTab.YOUTUBE)
                }
                TaskbarLabeledBtn(CarIcons.GamePad, s.navGame, ui.activeTab == CarTab.FLAPPY_BIRD, scale, activeTint = EcoGreen) {
                    onSelectTab(CarTab.FLAPPY_BIRD)
                }
                TaskbarLabeledBtn(CarIcons.LayoutGrid, s.navLayout, ui.showLayoutDrawer, scale) {
                    onToggleLayout()
                }
            }

            // Voice Mic Button (EN / PL)
            Box(
                modifier = Modifier
                    .size((50 * scale).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onOpenVoice),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CarIcons.Mic, contentDescription = "Voice", tint = Color.Black, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun TaskbarIconBtn(
    icon: ImageVector,
    selected: Boolean,
    scale: Float,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val bg = if (selected) (tint ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.24f) else ElevatedBlack
    val fg = if (selected) (tint ?: MaterialTheme.colorScheme.primary) else Color(0xFFD1D5DB)
    Box(
        modifier = Modifier
            .size((42 * scale).dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, if (selected) fg.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TaskbarLabeledBtn(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    scale: Float,
    activeTint: Color? = null,
    onClick: () -> Unit,
) {
    val accent = activeTint ?: MaterialTheme.colorScheme.primary
    val bg = if (selected) accent.copy(alpha = 0.22f) else ElevatedBlack
    val fg = if (selected) accent else Color(0xFFD1D5DB)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, if (selected) accent.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = (5 * scale).dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(18.dp))
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
