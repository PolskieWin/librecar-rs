package com.librecar.rust.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.librecar.rust.ui.theme.AlertRed
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.ElevatedBlack
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
import com.librecar.rust.ui.theme.MutedText

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePane(
    targetUrl: String,
    speedKmh: Double,
    passengerConfirmed: Boolean,
    lowEndMode: Boolean,
    onConfirmPassenger: () -> Unit,
    onSearchQuery: (String) -> Unit,
    onCloseFreeRam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalCarStrings.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedUrl by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.loadUrl("about:blank")
                webViewRef?.clearHistory()
                webViewRef?.destroy()
            }
            webViewRef = null
        }
    }

    LaunchedEffect(targetUrl, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (targetUrl != lastLoadedUrl) {
            lastLoadedUrl = targetUrl
            wv.loadUrl(targetUrl)
        }
    }

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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(CarIcons.YouTube, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElevatedBlack)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("YouTube...", color = MutedText, fontSize = 13.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            onSearchQuery(query)
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = s.go,
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White)
                        .clickable {
                            keyboard?.hide()
                            onSearchQuery(query)
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Text(
                text = s.ytFreeRam,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElevatedBlack)
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onCloseFreeRam)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val presets = listOf("Lo-Fi Beats", "Night Drive", "Polskie Hity", "Podcast", "Music")
            presets.forEach { p ->
                Text(
                    text = p,
                    color = MutedText,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElevatedBlack)
                        .clickable { onSearchQuery(p) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.cacheMode = if (lowEndMode) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                canGoBack = view?.canGoBack() == true
                            }
                        }
                        webChromeClient = WebChromeClient()
                        lastLoadedUrl = targetUrl
                        loadUrl(targetUrl)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (speedKmh > 10.0 && !passengerConfirmed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xF208080A))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(CarIcons.YouTube, contentDescription = null, tint = AlertRed, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(s.ytPassengerWarning, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = s.ytContinuePassenger,
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .clickable(onClick = onConfirmPassenger)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}
