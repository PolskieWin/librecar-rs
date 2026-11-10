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
import androidx.compose.foundation.shape.CircleShape
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
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.EcoGreen
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
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

private const val SRC_ALT_ROUTES = "librecar-alt-routes-src"
private const val SRC_ACTIVE_ROUTE = "librecar-active-route-src"
private const val SRC_CAR_PUCK = "librecar-car-puck-src"

private const val LAYER_ALT_ROUTES = "librecar-alt-routes-layer"
private const val LAYER_ACTIVE_GLOW = "librecar-active-glow-layer"
private const val LAYER_ACTIVE_ROUTE = "librecar-active-route-layer"
private const val LAYER_PUCK_HALO = "librecar-puck-halo-layer"
private const val LAYER_PUCK_DOT = "librecar-puck-dot-layer"

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
    onToggleFollow: () -> Unit,
    onToggleDemoDrive: () -> Unit,
    onResetTripA: () -> Unit,
    onOpenVoice: () -> Unit,
    onRequestLocationPerm: () -> Unit,
    hasLocationPerm: Boolean,
    modifier: Modifier = Modifier,
) {
    val s = LocalCarStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyleKey by remember { mutableStateOf("") }
    var showTripB by remember { mutableStateOf(false) }

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

    // Apply style updates whenever map mode or 3D terrain setting changes
    val styleKey = "${ui.mapVisualMode.id}_${ui.terrain3dEnabled}_${ui.lowEndMode}"
    LaunchedEffect(mapInstance, styleKey) {
        val map = mapInstance ?: return@LaunchedEffect
        if (loadedStyleKey == styleKey) return@LaunchedEffect
        loadedStyleKey = styleKey
        val styleJson = buildMapStyleJson(ui.mapVisualMode, ui.terrain3dEnabled && !ui.lowEndMode)
        map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
            ensureOverlayLayers(style)
            updateRouteSources(style, ui.routeOptions, ui.selectedRouteIndex)
            updatePuckSource(style, loc.lat, loc.lon)
        }
    }

    // Update routes on style when route list or selection changes
    LaunchedEffect(mapInstance, ui.routeOptions, ui.selectedRouteIndex) {
        val map = mapInstance ?: return@LaunchedEffect
        map.getStyle { style ->
            ensureOverlayLayers(style)
            updateRouteSources(style, ui.routeOptions, ui.selectedRouteIndex)
        }
        val selected = ui.routeOptions.firstOrNull { it.index == ui.selectedRouteIndex }
        if (selected != null && selected.points.size >= 2 && !ui.followCamera) {
            val builder = LatLngBounds.Builder()
            selected.points.forEach { builder.include(LatLng(it.first, it.second)) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 110), 800)
        }
    }

    // Update GPS puck & 3D follow camera
    LaunchedEffect(mapInstance, loc.lat, loc.lon, loc.telemetry.bearingDeg, ui.followCamera, ui.tilt3dEnabled) {
        val map = mapInstance ?: return@LaunchedEffect
        map.getStyle { style ->
            updatePuckSource(style, loc.lat, loc.lon)
        }
        if (ui.followCamera) {
            val pos = CameraPosition.Builder()
                .target(LatLng(loc.lat, loc.lon))
                .zoom(if (ui.routeOptions.isNotEmpty()) 15.6 else 15.0)
                .tilt(if (ui.tilt3dEnabled) 60.0 else 0.0)
                .bearing(loc.telemetry.bearingDeg.toDouble())
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 650)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
    ) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        mapInstance = map
                        map.uiSettings.isCompassEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        val initialKey = "${ui.mapVisualMode.id}_${ui.terrain3dEnabled}_${ui.lowEndMode}"
                        loadedStyleKey = initialKey
                        val json = buildMapStyleJson(ui.mapVisualMode, ui.terrain3dEnabled && !ui.lowEndMode)
                        map.setStyle(Style.Builder().fromJson(json)) { style ->
                            ensureOverlayLayers(style)
                            updateRouteSources(style, ui.routeOptions, ui.selectedRouteIndex)
                            updatePuckSource(style, loc.lat, loc.lon)
                        }
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(loc.lat, loc.lon))
                            .zoom(15.0)
                            .tilt(if (ui.tilt3dEnabled) 60.0 else 0.0)
                            .bearing(loc.telemetry.bearingDeg.toDouble())
                            .build()
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top Overlay: Search Bar + Mode Chips + Active Maneuver Banner
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
                // Search Box
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardBlack.copy(alpha = 0.90f))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(22.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = CarIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (ui.searchQuery.isEmpty()) {
                            Text(
                                text = s.searchPlaceHint,
                                color = Color(0xFF8A8A96),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = ui.searchQuery,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch(ui.searchQuery) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (ui.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                            Icon(CarIcons.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onOpenVoice, modifier = Modifier.size(30.dp)) {
                        Icon(CarIcons.Mic, contentDescription = "Voice", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }

                if (ui.routeOptions.isNotEmpty()) {
                    PillChip(
                        label = s.clearRoute,
                        selected = true,
                        selectedColor = Color(0xFFFF5252),
                        onClick = onClearRoute,
                    )
                } else {
                    PillChip(
                        label = s.go,
                        selected = true,
                        selectedColor = MaterialTheme.colorScheme.primary,
                        onClick = { onSearch(ui.searchQuery) },
                    )
                }
            }

            // Map Style & 3D Controls Row
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PillChip(
                    label = s.mapSat3d,
                    selected = ui.mapVisualMode == MapVisualMode.SATELLITE_3D_HYBRID,
                    selectedColor = MaterialTheme.colorScheme.primary,
                    onClick = { onSetMapMode(MapVisualMode.SATELLITE_3D_HYBRID) },
                )
                PillChip(
                    label = s.mapSatPure,
                    selected = ui.mapVisualMode == MapVisualMode.SATELLITE_3D_PURE,
                    selectedColor = MaterialTheme.colorScheme.primary,
                    onClick = { onSetMapMode(MapVisualMode.SATELLITE_3D_PURE) },
                )
                PillChip(
                    label = s.mapAmoled,
                    selected = ui.mapVisualMode == MapVisualMode.AMOLED_NIGHT,
                    selectedColor = MaterialTheme.colorScheme.primary,
                    onClick = { onSetMapMode(MapVisualMode.AMOLED_NIGHT) },
                )
                PillChip(
                    label = s.tilt3d,
                    selected = ui.tilt3dEnabled,
                    selectedColor = MaterialTheme.colorScheme.secondary,
                    onClick = onToggleTilt3d,
                )
                PillChip(
                    label = s.followCam,
                    selected = ui.followCamera,
                    selectedColor = MaterialTheme.colorScheme.secondary,
                    onClick = onToggleFollow,
                )
                PillChip(
                    label = if (loc.isDemoSim) s.stopDemo else s.demoDrive,
                    selected = loc.isDemoSim,
                    selectedColor = Color(0xFFFFAB40),
                    onClick = onToggleDemoDrive,
                )
                if (!hasLocationPerm) {
                    PillChip(
                        label = "GPS",
                        selected = true,
                        selectedColor = Color(0xFFFF5252),
                        onClick = onRequestLocationPerm,
                    )
                }
            }

            // Search Dropdown Results
            if (ui.searchResults.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBlack.copy(alpha = 0.96f))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ui.searchResults.forEach { place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectPlace(place) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(CarIcons.Navigation, contentDescription = null, tint = EcoGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(place.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (place.subtitle.isNotBlank()) {
                                    Text(place.subtitle, color = Color(0xFF9CA3AF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // Active Next Maneuver Banner
            val activeRoute = ui.routeOptions.firstOrNull { it.index == ui.selectedRouteIndex }
            if (activeRoute != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE6051E12))
                        .border(1.dp, EcoGreen.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(CarIcons.EcoLeaf, contentDescription = null, tint = EcoGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = activeRoute.nextManeuver,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${activeRoute.evaluation.lPer100Km} L/100",
                        color = EcoGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // Bottom HUD: Speedometer (km/h) + ETA + Trip km counter + Eco Route Selector
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Eco vs Fastest Route Cards (when route is active)
            if (ui.routeOptions.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.routeOptions.forEach { opt ->
                        val eval = opt.evaluation
                        val isSelected = opt.index == ui.selectedRouteIndex
                        val borderCol = when {
                            eval.isBestFuelSaving && isSelected -> EcoGreen
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> HairlineBorder
                        }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardBlack.copy(alpha = 0.92f))
                                .border(if (isSelected) 1.5.dp else 1.dp, borderCol, RoundedCornerShape(14.dp))
                                .clickable { onSelectRoute(opt.index) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (eval.isBestFuelSaving) {
                                    Icon(CarIcons.EcoLeaf, contentDescription = null, tint = EcoGreen, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = if (eval.isBestFuelSaving) s.bestFuelTrack else if (eval.isFastest) s.fastestTrack else "Alt ${opt.index + 1}",
                                    color = if (eval.isBestFuelSaving) EcoGreen else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                            }
                            Text(
                                text = "${eval.durationMin.roundToInt()} min • ${"%.1f".format(Locale.US, eval.distanceKm)} km • ${eval.lPer100Km} L/100km",
                                color = Color(0xFFD1D5DB),
                                fontSize = 11.sp,
                            )
                            Text(
                                text = if (s.isPolish) eval.summaryPl else eval.summaryEn,
                                color = if (eval.isBestFuelSaving) EcoGreen else Color(0xFF9CA3AF),
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // Telemetry Cluster: km/h + Time to Drive (ETA) + KM Counter (Trip A/B + Odo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                // Speedometer + ETA Card
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardBlack.copy(alpha = 0.92f))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${loc.telemetry.speedKmh.roundToInt()}",
                            color = if (loc.telemetry.speedKmh > 125) Color(0xFFFF5252) else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = if (isCompactHud) 30.sp else 38.sp,
                            lineHeight = 38.sp,
                        )
                        Text(
                            text = if (loc.isDemoSim) "${s.kmhUnit} • DEMO" else s.kmhUnit,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(42.dp).background(HairlineBorder))

                    Column {
                        val etaMin = loc.telemetry.etaMinutes
                        val arrivalClock = remember(etaMin) {
                            if (etaMin > 0) {
                                SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(System.currentTimeMillis() + etaMin * 60_000L))
                            } else "--:--"
                        }
                        Text(
                            text = if (etaMin > 0) "$etaMin min ($arrivalClock)" else s.arrived,
                            color = if (etaMin > 0) EcoGreen else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = if (loc.telemetry.remainingKm > 0.02) {
                                "${"%.1f".format(Locale.US, loc.telemetry.remainingKm)} km • ${s.etaLabel}"
                            } else {
                                "${s.ecoScore} ${loc.telemetry.ecoDriveScore}%"
                            },
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                        )
                    }
                }

                // KM Counter Pill (Tap to toggle Trip A / Trip B, Reset button)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardBlack.copy(alpha = 0.92f))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
                        .clickable { showTripB = !showTripB }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column {
                        val tripVal = if (showTripB) loc.telemetry.tripBKm else loc.telemetry.tripAKm
                        val tripLabel = if (showTripB) s.tripB else s.tripA
                        Text(
                            text = "$tripLabel: ${"%.2f".format(Locale.US, tripVal)} km",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = "${s.odometer}: ${"%.1f".format(Locale.US, loc.telemetry.odometerKm)} km",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        text = s.resetTrip,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .clickable { onResetTripA() }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PillChip(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) Color.Black else Color(0xFFE5E7EB),
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) selectedColor else CardBlack.copy(alpha = 0.88f))
            .border(1.dp, if (selected) selectedColor else HairlineBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
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
          "exaggeration": 1.35
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
    if (style.getSource(SRC_ACTIVE_ROUTE) == null) {
        style.addSource(GeoJsonSource(SRC_ACTIVE_ROUTE, FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getSource(SRC_CAR_PUCK) == null) {
        style.addSource(GeoJsonSource(SRC_CAR_PUCK, FeatureCollection.fromFeatures(emptyArray())))
    }

    if (style.getLayer(LAYER_ALT_ROUTES) == null) {
        style.addLayer(
            LineLayer(LAYER_ALT_ROUTES, SRC_ALT_ROUTES).withProperties(
                lineColor(AndroidColor.parseColor("#64748B")),
                lineWidth(5f),
                lineOpacity(0.75f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
    }
    if (style.getLayer(LAYER_ACTIVE_GLOW) == null) {
        style.addLayer(
            LineLayer(LAYER_ACTIVE_GLOW, SRC_ACTIVE_ROUTE).withProperties(
                lineColor(AndroidColor.parseColor("#00E676")),
                lineWidth(12f),
                lineOpacity(0.35f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
    }
    if (style.getLayer(LAYER_ACTIVE_ROUTE) == null) {
        style.addLayer(
            LineLayer(LAYER_ACTIVE_ROUTE, SRC_ACTIVE_ROUTE).withProperties(
                lineColor(AndroidColor.parseColor("#00E676")),
                lineWidth(6.5f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
    }
    if (style.getLayer(LAYER_PUCK_HALO) == null) {
        style.addLayer(
            CircleLayer(LAYER_PUCK_HALO, SRC_CAR_PUCK).withProperties(
                circleRadius(14f),
                circleColor(AndroidColor.parseColor("#4400E676")),
            )
        )
    }
    if (style.getLayer(LAYER_PUCK_DOT) == null) {
        style.addLayer(
            CircleLayer(LAYER_PUCK_DOT, SRC_CAR_PUCK).withProperties(
                circleRadius(7f),
                circleColor(AndroidColor.parseColor("#00E676")),
                circleStrokeWidth(2.5f),
                circleStrokeColor(AndroidColor.WHITE),
            )
        )
    }
}

private fun updateRouteSources(style: Style, routes: List<RouteOption>, selectedIdx: Int) {
    val altFeatures = mutableListOf<Feature>()
    val activeFeatures = mutableListOf<Feature>()
    routes.forEach { opt ->
        if (opt.points.size >= 2) {
            val line = LineString.fromLngLats(opt.points.map { Point.fromLngLat(it.second, it.first) })
            if (opt.index == selectedIdx) {
                activeFeatures.add(Feature.fromGeometry(line))
            } else {
                altFeatures.add(Feature.fromGeometry(line))
            }
        }
    }
    style.getSourceAs<GeoJsonSource>(SRC_ALT_ROUTES)?.setGeoJson(FeatureCollection.fromFeatures(altFeatures))
    style.getSourceAs<GeoJsonSource>(SRC_ACTIVE_ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(activeFeatures))
}

private fun updatePuckSource(style: Style, lat: Double, lon: Double) {
    val fc = FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
    style.getSourceAs<GeoJsonSource>(SRC_CAR_PUCK)?.setGeoJson(fc)
}
