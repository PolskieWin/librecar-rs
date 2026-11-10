package com.librecar.rust.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.librecar.rust.CarUiState
import com.librecar.rust.data.CarCache
import com.librecar.rust.data.CarLocationState
import com.librecar.rust.data.LayoutMode
import com.librecar.rust.data.MapVisualMode
import com.librecar.rust.data.PlaceSearchResult
import com.librecar.rust.data.RouteOption
import com.librecar.rust.ui.theme.AlertRed
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.EcoAmber
import com.librecar.rust.ui.theme.EcoGreen
import com.librecar.rust.ui.theme.ElevatedBlack
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
import com.librecar.rust.ui.theme.MutedText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val SRC_ALT_ROUTES = "lc-alt-routes-src"
private const val SRC_ECO_ROUTE = "lc-eco-route-src"
private const val SRC_SPEED_ROUTE = "lc-speed-route-src"
private const val SRC_ACTIVE_ROUTE = "lc-active-route-src"
private const val SRC_CAR_PUCK = "lc-car-puck-src"

private const val LAYER_ALT_ROUTES = "lc-alt-routes-layer"
private const val LAYER_ECO_ROUTE = "lc-eco-route-layer"
private const val LAYER_SPEED_ROUTE = "lc-speed-route-layer"
private const val LAYER_ACTIVE_ROUTE = "lc-active-route-layer"
private const val LAYER_PUCK_HALO = "lc-puck-halo-layer"
private const val LAYER_PUCK_DOT = "lc-puck-dot-layer"

private val SpeedCyan = Color(0xFF38BDF8)

@Composable
fun MapPane(
    ui: CarUiState,
    loc: CarLocationState,
    isCompactHud: Boolean,
    isMapVisible: Boolean = true,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectPlace: (PlaceSearchResult) -> Unit,
    onSelectRoute: (Int) -> Unit,
    onSetPreferSpeed: (Boolean) -> Unit,
    onClearRoute: () -> Unit,
    onSetMapMode: (MapVisualMode) -> Unit,
    onToggleTilt3d: () -> Unit,
    onSetFollow: (Boolean) -> Unit,
    onResetTripA: () -> Unit,
    onAdjustFuelDelta: (Double) -> Unit,
    onFillTank: () -> Unit,
    onOpenVoice: () -> Unit,
    onRequestLocationPerm: () -> Unit,
    onSetMapHudScale: (Float) -> Unit = {},
    hasLocationPerm: Boolean,
    modifier: Modifier = Modifier,
) {
    val s = LocalCarStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboard = LocalSoftwareKeyboardController.current

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleReady by remember { mutableStateOf(false) }
    var loadedStyleKey by remember { mutableStateOf("") }
    var showTripB by remember { mutableStateOf(false) }
    var showFuelQuickMenu by remember { mutableStateOf(false) }
    var showTelemetryPopup by remember { mutableStateOf(false) }
    var showRoutePicker by remember { mutableStateOf(false) }
    var topBarExpanded by remember { mutableStateOf(ui.layoutMode != LayoutMode.ULTRA_COMPACT) }
    var userPinnedSearch by remember { mutableStateOf(false) }
    var showHudSizeSlider by remember { mutableStateOf(false) }

    val mapView = remember(ui.lowEndMode) {
        val opts = MapLibreMapOptions.createFromAttributes(context).apply {
            textureMode(true) // Prevents SurfaceView black-hole z-fighting inside Compose
            if (ui.lowEndMode) {
                pixelRatio(1.0f)
            }
            compassEnabled(false)
            logoEnabled(false)
            attributionEnabled(false)
        }
        MapView(context, opts)
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView, isMapVisible) {
        if (isMapVisible) mapView.onResume() else mapView.onPause()
    }

    val styleKey = "${ui.mapVisualMode.id}_${ui.terrain3dEnabled}_${ui.lowEndMode}"
    LaunchedEffect(mapInstance, styleKey) {
        val map = mapInstance ?: return@LaunchedEffect
        if (loadedStyleKey == styleKey) return@LaunchedEffect
        loadedStyleKey = styleKey
        isStyleReady = false
        val styleJson = resolveMapStyleJson(ui.mapVisualMode, ui.terrain3dEnabled && !ui.lowEndMode)
        map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
            ensureOverlayLayers(style)
            isStyleReady = true
        }
    }

    LaunchedEffect(mapView, ui.highRefresh120Hz, ui.lowEndMode) {
        mapView.setMaximumFps(if (ui.highRefresh120Hz && !ui.lowEndMode) 120 else 60)
    }

    val activeRoute = ui.routeOptions.firstOrNull { it.index == ui.selectedRouteIndex }
    val activeUnreachable = activeRoute?.evaluation?.canReach == false

    LaunchedEffect(isStyleReady, ui.routeOptions, ui.selectedRouteIndex, activeUnreachable) {
        val map = mapInstance ?: return@LaunchedEffect
        if (!isStyleReady) return@LaunchedEffect
        map.getStyle { style ->
            ensureOverlayLayers(style)
            updateRouteSources(style, ui.routeOptions, ui.selectedRouteIndex, activeUnreachable)
        }
        if (activeRoute != null && activeRoute.points.size >= 2 && !ui.followCamera) {
            val builder = LatLngBounds.Builder()
            activeRoute.points.forEach { builder.include(LatLng(it.first, it.second)) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 90), 650)
        }
    }

    LaunchedEffect(isStyleReady, loc.lat, loc.lon, loc.telemetry.bearingDeg, ui.followCamera, ui.tilt3dEnabled, ui.lowEndMode) {
        val map = mapInstance ?: return@LaunchedEffect
        if (!isStyleReady) return@LaunchedEffect
        map.getStyle { style -> updatePuckSource(style, loc.lat, loc.lon) }
        if (ui.followCamera) {
            val pos = CameraPosition.Builder()
                .target(LatLng(loc.lat, loc.lon))
                .zoom(if (ui.routeOptions.isNotEmpty()) 15.6 else 15.2)
                .tilt(if (ui.tilt3dEnabled) 46.0 else 0.0)
                .bearing(loc.telemetry.bearingDeg.toDouble())
                .build()
            val update = CameraUpdateFactory.newCameraPosition(pos)
            if (ui.lowEndMode) map.moveCamera(update) else map.easeCamera(update, 500)
        }
    }

    val telem = loc.telemetry
    val isUltra = ui.layoutMode == LayoutMode.ULTRA_COMPACT
    val sliderScale = ui.mapHudScale.coerceIn(0.50f, 1.15f)
    val hs = (sliderScale * if (isUltra) 0.78f else 1f).coerceIn(0.45f, 1.15f)
    val isDrivingFast = telem.speedKmh > 8.0

    LaunchedEffect(ui.layoutMode) {
        if (isUltra) {
            userPinnedSearch = false
            topBarExpanded = false
            showRoutePicker = false
        } else if (!isDrivingFast && ui.routeOptions.isEmpty()) {
            topBarExpanded = true
        }
    }

    LaunchedEffect(ui.destinationName, ui.routeOptions.size) {
        userPinnedSearch = false
        if (ui.routeOptions.isNotEmpty()) {
            topBarExpanded = false
            showRoutePicker = !isDrivingFast && !isUltra && ui.routeOptions.size > 1
        } else {
            showRoutePicker = false
            topBarExpanded = !isDrivingFast && !isUltra
        }
    }

    LaunchedEffect(isDrivingFast) {
        if (isDrivingFast) {
            showRoutePicker = false
            if (!userPinnedSearch && ui.searchResults.isEmpty() && ui.searchQuery.isEmpty()) {
                topBarExpanded = false
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .border(0.75.dp, HairlineBorder, RoundedCornerShape(14.dp))
    ) {
        val paneMaxWidth = this.maxWidth
        val shortHeight = this.maxHeight < 390.dp || isCompactHud
        val narrowWidth = paneMaxWidth < 500.dp
        val outerPad = (5f * hs).coerceAtLeast(2.5f).dp
        val hudGap = (4f * hs).coerceAtLeast(2f).dp
        val hudRadius = (8f * hs).coerceAtLeast(5f).dp
        val searchH = (30f * hs).coerceAtLeast(20f).dp
        val titleSp = (11f * hs).coerceAtLeast(8f).sp
        val metaSp = (9.5f * hs).coerceAtLeast(7f).sp
        val microSp = (8.5f * hs).coerceAtLeast(6.5f).sp
        val speedSp = ((if (shortHeight) 20f else 24f) * hs).coerceAtLeast(13f).sp
        val iconSm = (13f * hs).coerceAtLeast(9f).dp
        val hudPercent = (sliderScale * 100f).roundToInt()

        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.uiSettings.isCompassEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        map.prefetchZoomDelta = 0
                        map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                            override fun onMoveBegin(detector: MoveGestureDetector) {
                                onSetFollow(false)
                            }
                            override fun onMove(detector: MoveGestureDetector) {}
                            override fun onMoveEnd(detector: MoveGestureDetector) {}
                        })
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(loc.lat, loc.lon))
                            .zoom(15.2)
                            .tilt(if (ui.tilt3dEnabled) 46.0 else 0.0)
                            .bearing(telem.bearingDeg.toDouble())
                            .build()
                        mapInstance = map
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top Overlay (compact & resizable via Map GUI slider; auto-minimizes while driving)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(outerPad),
            verticalArrangement = Arrangement.spacedBy(hudGap),
        ) {
            val isExpandedTop = topBarExpanded || ui.searchResults.isNotEmpty()
            if (!isExpandedTop) {
                // Single ultra-slim floating driving capsule instead of stacked multi-row bars
                Row(
                    modifier = Modifier
                        .let { if (activeRoute != null) it.fillMaxWidth() else it }
                        .clip(RoundedCornerShape(hudRadius))
                        .background(CardBlack.copy(alpha = 0.88f))
                        .border(0.75.dp, if (activeUnreachable) AlertRed else HairlineBorder, RoundedCornerShape(hudRadius))
                        .padding(horizontal = (6f * hs).coerceAtLeast(4f).dp, vertical = (3.5f * hs).coerceAtLeast(2f).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((4f * hs).coerceAtLeast(3f).dp),
                ) {
                    if (activeRoute != null) {
                        Icon(
                            if (activeRoute.evaluation.isBestFuelSaving) CarIcons.EcoLeaf else CarIcons.Navigation,
                            contentDescription = null,
                            tint = if (activeRoute.evaluation.isBestFuelSaving) EcoGreen else SpeedCyan,
                            modifier = Modifier.size(iconSm),
                        )
                        Text(
                            text = activeRoute.nextManeuver,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = titleSp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${activeRoute.evaluation.lPer100Km} L",
                            color = MutedText,
                            fontSize = microSp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .let { if (activeRoute != null) it.widthIn(max = paneMaxWidth * 0.54f) else it }
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy((4f * hs).coerceAtLeast(3f).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                userPinnedSearch = true
                                topBarExpanded = true
                            },
                            modifier = Modifier.size((22f * hs).coerceAtLeast(16f).dp),
                        ) {
                            Icon(CarIcons.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(iconSm))
                        }
                        IconButton(
                            onClick = {
                                keyboard?.hide()
                                onOpenVoice()
                            },
                            modifier = Modifier.size((22f * hs).coerceAtLeast(16f).dp),
                        ) {
                            Icon(CarIcons.Mic, contentDescription = "Voice", tint = Color.White, modifier = Modifier.size(iconSm))
                        }
                        if (ui.routeOptions.size > 1) {
                            MapChip(s.bestFuelTrack, !ui.preferSpeedRoute, EcoGreen, Color.Black, hs) {
                                onSetPreferSpeed(false)
                            }
                            MapChip(s.fastestTrack, ui.preferSpeedRoute, SpeedCyan, Color.Black, hs) {
                                onSetPreferSpeed(true)
                            }
                            MapChip("${ui.routeOptions.size}⇄", showRoutePicker, scale = hs) {
                                showRoutePicker = !showRoutePicker
                            }
                        }
                        MapChip("HUD $hudPercent%", showHudSizeSlider, scale = hs) {
                            showHudSizeSlider = !showHudSizeSlider
                        }
                    }
                    if (ui.routeOptions.isNotEmpty()) {
                        MapChip(s.clearRoute, true, AlertRed, Color.White, hs, onClearRoute)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(hudGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(searchH)
                            .clip(RoundedCornerShape(hudRadius))
                            .background(CardBlack.copy(alpha = 0.88f))
                            .border(0.75.dp, HairlineBorder, RoundedCornerShape(hudRadius))
                            .padding(horizontal = (7f * hs).coerceAtLeast(5f).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(CarIcons.Search, contentDescription = null, tint = MutedText, modifier = Modifier.size(iconSm))
                        Spacer(Modifier.width((4f * hs).coerceAtLeast(3f).dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (ui.searchQuery.isEmpty()) {
                                Text(s.searchPlaceHint, color = MutedText, fontSize = metaSp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            BasicTextField(
                                value = ui.searchQuery,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = titleSp),
                                cursorBrush = SolidColor(Color.White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    keyboard?.hide()
                                    onSearch(ui.searchQuery)
                                }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (ui.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size((20f * hs).coerceAtLeast(15f).dp)) {
                                Icon(CarIcons.Close, contentDescription = null, tint = MutedText, modifier = Modifier.size(iconSm))
                            }
                        }
                        IconButton(onClick = {
                            keyboard?.hide()
                            onOpenVoice()
                        }, modifier = Modifier.size((22f * hs).coerceAtLeast(16f).dp)) {
                            Icon(CarIcons.Mic, contentDescription = "Voice", tint = Color.White, modifier = Modifier.size(iconSm))
                        }
                    }

                    if (ui.routeOptions.isNotEmpty()) {
                        MapChip(s.clearRoute, true, AlertRed, Color.White, hs, onClearRoute)
                    } else {
                        MapChip(s.go, true, Color.White, Color.Black, hs) {
                            keyboard?.hide()
                            onSearch(ui.searchQuery)
                        }
                    }
                    MapChip("▲", false, scale = hs) {
                        userPinnedSearch = false
                        topBarExpanded = false
                    }
                }

                // Hide extra mode chips while search dropdown is open to prevent horizontal overlap
                if (ui.searchResults.isEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy((4f * hs).coerceAtLeast(3f).dp),
                    ) {
                        MapChip(s.mapSat3d, ui.mapVisualMode == MapVisualMode.SATELLITE_3D_HYBRID, scale = hs) {
                            onSetMapMode(MapVisualMode.SATELLITE_3D_HYBRID)
                        }
                        MapChip(s.mapSatPure, ui.mapVisualMode == MapVisualMode.SATELLITE_3D_PURE, scale = hs) {
                            onSetMapMode(MapVisualMode.SATELLITE_3D_PURE)
                        }
                        MapChip(s.tilt3d, ui.tilt3dEnabled, scale = hs) { onToggleTilt3d() }
                        MapChip(s.followCam, ui.followCamera, scale = hs) { onSetFollow(!ui.followCamera) }
                        if (ui.routeOptions.size > 1) {
                            MapChip(s.bestFuelTrack, !ui.preferSpeedRoute, EcoGreen, Color.Black, hs) {
                                onSetPreferSpeed(false)
                            }
                            MapChip(s.fastestTrack, ui.preferSpeedRoute, SpeedCyan, Color.Black, hs) {
                                onSetPreferSpeed(true)
                            }
                            MapChip("${ui.routeOptions.size}⇄", showRoutePicker, scale = hs) {
                                showRoutePicker = !showRoutePicker
                            }
                        }
                        MapChip("Telem", showTelemetryPopup, scale = hs) { showTelemetryPopup = !showTelemetryPopup }
                        MapChip("HUD $hudPercent%", showHudSizeSlider, scale = hs) {
                            showHudSizeSlider = !showHudSizeSlider
                        }
                        if (!hasLocationPerm) {
                            MapChip("GPS", true, AlertRed, Color.White, hs, onRequestLocationPerm)
                        }
                    }
                }
            }

            if (showHudSizeSlider) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(hudRadius))
                        .background(CardBlack.copy(alpha = 0.94f))
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(hudRadius))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${s.mapHudScaleLabel}: $hudPercent%", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                    Slider(
                        value = sliderScale,
                        onValueChange = onSetMapHudScale,
                        valueRange = 0.50f..1.15f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
                        modifier = Modifier.weight(1f).height(26.dp),
                    )
                    MapChip("55%", sliderScale <= 0.58f, scale = 0.85f) { onSetMapHudScale(0.55f) }
                    MapChip("75%", sliderScale in 0.70f..0.80f, scale = 0.85f) { onSetMapHudScale(0.75f) }
                    MapChip("✕", false, scale = 0.85f) { showHudSizeSlider = false }
                }
            }

            if (showTelemetryPopup) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(hudRadius))
                        .background(CardBlack.copy(alpha = 0.92f))
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(hudRadius))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = (7f * hs).coerceAtLeast(5f).dp, vertical = (4f * hs).coerceAtLeast(2f).dp),
                    horizontalArrangement = Arrangement.spacedBy((8f * hs).coerceAtLeast(5f).dp),
                ) {
                    TelemItem("Avg", "${telem.avgSpeedKmh.roundToInt()} km/h", hs)
                    TelemItem("Max", "${telem.maxSpeedKmh.roundToInt()} km/h", hs)
                    TelemItem("Alt", "${telem.altitudeM.roundToInt()} m", hs)
                    TelemItem("Hdg", "${telem.bearingDeg.roundToInt()}°", hs)
                    TelemItem("Burn", "${telem.avgLPer100Km} L/100", hs)
                    TelemItem("Eco", "${telem.ecoDriveScore}%", hs)
                }
            }

            if (telem.isLowFuel || activeUnreachable) {
                val isCritical = activeUnreachable || telem.fuelLiters <= 4.0
                val bannerBg = if (isCritical) Color(0xEB2A0A0A) else Color(0xEB291D05)
                val bannerBorder = if (isCritical) AlertRed else EcoAmber
                val warnText = when {
                    activeRoute != null && !activeRoute.evaluation.canReach ->
                        if (s.isPolish) activeRoute.evaluation.reachabilityPl else activeRoute.evaluation.reachabilityEn
                    else ->
                        "${s.lowFuelBanner} · ${telem.fuelLiters} L (${telem.estimatedRangeKm.roundToInt()} km)"
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(hudRadius))
                        .background(bannerBg)
                        .border(0.75.dp, bannerBorder, RoundedCornerShape(hudRadius))
                        .clickable { showFuelQuickMenu = !showFuelQuickMenu }
                        .padding(horizontal = (7f * hs).coerceAtLeast(5f).dp, vertical = (3f * hs).coerceAtLeast(2f).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((5f * hs).coerceAtLeast(3f).dp),
                ) {
                    Icon(CarIcons.FuelPump, contentDescription = null, tint = bannerBorder, modifier = Modifier.size(iconSm))
                    Text(warnText, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = metaSp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(
                        text = s.fillTankLabel,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = microSp,
                        modifier = Modifier
                            .clip(RoundedCornerShape((5f * hs).coerceAtLeast(4f).dp))
                            .background(bannerBorder)
                            .clickable { onFillTank() }
                            .padding(horizontal = (5f * hs).coerceAtLeast(4f).dp, vertical = (2f * hs).coerceAtLeast(1.5f).dp),
                    )
                }
            }

            if (ui.searchResults.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (shortHeight) 110.dp else 160.dp)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(hudRadius))
                        .background(CardBlack.copy(alpha = 0.96f))
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(hudRadius))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ui.searchResults.forEach { place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    keyboard?.hide()
                                    onSelectPlace(place)
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(CarIcons.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(7.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(place.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (place.subtitle.isNotBlank()) {
                                    Text(place.subtitle, color = MutedText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            if (isExpandedTop && activeRoute != null && ui.searchResults.isEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(hudRadius))
                        .background(CardBlack.copy(alpha = 0.88f))
                        .border(0.75.dp, if (activeUnreachable) AlertRed else HairlineBorder, RoundedCornerShape(hudRadius))
                        .padding(horizontal = (7f * hs).coerceAtLeast(5f).dp, vertical = (3.5f * hs).coerceAtLeast(2f).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (activeRoute.evaluation.isBestFuelSaving) CarIcons.EcoLeaf else CarIcons.Navigation,
                        contentDescription = null,
                        tint = if (activeRoute.evaluation.isBestFuelSaving) EcoGreen else SpeedCyan,
                        modifier = Modifier.size(iconSm),
                    )
                    Spacer(Modifier.width((4f * hs).coerceAtLeast(3f).dp))
                    Text(
                        text = activeRoute.nextManeuver,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = titleSp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width((6f * hs).coerceAtLeast(4f).dp))
                    Text(
                        text = "${activeRoute.evaluation.lPer100Km} L/100",
                        color = MutedText,
                        fontSize = microSp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }
        }

        // Bottom Instrument Cluster (sleek wrap-content capsule scaled by Map GUI Size!)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = paneMaxWidth)
                .padding(outerPad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(hudGap),
        ) {
            if (showFuelQuickMenu) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(hudRadius))
                        .background(CardBlack.copy(alpha = 0.94f))
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(hudRadius))
                        .padding(horizontal = (7f * hs).coerceAtLeast(5f).dp, vertical = (4f * hs).coerceAtLeast(2.5f).dp),
                    horizontalArrangement = Arrangement.spacedBy((5f * hs).coerceAtLeast(3f).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${telem.fuelLiters} / ${telem.tankCapacityLiters} L", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = metaSp)
                    MapChip("-5 L", false, scale = hs) { onAdjustFuelDelta(-5.0) }
                    MapChip("+5 L", false, scale = hs) { onAdjustFuelDelta(5.0) }
                    MapChip(s.fillTankLabel, true, EcoGreen, Color.Black, hs) {
                        onFillTank()
                        showFuelQuickMenu = false
                    }
                }
            }

            if (ui.routeOptions.isNotEmpty() && showRoutePicker) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy((4f * hs).coerceAtLeast(3f).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ui.routeOptions.forEach { opt ->
                        val eval = opt.evaluation
                        val isSelected = opt.index == ui.selectedRouteIndex
                        val accentCol = when {
                            !eval.canReach -> AlertRed
                            eval.isBestFuelSaving -> EcoGreen
                            eval.isFastest -> SpeedCyan
                            else -> Color.White
                        }
                        val label = when {
                            eval.isBestFuelSaving -> s.bestFuelTrack
                            eval.isFastest -> s.fastestTrack
                            else -> "Alt ${opt.index + 1}"
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(hudRadius))
                                .background(CardBlack.copy(alpha = 0.90f))
                                .border(if (isSelected) 1.dp else 0.75.dp, if (isSelected) accentCol else HairlineBorder, RoundedCornerShape(hudRadius))
                                .clickable {
                                    onSelectRoute(opt.index)
                                    showRoutePicker = false
                                }
                                .padding(horizontal = (7f * hs).coerceAtLeast(5f).dp, vertical = (3f * hs).coerceAtLeast(2f).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((3f * hs).coerceAtLeast(2f).dp),
                        ) {
                            if (eval.isBestFuelSaving) {
                                Icon(CarIcons.EcoLeaf, contentDescription = null, tint = EcoGreen, modifier = Modifier.size((10f * hs).coerceAtLeast(8f).dp))
                            }
                            Text(
                                text = "$label ${eval.durationMin.roundToInt()}m · ${"%.1f".format(Locale.US, eval.distanceKm)}km · ${eval.estimatedLiters}L",
                                color = if (isSelected) accentCol else Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = microSp,
                                maxLines = 1,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                            )
                        }
                    }
                    MapChip("✕", false, scale = hs) { showRoutePicker = false }
                }
            }

            // Compact floating telemetry capsule (wraps content cleanly instead of blocking bottom edge)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape((10f * hs).coerceAtLeast(6f).dp))
                    .background(CardBlack.copy(alpha = 0.89f))
                    .border(0.75.dp, if (telem.isLowFuel) EcoAmber else HairlineBorder, RoundedCornerShape((10f * hs).coerceAtLeast(6f).dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = (8f * hs).coerceAtLeast(5f).dp, vertical = (4f * hs).coerceAtLeast(2.5f).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(((if (narrowWidth) 7f else 10f) * hs).coerceAtLeast(5f).dp),
            ) {
                val divH = (18f * hs).coerceAtLeast(12f).dp

                // Speedometer
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${telem.speedKmh.roundToInt()}",
                        color = if (telem.speedKmh > 130) AlertRed else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = speedSp,
                        lineHeight = speedSp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                    Text(
                        text = s.kmhUnit,
                        color = MutedText,
                        fontSize = microSp,
                        modifier = Modifier.padding(bottom = (1.5f * hs).dp),
                    )
                }

                Box(modifier = Modifier.width(0.75.dp).height(divH).background(HairlineBorder))

                // ETA
                val etaMin = telem.etaMinutes
                val arrivalClock = remember(etaMin) {
                    if (etaMin > 0) {
                        SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(System.currentTimeMillis() + etaMin * 60_000L))
                    } else "--:--"
                }
                Column {
                    Text(
                        text = if (etaMin > 0) "${etaMin}m · $arrivalClock" else s.arrived,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = metaSp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                    Text(
                        text = if (telem.remainingKm > 0.02) "${"%.1f".format(Locale.US, telem.remainingKm)} km" else "${s.ecoScore} ${telem.ecoDriveScore}%",
                        color = MutedText,
                        fontSize = microSp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }

                Box(modifier = Modifier.width(0.75.dp).height(divH).background(HairlineBorder))

                // Fuel Range
                Row(
                    modifier = Modifier.clickable { showFuelQuickMenu = !showFuelQuickMenu },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((3f * hs).coerceAtLeast(2f).dp),
                ) {
                    Icon(CarIcons.FuelPump, contentDescription = null, tint = if (telem.isLowFuel) EcoAmber else Color.White, modifier = Modifier.size(iconSm))
                    Column {
                        Text(
                            text = "${telem.estimatedRangeKm.roundToInt()} km",
                            color = if (telem.isLowFuel) EcoAmber else Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = metaSp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                        Text("${telem.fuelLiters} L", color = MutedText, fontSize = microSp, style = TextStyle(fontFeatureSettings = "tnum"))
                    }
                }

                Box(modifier = Modifier.width(0.75.dp).height(divH).background(HairlineBorder))

                // KM Counter + Reset
                Row(
                    modifier = Modifier.clickable { showTripB = !showTripB },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((4f * hs).coerceAtLeast(3f).dp),
                ) {
                    Column {
                        val tripVal = if (showTripB) telem.tripBKm else telem.tripAKm
                        val tripLabel = if (showTripB) s.tripB else s.tripA
                        Text("$tripLabel ${"%.1f".format(Locale.US, tripVal)}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = metaSp, style = TextStyle(fontFeatureSettings = "tnum"))
                        Text("${s.odometer} ${"%.1f".format(Locale.US, telem.odometerKm)}", color = MutedText, fontSize = microSp, style = TextStyle(fontFeatureSettings = "tnum"))
                    }
                    Text(
                        text = s.resetTrip,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = microSp,
                        modifier = Modifier
                            .clip(RoundedCornerShape((5f * hs).coerceAtLeast(4f).dp))
                            .background(ElevatedBlack)
                            .clickable { onResetTripA() }
                            .padding(horizontal = (5f * hs).coerceAtLeast(3f).dp, vertical = (2.5f * hs).coerceAtLeast(1.5f).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemItem(label: String, value: String, scale: Float = 1f) {
    Column {
        Text(label, color = MutedText, fontSize = (8f * scale).coerceAtLeast(6f).sp)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (9.5f * scale).coerceAtLeast(7f).sp, style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun MapChip(
    label: String,
    active: Boolean,
    activeBg: Color = Color.White,
    activeFg: Color = Color.Black,
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (active) activeFg else Color(0xFFD4D4D8),
        fontWeight = FontWeight.SemiBold,
        fontSize = (9.5f * scale).coerceAtLeast(7f).sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape((7f * scale).coerceAtLeast(4f).dp))
            .background(if (active) activeBg else CardBlack.copy(alpha = 0.88f))
            .border(0.75.dp, if (active) activeBg else HairlineBorder, RoundedCornerShape((7f * scale).coerceAtLeast(4f).dp))
            .clickable(onClick = onClick)
            .padding(horizontal = (6.5f * scale).coerceAtLeast(4f).dp, vertical = (3.5f * scale).coerceAtLeast(2f).dp),
    )
}

private fun resolveMapStyleJson(mode: MapVisualMode, enableTerrainDem: Boolean): String {
    val overlaySource = if (mode == MapVisualMode.SATELLITE_3D_HYBRID) {
        """
        ,"roads": {
          "type": "raster",
          "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}"],
          "tileSize": 256,
          "maxzoom": 17
        }
        """.trimIndent()
    } else ""

    val overlayLayer = if (mode == MapVisualMode.SATELLITE_3D_HYBRID) {
        """,{"id":"roads-layer","type":"raster","source":"roads","paint":{"raster-opacity":0.88}}"""
    } else ""

    val terrainSource = if (enableTerrainDem) {
        """
        ,"terrain_dem": {
          "type": "raster-dem",
          "tiles": ["https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"],
          "encoding": "terrarium",
          "tileSize": 256,
          "maxzoom": 11
        }
        """.trimIndent()
    } else ""

    val terrainRoot = if (enableTerrainDem) {
        """
        ,"terrain": {
          "source": "terrain_dem",
          "exaggeration": 1.25
        }
        """.trimIndent()
    } else ""

    return """
    {
      "version": 8,
      "name": "LibreCar-${mode.id}",
      "sources": {
        "sat": {
          "type": "raster",
          "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
          "tileSize": 256,
          "maxzoom": 17
        }
        $overlaySource
        $terrainSource
      }
      $terrainRoot,
      "layers": [
        {"id": "bg", "type": "background", "paint": {"background-color": "#000000"}},
        {"id": "sat-layer", "type": "raster", "source": "sat"}
        $overlayLayer
      ]
    }
    """.trimIndent()
}

private fun ensureOverlayLayers(style: Style) {
    if (style.getSource(SRC_ALT_ROUTES) == null) {
        style.addSource(GeoJsonSource(SRC_ALT_ROUTES, FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getSource(SRC_ECO_ROUTE) == null) {
        style.addSource(GeoJsonSource(SRC_ECO_ROUTE, FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getSource(SRC_SPEED_ROUTE) == null) {
        style.addSource(GeoJsonSource(SRC_SPEED_ROUTE, FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getSource(SRC_ACTIVE_ROUTE) == null) {
        style.addSource(GeoJsonSource(SRC_ACTIVE_ROUTE, FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getSource(SRC_CAR_PUCK) == null) {
        style.addSource(GeoJsonSource(SRC_CAR_PUCK, FeatureCollection.fromFeatures(emptyArray())))
    }

    if (style.getLayer(LAYER_ALT_ROUTES) == null) {
        style.addLayer(
            LineLayer(LAYER_ALT_ROUTES, SRC_ALT_ROUTES).withProperties(
                lineColor(AndroidColor.parseColor("#52525B")),
                lineWidth(4.5f),
                lineOpacity(0.75f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
    }
    if (style.getLayer(LAYER_ECO_ROUTE) == null) {
        style.addLayer(
            LineLayer(LAYER_ECO_ROUTE, SRC_ECO_ROUTE).withProperties(
                lineColor(AndroidColor.parseColor("#22C55E")),
                lineWidth(5.5f),
                lineOpacity(0.92f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
    }
    if (style.getLayer(LAYER_SPEED_ROUTE) == null) {
        style.addLayer(
            LineLayer(LAYER_SPEED_ROUTE, SRC_SPEED_ROUTE).withProperties(
                lineColor(AndroidColor.parseColor("#38BDF8")),
                lineWidth(5.5f),
                lineOpacity(0.92f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
    }
    if (style.getLayer(LAYER_ACTIVE_ROUTE) == null) {
        style.addLayer(
            LineLayer(LAYER_ACTIVE_ROUTE, SRC_ACTIVE_ROUTE).withProperties(
                lineColor(AndroidColor.WHITE),
                lineWidth(6.5f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
    }
    if (style.getLayer(LAYER_PUCK_HALO) == null) {
        style.addLayer(
            CircleLayer(LAYER_PUCK_HALO, SRC_CAR_PUCK).withProperties(
                circleRadius(13f),
                circleColor(AndroidColor.parseColor("#33FFFFFF")),
            )
        )
    }
    if (style.getLayer(LAYER_PUCK_DOT) == null) {
        style.addLayer(
            CircleLayer(LAYER_PUCK_DOT, SRC_CAR_PUCK).withProperties(
                circleRadius(6.5f),
                circleColor(AndroidColor.WHITE),
                circleStrokeWidth(2.5f),
                circleStrokeColor(AndroidColor.BLACK),
            )
        )
    }
}

private fun updateRouteSources(style: Style, routes: List<RouteOption>, selectedIdx: Int, activeUnreachable: Boolean) {
    val altFeatures = mutableListOf<Feature>()
    val ecoFeatures = mutableListOf<Feature>()
    val speedFeatures = mutableListOf<Feature>()
    val activeFeatures = mutableListOf<Feature>()
    routes.forEach { opt ->
        if (opt.points.size >= 2) {
            val line = LineString.fromLngLats(opt.points.map { Point.fromLngLat(it.second, it.first) })
            when {
                opt.index == selectedIdx -> activeFeatures.add(Feature.fromGeometry(line))
                opt.evaluation.isBestFuelSaving -> ecoFeatures.add(Feature.fromGeometry(line))
                opt.evaluation.isFastest -> speedFeatures.add(Feature.fromGeometry(line))
                else -> altFeatures.add(Feature.fromGeometry(line))
            }
        }
    }
    val activeEval = routes.firstOrNull { it.index == selectedIdx }?.evaluation
    style.getLayerAs<LineLayer>(LAYER_ACTIVE_ROUTE)?.setProperties(
        lineColor(
            when {
                activeUnreachable -> AndroidColor.parseColor("#EF4444")
                activeEval?.isBestFuelSaving == true -> AndroidColor.parseColor("#22C55E")
                activeEval?.isFastest == true -> AndroidColor.parseColor("#38BDF8")
                else -> AndroidColor.WHITE
            }
        )
    )
    style.getSourceAs<GeoJsonSource>(SRC_ALT_ROUTES)?.setGeoJson(FeatureCollection.fromFeatures(altFeatures))
    style.getSourceAs<GeoJsonSource>(SRC_ECO_ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(ecoFeatures))
    style.getSourceAs<GeoJsonSource>(SRC_SPEED_ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(speedFeatures))
    style.getSourceAs<GeoJsonSource>(SRC_ACTIVE_ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(activeFeatures))
}

private fun updatePuckSource(style: Style, lat: Double, lon: Double) {
    val fc = FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
    style.getSourceAs<GeoJsonSource>(SRC_CAR_PUCK)?.setGeoJson(fc)
}
