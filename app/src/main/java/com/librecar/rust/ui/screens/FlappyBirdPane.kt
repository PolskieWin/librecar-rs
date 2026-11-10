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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.ui.theme.AlertRed
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.EcoAmber
import com.librecar.rust.ui.theme.ElevatedBlack
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
import com.librecar.rust.ui.theme.MutedText
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
    val frameBuf = remember { FloatArray(15).also { RustEngine.safeFlappyStepInto(0f, false, it) } }
    var frameTick by remember { mutableIntStateOf(0) }
    var lastScore by remember { mutableIntStateOf(0) }
    var lastState by remember { mutableIntStateOf(0) }
    var gameOverTimestampMs by remember { mutableLongStateOf(0L) }

    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 30) }.getOrNull()
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

            val canRestart = frameBuf[5].toInt() != 2 || (System.currentTimeMillis() - gameOverTimestampMs) > 350L
            val shouldFlap = flapQueued && canRestart
            flapQueued = false
            if (shouldFlap && soundEnabled) {
                runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 30) }
            }
            RustEngine.safeFlappyStepInto(dt, shouldFlap, frameBuf)
            val scoreNow = frameBuf[3].toInt()
            val bestNow = frameBuf[4].toInt()
            val stateNow = frameBuf[5].toInt()

            if (scoreNow > lastScore) {
                if (soundEnabled) runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 45) }
                if (bestNow > bestScore) onBestScoreUpdate(bestNow)
            }
            if (stateNow == 2 && lastState != 2) {
                gameOverTimestampMs = System.currentTimeMillis()
                if (soundEnabled) runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 80) }
            }
            lastScore = scoreNow
            lastState = stateNow
            frameTick++
        }
    }

    val gameState = frameBuf[5].toInt()
    val score = frameBuf[3].toInt()
    val displayedBest = maxOf(bestScore, frameBuf[4].toInt())

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBlack)
            .border(0.75.dp, HairlineBorder, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.flappyTitle, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${s.flappyScore} $score",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Text(
                    text = "${s.flappyBest} $displayedBest",
                    color = MutedText,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Text(
                    text = if (soundEnabled) "SFX ON" else "SFX OFF",
                    color = if (soundEnabled) Color.Black else MutedText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (soundEnabled) Color.White else ElevatedBlack)
                        .clickable { soundEnabled = !soundEnabled }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (speedKmh > 10.0) {
            Text(s.ytPassengerWarning, color = EcoAmber, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF040406))
                .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        flapQueued = true
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val unused = frameTick
                val w = size.width
                val h = size.height

                if (!lowEndMode) {
                    for (i in 0 until 10) {
                        val sx = ((i * 73 + 19) % 100) / 100f * w
                        val sy = ((i * 41 + 11) % 75) / 100f * h
                        drawCircle(Color(0xFF1F1F26), radius = 1.8f, center = Offset(sx, sy))
                    }
                }

                val pipeW = 0.11f * w
                val gapHalf = frameBuf[14] * h
                for (i in 0 until 4) {
                    val px = frameBuf[6 + i * 2] * w
                    val gapY = frameBuf[7 + i * 2] * h
                    val topH = (gapY - gapHalf).coerceAtLeast(0f)
                    val botY = (gapY + gapHalf).coerceAtMost(0.90f * h)
                    val botH = (0.90f * h - botY).coerceAtLeast(0f)

                    drawRoundRect(
                        color = Color(0xFFD4D4D8),
                        topLeft = Offset(px, 0f),
                        size = Size(pipeW, topH),
                        cornerRadius = CornerRadius(6f, 6f),
                    )
                    if (topH > 12f) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(px - 3f, topH - 12f),
                            size = Size(pipeW + 6f, 12f),
                            cornerRadius = CornerRadius(4f, 4f),
                        )
                    }
                    drawRoundRect(
                        color = Color(0xFFD4D4D8),
                        topLeft = Offset(px, botY),
                        size = Size(pipeW, botH),
                        cornerRadius = CornerRadius(6f, 6f),
                    )
                    if (botH > 12f) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(px - 3f, botY),
                            size = Size(pipeW + 6f, 12f),
                            cornerRadius = CornerRadius(4f, 4f),
                        )
                    }
                }

                val groundY = 0.90f * h
                drawRect(color = Color(0xFF121216), topLeft = Offset(0f, groundY), size = Size(w, h - groundY))
                drawLine(color = Color(0xFF52525B), start = Offset(0f, groundY), end = Offset(w, groundY), strokeWidth = 2f)

                val birdCx = 0.24f * w
                val birdCy = frameBuf[0] * h
                val birdR = (0.028f * minOf(w, h)).coerceAtLeast(14f)
                rotate(degrees = frameBuf[2], pivot = Offset(birdCx, birdCy)) {
                    drawCircle(color = Color.White, radius = birdR, center = Offset(birdCx, birdCy))
                    drawOval(
                        color = Color(0xFFA1A1AA),
                        topLeft = Offset(birdCx - birdR * 0.65f, birdCy - birdR * 0.1f),
                        size = Size(birdR * 0.95f, birdR * 0.55f),
                    )
                    drawCircle(color = Color.Black, radius = birdR * 0.22f, center = Offset(birdCx + birdR * 0.45f, birdCy - birdR * 0.22f))
                    drawRoundRect(
                        color = EcoAmber,
                        topLeft = Offset(birdCx + birdR * 0.74f, birdCy - birdR * 0.05f),
                        size = Size(birdR * 0.5f, birdR * 0.32f),
                        cornerRadius = CornerRadius(3f, 3f),
                    )
                }
            }

            if (gameState == 0 || gameState == 2) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xD8000000))
                        .border(0.75.dp, if (gameState == 2) AlertRed else HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (gameState == 2) "${s.flappyGameOver} ($score)" else s.flappyTapStart,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
