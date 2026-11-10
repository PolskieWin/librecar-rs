package com.librecar.rust.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.EcoGreen
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
import kotlinx.coroutines.isActive

@Composable
fun FlappyBirdPane(
    speedKmh: Double,
    bestScore: Int,
    lowEndMode: Boolean,
    onBestScoreUpdate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalCarStrings.current
    var soundEnabled by remember { mutableStateOf(false) }
    var flapQueued by remember { mutableStateOf(false) }
    var frameState by remember { mutableStateOf(RustEngine.safeFlappyStep(0f, false)) }
    var lastScore by remember { mutableIntStateOf(0) }
    var lastState by remember { mutableIntStateOf(0) }

    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { toneGen?.release() } }
    }

    LaunchedEffect(Unit) {
        RustEngine.safeFlappySetBest(bestScore)
        var lastNanos = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNanos = now

            val shouldFlap = flapQueued
            if (shouldFlap) {
                flapQueued = false
                if (soundEnabled) runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 35) }
            }
            val next = RustEngine.safeFlappyStep(dt, shouldFlap)
            val scoreNow = next[3].toInt()
            val bestNow = next[4].toInt()
            val stateNow = next[5].toInt()

            if (scoreNow > lastScore) {
                if (soundEnabled) runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 50) }
                if (bestNow > bestScore) onBestScoreUpdate(bestNow)
            }
            if (stateNow == 2 && lastState != 2 && soundEnabled) {
                runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 90) }
            }
            lastScore = scoreNow
            lastState = stateNow
            frameState = next
        }
    }

    val primaryCol = MaterialTheme.colorScheme.primary
    val secondaryCol = MaterialTheme.colorScheme.secondary
    val gameState = frameState[5].toInt()
    val score = frameState[3].toInt()
    val displayedBest = maxOf(bestScore, frameState[4].toInt())

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardBlack)
            .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header HUD
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${s.flappyTitle} • Rust 60fps",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${s.flappyScore}: $score",
                    color = EcoGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                )
                Text(
                    text = "${s.flappyBest}: $displayedBest",
                    color = secondaryCol,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Text(
                    text = if (soundEnabled) "SFX ON" else "SFX OFF",
                    color = if (soundEnabled) Color.Black else Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (soundEnabled) primaryCol else Color(0xFF1A1A22))
                        .clickable { soundEnabled = !soundEnabled }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (speedKmh > 10.0) {
            Text(
                text = s.ytPassengerWarning,
                color = Color(0xFFFFAB40),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Playable Zero-Slop Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF040406))
                .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        flapQueued = true
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Minimal twinkling stars (skipped on low-end mode for ultra speed)
                if (!lowEndMode) {
                    for (i in 0 until 14) {
                        val sx = ((i * 73 + 19) % 100) / 100f * w
                        val sy = ((i * 41 + 11) % 75) / 100f * h
                        drawCircle(Color(0xFF272734), radius = 2.2f, center = Offset(sx, sy))
                    }
                }

                // 4 Pipe pairs from Rust state array
                val pipeW = 0.11f * w
                val gapHalf = frameState[14] * h
                for (i in 0 until 4) {
                    val px = frameState[6 + i * 2] * w
                    val gapY = frameState[7 + i * 2] * h
                    val topH = (gapY - gapHalf).coerceAtLeast(0f)
                    val botY = (gapY + gapHalf).coerceAtMost(0.90f * h)
                    val botH = (0.90f * h - botY).coerceAtLeast(0f)

                    // Top pipe
                    drawRoundRect(
                        color = primaryCol.copy(alpha = 0.85f),
                        topLeft = Offset(px, 0f),
                        size = Size(pipeW, topH),
                        cornerRadius = CornerRadius(10f, 10f),
                    )
                    // Pipe lip
                    if (topH > 12f) {
                        drawRoundRect(
                            color = primaryCol,
                            topLeft = Offset(px - 4f, topH - 14f),
                            size = Size(pipeW + 8f, 14f),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                    }
                    // Bottom pipe
                    drawRoundRect(
                        color = primaryCol.copy(alpha = 0.85f),
                        topLeft = Offset(px, botY),
                        size = Size(pipeW, botH),
                        cornerRadius = CornerRadius(10f, 10f),
                    )
                    if (botH > 12f) {
                        drawRoundRect(
                            color = primaryCol,
                            topLeft = Offset(px - 4f, botY),
                            size = Size(pipeW + 8f, 14f),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                    }
                }

                // Ground strip
                val groundY = 0.90f * h
                drawRect(
                    color = Color(0xFF14141B),
                    topLeft = Offset(0f, groundY),
                    size = Size(w, h - groundY),
                )
                drawLine(
                    color = EcoGreen,
                    start = Offset(0f, groundY),
                    end = Offset(w, groundY),
                    strokeWidth = 3f,
                )

                // Bird
                val birdCx = 0.24f * w
                val birdCy = frameState[0] * h
                val birdR = (0.028f * minOf(w, h)).coerceAtLeast(14f)
                val rot = frameState[2]
                rotate(degrees = rot, pivot = Offset(birdCx, birdCy)) {
                    // Body
                    drawCircle(
                        color = Color(0xFFFFD54F),
                        radius = birdR,
                        center = Offset(birdCx, birdCy),
                    )
                    // Wing
                    drawOval(
                        color = Color(0xFFFFA000),
                        topLeft = Offset(birdCx - birdR * 0.65f, birdCy - birdR * 0.1f),
                        size = Size(birdR * 0.95f, birdR * 0.55f),
                    )
                    // Eye
                    drawCircle(
                        color = Color.White,
                        radius = birdR * 0.34f,
                        center = Offset(birdCx + birdR * 0.42f, birdCy - birdR * 0.25f),
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = birdR * 0.17f,
                        center = Offset(birdCx + birdR * 0.52f, birdCy - birdR * 0.25f),
                    )
                    // Beak
                    drawRoundRect(
                        color = Color(0xFFFF5252),
                        topLeft = Offset(birdCx + birdR * 0.72f, birdCy - birdR * 0.05f),
                        size = Size(birdR * 0.55f, birdR * 0.35f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            }

            if (gameState == 0 || gameState == 2) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC000000))
                        .border(1.dp, if (gameState == 2) Color(0xFFFF5252) else primaryCol, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = if (gameState == 2) "${s.flappyGameOver} ($score)" else s.flappyTapStart,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
