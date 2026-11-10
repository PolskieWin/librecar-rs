package com.librecar.rust.ui.screens

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.librecar.rust.ui.theme.AlertRed
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePane(
    targetUrl: String,
    speedKmh: Double,
    passengerConfirmed: Boolean,
    lowEndMode: Boolean,
    onConfirmPassenger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalCarStrings.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedUrl by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var fullscreenContainer by remember { mutableStateOf<FrameLayout?>(null) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    BackHandler(enabled = canGoBack || customVideoView != null) {
        if (customVideoView != null) {
            customCallback?.onCustomViewHidden()
        } else {
            webViewRef?.goBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                customCallback?.onCustomViewHidden()
                webViewRef?.stopLoading()
                webViewRef?.onPause()
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .border(0.75.dp, HairlineBorder, RoundedCornerShape(14.dp)),
    ) {
        AndroidView(
            factory = { ctx ->
                val container = FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
                val fsOverlay = FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    visibility = View.GONE
                }
                fullscreenContainer = fsOverlay

                val wv = WebView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowContentAccess = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            canGoBack = view?.canGoBack() == true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view == null) return
                            customVideoView = view
                            customCallback = callback
                            fsOverlay.removeAllViews()
                            fsOverlay.addView(view)
                            fsOverlay.visibility = View.VISIBLE
                            visibility = View.GONE
                        }

                        override fun onHideCustomView() {
                            fsOverlay.removeAllViews()
                            fsOverlay.visibility = View.GONE
                            visibility = View.VISIBLE
                            customVideoView = null
                            customCallback?.onCustomViewHidden()
                            customCallback = null
                        }
                    }
                    lastLoadedUrl = targetUrl
                    loadUrl(targetUrl)
                }
                webViewRef = wv
                container.addView(wv)
                container.addView(fsOverlay)
                container
            },
            update = {
                webViewRef?.onResume()
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
                Icon(CarIcons.YouTube, contentDescription = null, tint = AlertRed, modifier = Modifier.size(38.dp))
                Spacer(Modifier.height(10.dp))
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
