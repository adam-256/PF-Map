package com.pfmap.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pfmap.app.data.Club
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Free, key-less vector tiles (OpenFreeMap, OpenStreetMap data) with a MapLibre demo fallback. */
private const val STYLE_PRIMARY = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_FALLBACK = "https://demotiles.maplibre.org/style.json"

private const val SRC = "clubs"
private const val L_CLUSTERS = "club-clusters"
private const val L_COUNT = "club-cluster-count"
private const val L_POINTS = "club-points"
private const val L_SELECTED = "club-selected"
private const val L_LABELS = "club-labels"

private val PURPLE = Color.parseColor("#6A2C91")
private val PURPLE_DARK = Color.parseColor("#3E1260")
private val YELLOW = Color.parseColor("#FFD23F")

sealed interface CameraCommand {
    /** zoom < 0 keeps the current zoom. */
    data class FlyTo(val lat: Double, val lng: Double, val zoom: Double, val bottomPaddingPx: Int = 0) : CameraCommand
}

@SuppressLint("MissingPermission")
@Composable
fun ClubMap(
    clubs: List<Club>,
    selectedId: String?,
    locationEnabled: Boolean,
    commands: Flow<CameraCommand>,
    onClubTapped: (String) -> Unit,
    onMapTapped: () -> Unit,
    onUserLocation: (Location) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var fontStack by remember { mutableStateOf(arrayOf("Noto Sans Bold")) }
    val tapped by rememberUpdatedState(onClubTapped)
    val tappedEmpty by rememberUpdatedState(onMapTapped)
    val userLoc by rememberUpdatedState(onUserLocation)

    // Forward the host lifecycle to the MapView.
    DisposableEffect(lifecycle) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> { destroyed = true; mapView.onDestroy() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!destroyed) mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.cameraPosition = CameraPosition.Builder().target(LatLng(39.5, -98.35)).zoom(3.2).build()
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false

            fun load(uri: String) {
                fontStack = if (uri == STYLE_PRIMARY) arrayOf("Noto Sans Bold") else arrayOf("Open Sans Semibold")
                m.setStyle(Style.Builder().fromUri(uri)) { s ->
                    addClubLayers(s, fontStack)
                    style = s
                }
            }
            var fellBack = false
            mapView.addOnDidFailLoadingMapListener {
                if (!fellBack) { fellBack = true; load(STYLE_FALLBACK) }
            }
            load(STYLE_PRIMARY)

            m.addOnMapClickListener { latLng ->
                val p: PointF = m.projection.toScreenLocation(latLng)
                val r = 14f * density.density
                val box = RectF(p.x - r, p.y - r, p.x + r, p.y + r)
                val point = m.queryRenderedFeatures(box, L_SELECTED, L_POINTS).firstOrNull()
                if (point != null) {
                    point.getStringProperty("id")?.let { tapped(it) }
                    return@addOnMapClickListener true
                }
                val cluster = m.queryRenderedFeatures(box, L_CLUSTERS).firstOrNull()
                if (cluster != null) {
                    val src = m.style?.getSourceAs<GeoJsonSource>(SRC)
                    val target = (cluster.geometry() as? Point)?.let { LatLng(it.latitude(), it.longitude()) } ?: latLng
                    val zoom = runCatching { src?.getClusterExpansionZoom(cluster)?.toDouble() }.getOrNull()
                        ?: (m.cameraPosition.zoom + 2)
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoom + 0.3), 500)
                    return@addOnMapClickListener true
                }
                tappedEmpty()
                false
            }
        }
    }

    // Club data -> GeoJSON source.
    LaunchedEffect(style, clubs) {
        val s = style ?: return@LaunchedEffect
        val features = clubs.map { c ->
            Feature.fromGeometry(Point.fromLngLat(c.lng, c.lat)).apply {
                addStringProperty("id", c.id)
                addStringProperty("name", c.name.removePrefix("Planet Fitness").trim().ifEmpty { c.city })
            }
        }
        s.getSourceAs<GeoJsonSource>(SRC)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Highlight the selected club.
    LaunchedEffect(style, selectedId) {
        val s = style ?: return@LaunchedEffect
        (s.getLayer(L_SELECTED) as? CircleLayer)?.setFilter(eq(get("id"), literal(selectedId ?: "\u0000")))
    }

    // "My location" puck.
    LaunchedEffect(style, locationEnabled) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        if (!locationEnabled) return@LaunchedEffect
        val lc = m.locationComponent
        if (!lc.isLocationComponentActivated) {
            lc.activateLocationComponent(LocationComponentActivationOptions.builder(context, s).build())
        }
        lc.isLocationComponentEnabled = true
        lc.cameraMode = CameraMode.NONE
        lc.renderMode = RenderMode.COMPASS
        // Report fixes: quickly until the first one arrives, then every 20 s for distance sorting.
        var last: Location? = null
        while (true) {
            val loc = lc.lastKnownLocation
            if (loc != null && (last == null || loc.distanceTo(last) > 50f)) {
                last = loc
                userLoc(loc)
            }
            delay(if (last == null) 1000 else 20_000)
        }
    }

    // Camera commands from the screen (search result, my location, selection).
    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        commands.collect { cmd ->
            when (cmd) {
                is CameraCommand.FlyTo -> {
                    val pos = CameraPosition.Builder()
                        .target(LatLng(cmd.lat, cmd.lng))
                        .zoom(if (cmd.zoom < 0) m.cameraPosition.zoom else cmd.zoom)
                        .padding(0.0, 0.0, 0.0, cmd.bottomPaddingPx.toDouble())
                        .build()
                    m.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 700)
                }
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addClubLayers(style: Style, font: Array<String>) {
    style.addSource(
        GeoJsonSource(
            SRC,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withCluster(true).withClusterMaxZoom(10).withClusterRadius(48),
        ),
    )
    style.addLayer(
        CircleLayer(L_CLUSTERS, SRC).withFilter(has("point_count")).withProperties(
            circleColor(step(get("point_count"), color(PURPLE), stop(25, color(Color.parseColor("#55207A"))), stop(150, color(PURPLE_DARK)))),
            circleRadius(step(get("point_count"), literal(15f), stop(25, 19f), stop(150, 25f))),
            circleStrokeColor(color(YELLOW)),
            circleStrokeWidth(2.5f),
        ),
    )
    style.addLayer(
        SymbolLayer(L_COUNT, SRC).withFilter(has("point_count")).withProperties(
            textField(get("point_count_abbreviated")),
            textFont(font),
            textSize(13f),
            textColor(color(Color.WHITE)),
            textAllowOverlap(true),
            textIgnorePlacement(true),
        ),
    )
    style.addLayer(
        CircleLayer(L_POINTS, SRC).withFilter(not(has("point_count"))).withProperties(
            circleColor(color(PURPLE)),
            circleRadius(8f),
            circleStrokeColor(color(YELLOW)),
            circleStrokeWidth(2.5f),
        ),
    )
    style.addLayer(
        SymbolLayer(L_LABELS, SRC).withFilter(not(has("point_count"))).withProperties(
            textField(get("name")),
            textFont(font),
            textSize(12f),
            textColor(color(PURPLE_DARK)),
            textHaloColor(color(Color.WHITE)),
            textHaloWidth(1.5f),
            textAnchor("top"),
            textOffset(arrayOf(0f, 1.0f)),
            textOptional(true),
        ).also { it.minZoom = 11f },
    )
    style.addLayer(
        CircleLayer(L_SELECTED, SRC).withFilter(eq(get("id"), literal("\u0000"))).withProperties(
            circleColor(color(YELLOW)),
            circleRadius(12f),
            circleStrokeColor(color(PURPLE_DARK)),
            circleStrokeWidth(4f),
        ),
    )
}
