package com.librecar.rust.ui.screens

import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.librecar.rust.data.CarLocationState
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
private const val SRC_ACTIVE_ROUTE = "lc-active-route-src"
private const val SRC_CAR_PUCK = "lc-car-puck-src"

private const val LAYER_ALT_ROUTES = "lc-alt-routes-layer"
private const val LAYER_ECO_ROUTE = "lc-eco-route-layer"
private const val LAYER_ACTIVE_ROUTE = "lc-active-route-layer"
private const val LAYER_PUCK_HALO = "lc-puck-halo-layer"
private const val LAYER_PUCK_DOT = "lc-puck-dot-layer"

@Composable
fun MapPane(
    ui: CarUiState,
    loc: CarLocationState,
    isCompactHud: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectPlace: (PlaceSearchResult) -> Unit,
    onSelectRoute: (Int) -> Unit,
    onClearRoute: () -> Unit,
    onSetMapMode: (MapVisualMode) -> Unit,
    onToggleTilt3d: () -> Unit,
    onSetFollow: (Boolean) -> Unit,
    onToggleDemoDrive: () -> Unit,
    onResetTripA: () -> Unit,
    onAdjustFuelDelta: (Double) -> Unit,
    onFillTank: () -> Unit,
    onOpenVoice: () -> Unit,
    onRequestLocationPerm: () -> Unit,
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

    val mapView = remember(ui.lowEndMode) {
        val opts = MapLibreMapOptions.createFromAttributes(context).apply {
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

    val styleKey = "${ui.mapVisualMode.id}_${ui.terrain3dEnabled}_${ui.lowEndMode}"
    LaunchedEffect(mapInstance, styleKey) {
        val map = mapInstance ?: return@LaunchedEffect
        if (loadedStyleKey == styleKey) return@LaunchedEffect
        loadedStyleKey = styleKey
        isStyleReady = false
        val styleJson = buildMapStyleJson(ui.mapVisualMode, ui.terrain3dEnabled && !ui.lowEndMode)
        map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
            ensureOverlayLayers(style)
            isStyleReady = true
        }
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
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100), 650)
        }
    }

    LaunchedEffect(isStyleReady, loc.lat, loc.lon, loc.stats.bearingDeg, ui.followCamera, ui.tilt3dEnabled, ui.lowEndMode) {
        val map = mapInstance ?: return@LaunchedEffect
        if (!isStyleReady) return@LaunchedEffect
        map.getStyle { style -> updatePuckSource(style, loc.lat, loc.lon) }
        if (ui.followCamera) {
            val pos = CameraPosition.Builder()
                .target(LatLng(loc.lat, loc.lon))
                .zoom(if (ui.routeOptions.isNotEmpty()) 15.6 else 15.0)
                .tilt(if (ui.tilt3dEnabled) 58.0 else 0.0)
                .bearing(loc.stats.bearingDeg.toDouble())
                .build()
            val update = CameraUpdateFactory.newCameraPosition(pos)
            if (ui.lowEndMode) map.moveCamera(update) else map.easeCamera(update, 500)
        }
    }

    val stats = loc.stats

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(0.75.dp, HairlineBorder, RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.uiSettings.isCompassEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        map.prefetchZoomDelta = if (ui.lowEndMode) 0 else 4
                        map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                            override fun onMoveBegin(detector: MoveGestureDetector) {
                                onSetFollow(false)
                            }
                            override fun onMove(detector: MoveGestureDetector) {}
                            override fun onMoveEnd(detector: MoveGestureDetector) {}
                        })
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(loc.lat, loc.lon))
                            .zoom(15.0)
                            .tilt(if (ui.tilt3dEnabled) 58.0 else 0.0)
                            .bearing(stats.bearingDeg.toDouble())
                            .build()
                        mapInstance = map
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top Bar & Banners
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBlack.copy(alpha = 0.92f))
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(CarIcons.Search, contentDescription = null, tint = MutedText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (ui.searchQuery.isEmpty()) {
                            Text(s.searchPlaceHint, color = MutedText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        BasicTextField(
                            value = ui.searchQuery,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
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
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(26.dp)) {
                            Icon(CarIcons.Close, contentDescription = null, tint = MutedText, modifier = Modifier.size(14.dp))
                        }
                    }
                    IconButton(onClick = {
                        keyboard?.hide()
                        onOpenVoice()
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(CarIcons.Mic, contentDescription = "Voice", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                if (ui.routeOptions.isNotEmpty()) {
                    MapChip(
                        label = s.clearRoute,
                        active = true,
                        activeBg = AlertRed,
                        activeFg = Color.White,
                        onClick = onClearRoute,
                    )
                } else {
                    MapChip(
                        label = s.go,
                        active = true,
                        activeBg = Color.White,
                        activeFg = Color.Black,
                        onClick = {
                            keyboard?.hide()
                            onSearch(ui.searchQuery)
                        },
                    )
                }
            }

            // Map Style & Mode Controls
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MapChip(s.mapSat3d, ui.mapVisualMode == MapVisualMode.SATELLITE_3D_HYBRID) {
                    onSetMapMode(MapVisualMode.SATELLITE_3D_HYBRID)
                }
                MapChip(s.mapSatPure, ui.mapVisualMode == MapVisualMode.SATELLITE_3D_PURE) {
                    onSetMapMode(MapVisualMode.SATELLITE_3D_PURE)
                }
                MapChip(s.mapAmoled, ui.mapVisualMode == MapVisualMode.AMOLED_NIGHT) {
                    onSetMapMode(MapVisualMode.AMOLED_NIGHT)
                }
                MapChip(s.tilt3d, ui.tilt3dEnabled) { onToggleTilt3d() }
                MapChip(s.followCam, ui.followCamera) { onSetFollow(!ui.followCamera) }
                MapChip(
                    label = if (loc.isDemoSim) s.stopDemo else s.demoDrive,
                    active = loc.isDemoSim,
                    activeBg = EcoAmber,
                    activeFg = Color.Black,
                    onClick = onToggleDemoDrive,
                )
                if (!hasLocationPerm) {
                    MapChip("GPS", true, AlertRed, Color.White, onRequestLocationPerm)
                }
            }

            // Low Fuel Warning Banner OR Unreachable Destination Alert
            if (stats.isLowFuel || activeUnreachable) {
                val isCritical = activeUnreachable || stats.fuelLiters <= 4.0
                val bannerBg = if (isCritical) Color(0xEB2A0A0A) else Color(0xEB291D05)
                val bannerBorder = if (isCritical) AlertRed else EcoAmber
                val warnText = when {
                    activeRoute != null && !activeRoute.evaluation.canReach ->
                        if (s.isPolish) activeRoute.evaluation.reachabilityPl else activeRoute.evaluation.reachabilityEn
                    else ->
                        "${s.lowFuelBanner} · ${stats.fuelLiters} L (${stats.estimatedRangeKm.roundToInt()} km)"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bannerBg)
                        .border(0.75.dp, bannerBorder, RoundedCornerShape(12.dp))
                        .clickable { showFuelQuickMenu = !showFuelQuickMenu }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(CarIcons.FuelPump, contentDescription = null, tint = bannerBorder, modifier = Modifier.size(16.dp))
                    Text(warnText, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(
                        text = s.fillTankLabel,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bannerBorder)
                            .clickable { onFillTank() }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Search Results Dropdown
            if (ui.searchResults.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBlack.copy(alpha = 0.96f))
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ui.searchResults.forEach { place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    keyboard?.hide()
                                    onSelectPlace(place)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(CarIcons.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(place.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (place.subtitle.isNotBlank()) {
                                    Text(place.subtitle, color = MutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // Active Turn Maneuver Strip
            if (activeRoute != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBlack.copy(alpha = 0.92f))
                        .border(0.75.dp, if (activeUnreachable) AlertRed else HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (activeRoute.evaluation.isBestFuelSaving) CarIcons.EcoLeaf else CarIcons.Navigation,
                        contentDescription = null,
                        tint = if (activeRoute.evaluation.isBestFuelSaving) EcoGreen else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = activeRoute.nextManeuver,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${activeRoute.evaluation.lPer100Km} L/100",
                        color = MutedText,
                        fontSize = 12.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }
        }

        // Bottom Instrument Cluster + Eco Route Options
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showFuelQuickMenu) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBlack.copy(alpha = 0.96f))
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${stats.fuelLiters} / ${stats.tankCapacityLiters} L", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    MapChip("-5 L", false) { onAdjustFuelDelta(-5.0) }
                    MapChip("+5 L", false) { onAdjustFuelDelta(5.0) }
                    MapChip(s.fillTankLabel, true, EcoGreen, Color.Black) {
                        onFillTank()
                        showFuelQuickMenu = false
                    }
                }
            }

            if (ui.routeOptions.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.routeOptions.forEach { opt ->
                        val eval = opt.evaluation
                        val isSelected = opt.index == ui.selectedRouteIndex
                        val borderCol = when {
                            !eval.canReach -> AlertRed
                            eval.isBestFuelSaving && isSelected -> EcoGreen
                            isSelected -> Color.White
                            else -> HairlineBorder
                        }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardBlack.copy(alpha = 0.93f))
                                .border(if (isSelected) 1.25.dp else 0.75.dp, borderCol, RoundedCornerShape(12.dp))
                                .clickable { onSelectRoute(opt.index) }
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (eval.isBestFuelSaving) {
                                    Icon(CarIcons.EcoLeaf, contentDescription = null, tint = EcoGreen, modifier = Modifier.size(13.dp))
                                }
                                Text(
                                    text = if (eval.isBestFuelSaving) s.bestFuelTrack else if (eval.isFastest) s.fastestTrack else "Alt ${opt.index + 1}",
                                    color = if (eval.isBestFuelSaving) EcoGreen else Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    text = "· ${eval.durationMin.roundToInt()} min · ${"%.1f".format(Locale.US, eval.distanceKm)} km",
                                    color = MutedText,
                                    fontSize = 11.sp,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                )
                            }
                            Text(
                                text = "${eval.estimatedLiters} L (${eval.lPer100Km} L/100km) · " +
                                    (if (s.isPolish) eval.reachabilityPl else eval.reachabilityEn),
                                color = if (!eval.canReach) AlertRed else if (eval.isBestFuelSaving) Color(0xFFD1FAE5) else MutedText,
                                fontSize = 10.sp,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                            )
                        }
                    }
                }
            }

            // Minimalist Speedometer + ETA + KM Counter + Fuel Range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBlack.copy(alpha = 0.92f))
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${stats.speedKmh.roundToInt()}",
                            color = if (stats.speedKmh > 130) AlertRed else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isCompactHud) 28.sp else 36.sp,
                            lineHeight = 36.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            text = if (loc.isDemoSim) "${s.kmhUnit} · DEMO" else s.kmhUnit,
                            color = MutedText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Box(modifier = Modifier.width(0.75.dp).height(38.dp).background(HairlineBorder))

                    Column {
                        val etaMin = stats.etaMinutes
                        val arrivalClock = remember(etaMin) {
                            if (etaMin > 0) {
                                SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(System.currentTimeMillis() + etaMin * 60_000L))
                            } else "--:--"
                        }
                        Text(
                            text = if (etaMin > 0) "$etaMin min · $arrivalClock" else s.arrived,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            text = if (stats.remainingKm > 0.02) {
                                "${"%.1f".format(Locale.US, stats.remainingKm)} km · ${s.etaLabel}"
                            } else {
                                "${s.ecoScore} ${stats.ecoDriveScore}%"
                            },
                            color = MutedText,
                            fontSize = 11.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                    }
                }

                // Right Cluster: Fuel Range + KM Counter
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardBlack.copy(alpha = 0.92f))
                            .border(0.75.dp, if (stats.isLowFuel) EcoAmber else HairlineBorder, RoundedCornerShape(14.dp))
                            .clickable { showFuelQuickMenu = !showFuelQuickMenu }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            CarIcons.FuelPump,
                            contentDescription = null,
                            tint = if (stats.isLowFuel) EcoAmber else Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Column {
                            Text(
                                text = "${stats.estimatedRangeKm.roundToInt()} km",
                                color = if (stats.isLowFuel) EcoAmber else Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                            )
                            Text(
                                text = "${stats.fuelLiters} L",
                                color = MutedText,
                                fontSize = 10.sp,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardBlack.copy(alpha = 0.92f))
                            .border(0.75.dp, HairlineBorder, RoundedCornerShape(14.dp))
                            .clickable { showTripB = !showTripB }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column {
                            val tripVal = if (showTripB) stats.tripBKm else stats.tripAKm
                            val tripLabel = if (showTripB) s.tripB else s.tripA
                            Text(
                                text = "$tripLabel ${"%.1f".format(Locale.US, tripVal)} km",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                            )
                            Text(
                                text = "${s.odometer} ${"%.1f".format(Locale.US, stats.odometerKm)} km",
                                color = MutedText,
                                fontSize = 10.sp,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                            )
                        }
                        Text(
                            text = s.resetTrip,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ElevatedBlack)
                                .clickable { onResetTripA() }
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapChip(
    label: String,
    active: Boolean,
    activeBg: Color = Color.White,
    activeFg: Color = Color.Black,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (active) activeFg else Color(0xFFD4D4D8),
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) activeBg else CardBlack.copy(alpha = 0.88f))
            .border(0.75.dp, if (active) activeBg else HairlineBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private fun buildMapStyleJson(mode: MapVisualMode, enableTerrainDem: Boolean): String {
    val (baseSource, baseLayer, overlaySource, overlayLayer) = when (mode) {
        MapVisualMode.SATELLITE_3D_HYBRID -> listOf(
            """
            "sat": {
              "type": "raster",
              "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
              "tileSize": 256,
              "maxzoom": 18
            }
            """.trimIndent(),
            """{"id":"sat-layer","type":"raster","source":"sat"}""",
            """
            ,"labels": {
              "type": "raster",
              "tiles": ["https://basemaps.cartocdn.com/dark_only_labels/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "maxzoom": 19
            }
            """.trimIndent(),
            """,{"id":"labels-layer","type":"raster","source":"labels","paint":{"raster-opacity":0.92}}""",
        )
        MapVisualMode.SATELLITE_3D_PURE -> listOf(
            """
            "sat": {
              "type": "raster",
              "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
              "tileSize": 256,
              "maxzoom": 18
            }
            """.trimIndent(),
            """{"id":"sat-layer","type":"raster","source":"sat"}""",
            "",
            "",
        )
        MapVisualMode.AMOLED_NIGHT -> listOf(
            """
            "dark": {
              "type": "raster",
              "tiles": ["https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "maxzoom": 19
            }
            """.trimIndent(),
            """{"id":"dark-layer","type":"raster","source":"dark"}""",
            "",
            "",
        )
    }

    val terrainSource = if (enableTerrainDem) {
        """
        ,"terrain_dem": {
          "type": "raster-dem",
          "tiles": ["https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{y}/{x}.png"],
          "encoding": "terrarium",
          "tileSize": 256,
          "maxzoom": 14
        }
        """.trimIndent()
    } else ""

    val terrainRoot = if (enableTerrainDem) {
        """
        ,"terrain": {
          "source": "terrain_dem",
          "exaggeration": 1.3
        }
        """.trimIndent()
    } else ""

    return """
    {
      "version": 8,
      "name": "LibreCar-${mode.id}",
      "sources": {
        $baseSource
        $overlaySource
        $terrainSource
      }
      $terrainRoot,
      "layers": [
        {"id": "bg", "type": "background", "paint": {"background-color": "#000000"}},
        $baseLayer
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
    val activeFeatures = mutableListOf<Feature>()
    routes.forEach { opt ->
        if (opt.points.size >= 2) {
            val line = LineString.fromLngLats(opt.points.map { Point.fromLngLat(it.second, it.first) })
            when {
                opt.index == selectedIdx -> activeFeatures.add(Feature.fromGeometry(line))
                opt.evaluation.isBestFuelSaving -> ecoFeatures.add(Feature.fromGeometry(line))
                else -> altFeatures.add(Feature.fromGeometry(line))
            }
        }
    }
    style.getLayerAs<LineLayer>(LAYER_ACTIVE_ROUTE)?.setProperties(
        lineColor(
            when {
                activeUnreachable -> AndroidColor.parseColor("#EF4444")
                routes.firstOrNull { it.index == selectedIdx }?.evaluation?.isBestFuelSaving == true ->
                    AndroidColor.parseColor("#22C55E")
                else -> AndroidColor.WHITE
            }
        )
    )
    style.getSourceAs<GeoJsonSource>(SRC_ALT_ROUTES)?.setGeoJson(FeatureCollection.fromFeatures(altFeatures))
    style.getSourceAs<GeoJsonSource>(SRC_ECO_ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(ecoFeatures))
    style.getSourceAs<GeoJsonSource>(SRC_ACTIVE_ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(activeFeatures))
}

private fun updatePuckSource(style: Style, lat: Double, lon: Double) {
    val fc = FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
    style.getSourceAs<GeoJsonSource>(SRC_CAR_PUCK)?.setGeoJson(fc)
}
