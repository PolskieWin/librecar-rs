package com.librecar.rust.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

private val NeonCyan = Color(0xFF38BDF8)

@Composable
fun GeometryDashPane(
    speedKmh: Double,
    bestScore: Int,
    lowEndMode: Boolean,
    onBestScoreUpdate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalCarStrings.current
    var soundEnabled by remember { mutableStateOf(false) }
    var isHoldingJump by remember { mutableStateOf(false) }
    val frameBuf = remember { FloatArray(31).also { RustEngine.safeDashStepInto(0f, false, it) } }
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
        RustEngine.safeDashSetBest(bestScore)
        var lastNanos = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNanos = now

            val canInput = frameBuf[4].toInt() != 2 || (System.currentTimeMillis() - gameOverTimestampMs) > 320L
            val hold = isHoldingJump && canInput
            if (hold && frameBuf[4].toInt() != 1 && soundEnabled) {
                runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 25) }
            }
            RustEngine.safeDashStepInto(dt, hold, frameBuf)
            val scoreNow = frameBuf[2].toInt()
            val bestNow = frameBuf[3].toInt()
            val stateNow = frameBuf[4].toInt()

            if (scoreNow > lastScore) {
                if (soundEnabled) runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 30) }
                if (bestNow > bestScore) onBestScoreUpdate(bestNow)
            }
            if (stateNow == 2 && lastState != 2) {
                gameOverTimestampMs = System.currentTimeMillis()
                if (soundEnabled) runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 90) }
            }
            lastScore = scoreNow
            lastState = stateNow
            frameTick++
        }
    }

    val gameState = frameBuf[4].toInt()
    val score = frameBuf[2].toInt()
    val displayedBest = maxOf(bestScore, frameBuf[3].toInt())
    val attempt = frameBuf[5].toInt().coerceAtLeast(1)

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
            Text("${s.dashTitle} · #$attempt", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${s.flappyScore} $score",
                    color = NeonCyan,
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
                .background(Color(0xFF030508))
                .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isHoldingJump = true
                        waitForUpOrCancellation()
                        isHoldingJump = false
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val unused = frameTick
                val w = size.width
                val h = size.height
                val groundY = 0.80f * h
                val scrollPhase = frameBuf[6]

                if (!lowEndMode) {
                    // Minimalist parallax horizon grid stripes
                    val stepX = (w / 10f).coerceAtLeast(48f)
                    val offset = (scrollPhase * stepX) % stepX
                    var gx = -offset
                    while (gx < w) {
                        drawLine(
                            color = Color(0xFF101824),
                            start = Offset(gx, groundY),
                            end = Offset(gx - 28f, h),
                            strokeWidth = 1.5f,
                        )
                        gx += stepX
                    }
                }

                // Obstacles: 0 = spike, 1 = solid block, 2 = double spike
                for (i in 0 until 6) {
                    val base = 7 + i * 4
                    val ox = frameBuf[base] * w
                    val ow = frameBuf[base + 1] * w
                    val oh = frameBuf[base + 2] * h
                    val kind = frameBuf[base + 3].toInt()
                    if (ox > w || ox + ow < 0f) continue

                    val topY = groundY - oh
                    when (kind) {
                        1 -> {
                            // Solid jump block (landable top!)
                            drawRoundRect(
                                color = Color(0xFF18181B),
                                topLeft = Offset(ox, topY),
                                size = Size(ow, oh),
                                cornerRadius = CornerRadius(4f, 4f),
                            )
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(ox, topY),
                                size = Size(ow, oh),
                                cornerRadius = CornerRadius(4f, 4f),
                                style = Stroke(width = 2.2f),
                            )
                        }
                        2 -> {
                            // Twin spikes
                            val singleW = ow * 0.5f
                            for (k in 0..1) {
                                val sx = ox + k * singleW
                                val p = Path().apply {
                                    moveTo(sx + singleW * 0.5f, topY)
                                    lineTo(sx + singleW, groundY)
                                    lineTo(sx, groundY)
                                    close()
                                }
                                drawPath(p, color = Color(0xFF1E293B))
                                drawPath(p, color = NeonCyan, style = Stroke(width = 2.2f))
                            }
                        }
                        else -> {
                            // Single spike
                            val p = Path().apply {
                                moveTo(ox + ow * 0.5f, topY)
                                lineTo(ox + ow, groundY)
                                lineTo(ox, groundY)
                                close()
                            }
                            drawPath(p, color = Color(0xFF1E293B))
                            drawPath(p, color = NeonCyan, style = Stroke(width = 2.2f))
                        }
                    }
                }

                // Ground floor + glowing horizon line
                drawRect(color = Color(0xFF0B0E14), topLeft = Offset(0f, groundY), size = Size(w, h - groundY))
                drawLine(color = Color.White, start = Offset(0f, groundY), end = Offset(w, groundY), strokeWidth = 2.5f)

                // Geometry Dash Cube (1:1 synchronized with Rust physics cube height)
                val cubeSide = (0.056f * h).coerceAtLeast(22f)
                val cubeCx = 0.22f * w
                val cubeBottom = frameBuf[0] * h
                val cubeTop = cubeBottom - cubeSide
                val cubeCenter = Offset(cubeCx, cubeTop + cubeSide * 0.5f)

                rotate(degrees = frameBuf[1], pivot = cubeCenter) {
                    drawRoundRect(
                        color = NeonCyan,
                        topLeft = Offset(cubeCx - cubeSide * 0.5f, cubeTop),
                        size = Size(cubeSide, cubeSide),
                        cornerRadius = CornerRadius(5f, 5f),
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(cubeCx - cubeSide * 0.5f, cubeTop),
                        size = Size(cubeSide, cubeSide),
                        cornerRadius = CornerRadius(5f, 5f),
                        style = Stroke(width = 2.2f),
                    )
                    // Minimalist cube eyes & mouth
                    val eyeS = cubeSide * 0.20f
                    drawRect(Color.Black, topLeft = Offset(cubeCx - cubeSide * 0.26f, cubeTop + cubeSide * 0.24f), size = Size(eyeS, eyeS))
                    drawRect(Color.Black, topLeft = Offset(cubeCx + cubeSide * 0.06f, cubeTop + cubeSide * 0.24f), size = Size(eyeS, eyeS))
                    drawRect(Color.Black, topLeft = Offset(cubeCx - cubeSide * 0.26f, cubeTop + cubeSide * 0.62f), size = Size(cubeSide * 0.52f, cubeSide * 0.14f))
                }
            }

            if (gameState == 0 || gameState == 2) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xD8000000))
                        .border(0.75.dp, if (gameState == 2) AlertRed else NeonCyan, RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (gameState == 2) "${s.dashGameOver} ($score)" else s.dashTapJump,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
