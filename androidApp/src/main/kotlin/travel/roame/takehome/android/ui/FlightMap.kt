package travel.roame.takehome.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import travel.roame.takehome.domain.DestinationMarker
import travel.roame.takehome.domain.Formatting
import travel.roame.takehome.model.Flight
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Remembers the camera across list/map switches (the MapView itself is recreated). */
class MapCameraMemory {
    var center: GeoPoint? = null
    var zoom: Double? = null
}

/**
 * OpenStreetMap-based flight map. One marker per destination (count + lowest points); tapping a marker
 * opens a sheet to browse its flights. The selected flight's route is drawn as a great-circle arc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightMap(
    markers: List<DestinationMarker>,
    selected: Flight?,
    savedIds: Set<String>,
    camera: MapCameraMemory,
    onSelectFlight: (Flight) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = MarkerColors(
        accent = MaterialTheme.colorScheme.primary.toArgb(),
        onAccent = MaterialTheme.colorScheme.onPrimary.toArgb(),
        surface = MaterialTheme.colorScheme.surfaceContainerLowest.toArgb(),
        onSurface = MaterialTheme.colorScheme.onSurface.toArgb(),
    )
    var browsingCode by remember { mutableStateOf<String?>(null) }

    val mapView = remember { createMapView(context, camera) }
    val renderer = remember(mapView) { FlightMapRenderer(mapView) { code -> browsingCode = code } }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            camera.center = mapView.mapCenter as? GeoPoint ?: GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude)
            camera.zoom = mapView.zoomLevelDouble
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { renderer.render(markers, selected, colors) },
    )

    val browsing = browsingCode?.let { code -> markers.firstOrNull { it.airportCode == code } }
    if (browsingCode != null) {
        ModalBottomSheet(
            onDismissRequest = { browsingCode = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        ) {
            if (browsing == null) {
                Text("No flights", modifier = Modifier.padding(24.dp))
            } else {
                Column(Modifier.navigationBarsPadding()) {
                    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                        Text("${browsing.city} (${browsing.airportCode})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${Formatting.flightCount(browsing.flightCount)} · from ${Formatting.points(browsing.lowestPoints)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn {
                        items(browsing.flights, key = { it.id }) { flight ->
                            FlightRow(
                                flight = flight,
                                isSaved = flight.id in savedIds,
                                isSelected = flight.id == selected?.id,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onSelectFlight(flight)
                                    browsingCode = null
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

private fun createMapView(context: Context, camera: MapCameraMemory) = MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
    isTilesScaledToDpi = true
    minZoomLevel = 2.0
    maxZoomLevel = 12.0
    isVerticalMapRepetitionEnabled = false
    setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
    controller.setZoom(camera.zoom ?: 2.6)
    controller.setCenter(camera.center ?: GeoPoint(30.0, -20.0))
}

private data class MarkerColors(val accent: Int, val onAccent: Int, val surface: Int, val onSurface: Int)

/** Owns the overlays and only rebuilds them when the inputs actually change. */
private class FlightMapRenderer(private val mapView: MapView, private val onMarkerTap: (String) -> Unit) {
    private var lastMarkers: List<DestinationMarker>? = null
    private var lastSelectedId: String? = null
    private var lastColors: MarkerColors? = null
    private val iconCache = HashMap<String, BitmapDrawable>()
    private val density = mapView.resources.displayMetrics.density

    fun render(markers: List<DestinationMarker>, selected: Flight?, colors: MarkerColors) {
        if (markers === lastMarkers && selected?.id == lastSelectedId && colors == lastColors) return
        if (colors != lastColors) iconCache.clear()
        lastMarkers = markers
        lastSelectedId = selected?.id
        lastColors = colors

        mapView.overlays.clear()
        if (selected != null) {
            mapView.overlays.add(routeLine(selected, colors))
        }
        markers.forEach { m ->
            val highlighted = selected?.destination == m.airportCode
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(m.latitude, m.longitude)
                    icon = icon(m, highlighted, colors)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setInfoWindow(null)
                    title = m.city
                    setOnMarkerClickListener { _, _ ->
                        onMarkerTap(m.airportCode)
                        true
                    }
                },
            )
        }
        if (selected != null) {
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(selected.originLatitude, selected.originLongitude)
                    icon = originIcon(selected.origin, colors)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ -> true }
                },
            )
        }
        mapView.invalidate()
    }

    private fun routeLine(flight: Flight, colors: MarkerColors) = Polyline(mapView).apply {
        setPoints(greatCircle(flight.originLatitude, flight.originLongitude, flight.destinationLatitude, flight.destinationLongitude))
        outlinePaint.color = colors.accent
        outlinePaint.strokeWidth = 3.5f * density
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.isAntiAlias = true
        setOnClickListener { _, _, _ -> true }
    }

    private fun icon(m: DestinationMarker, highlighted: Boolean, colors: MarkerColors): BitmapDrawable {
        val key = "${m.airportCode}|${m.flightCount}|${m.lowestPoints}|$highlighted"
        return iconCache.getOrPut(key) {
            BitmapDrawable(
                mapView.resources,
                pillBitmap(
                    primary = m.airportCode,
                    secondary = Formatting.compactPoints(m.lowestPoints),
                    badge = m.flightCount.toString(),
                    background = if (highlighted) colors.accent else colors.surface,
                    foreground = if (highlighted) colors.onAccent else colors.onSurface,
                    badgeColor = colors.accent,
                    highlighted = highlighted,
                ),
            )
        }
    }

    private fun originIcon(code: String, colors: MarkerColors): BitmapDrawable =
        iconCache.getOrPut("origin|$code") {
            BitmapDrawable(
                mapView.resources,
                pillBitmap(code, null, null, colors.onSurface, colors.surface, colors.accent, highlighted = false, pointer = false),
            )
        }

    private fun pillBitmap(
        primary: String,
        secondary: String?,
        badge: String?,
        background: Int,
        foreground: Int,
        badgeColor: Int,
        highlighted: Boolean,
        pointer: Boolean = true,
    ): Bitmap {
        val d = density * if (highlighted) 1.12f else 1f
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f * d; typeface = Typeface.DEFAULT_BOLD; color = foreground }
        val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f * d; color = foreground }
        val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f * d; typeface = Typeface.DEFAULT_BOLD; color = if (highlighted) foreground else badgeColor
        }
        val padH = 9f * d
        val gap = 5f * d
        val height = 26f * d
        val pointerH = if (pointer) 6f * d else 0f
        val shadow = 2f * d
        val badgeW = badge?.let { badgeText.measureText(it) + 10f * d } ?: 0f
        val width = padH * 2 + bold.measureText(primary) +
            (secondary?.let { gap + regular.measureText(it) } ?: 0f) +
            (if (badge != null) gap + badgeW else 0f)

        val bmp = Bitmap.createBitmap((width + shadow * 2).toInt() + 1, (height + pointerH + shadow * 2).toInt() + 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = background
            setShadowLayer(shadow, 0f, shadow / 2, 0x40000000)
        }
        val body = RectF(shadow, shadow, shadow + width, shadow + height)
        canvas.drawRoundRect(body, height / 2, height / 2, fill)
        if (pointer) {
            val path = Path().apply {
                moveTo(body.centerX() - 5f * d, body.bottom - 1f)
                lineTo(body.centerX() + 5f * d, body.bottom - 1f)
                lineTo(body.centerX(), body.bottom + pointerH)
                close()
            }
            canvas.drawPath(path, fill)
        }
        val baseline = body.centerY() - (bold.descent() + bold.ascent()) / 2
        var x = body.left + padH
        canvas.drawText(primary, x, baseline, bold)
        x += bold.measureText(primary)
        if (secondary != null) {
            x += gap
            canvas.drawText(secondary, x, baseline, regular)
            x += regular.measureText(secondary)
        }
        if (badge != null) {
            x += gap
            val badgeRect = RectF(x, body.centerY() - 8f * d, x + badgeW, body.centerY() + 8f * d)
            val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (highlighted) 0x40FFFFFF else (badgeColor and 0x00FFFFFF) or 0x22000000
            }
            canvas.drawRoundRect(badgeRect, 8f * d, 8f * d, badgeFill)
            val badgeBaseline = badgeRect.centerY() - (badgeText.descent() + badgeText.ascent()) / 2
            canvas.drawText(badge, badgeRect.left + 5f * d, badgeBaseline, badgeText)
        }
        return bmp
    }
}

/** Points along the great circle between two coordinates, so long-haul routes curve like real flight paths. */
internal fun greatCircle(lat1: Double, lon1: Double, lat2: Double, lon2: Double, segments: Int = 64): List<GeoPoint> {
    val p1 = Math.toRadians(lat1); val l1 = Math.toRadians(lon1)
    val p2 = Math.toRadians(lat2); val l2 = Math.toRadians(lon2)
    val dist = 2 * asin(sqrt(sin((p2 - p1) / 2).let { it * it } + cos(p1) * cos(p2) * sin((l2 - l1) / 2).let { it * it }))
    if (dist == 0.0) return listOf(GeoPoint(lat1, lon1), GeoPoint(lat2, lon2))
    var previousLon = lon1
    return (0..segments).map { i ->
        val f = i.toDouble() / segments
        val a = sin((1 - f) * dist) / sin(dist)
        val b = sin(f * dist) / sin(dist)
        val x = a * cos(p1) * cos(l1) + b * cos(p2) * cos(l2)
        val y = a * cos(p1) * sin(l1) + b * cos(p2) * sin(l2)
        val z = a * sin(p1) + b * sin(p2)
        val lat = Math.toDegrees(atan2(z, sqrt(x * x + y * y)))
        var lon = Math.toDegrees(atan2(y, x))
        // Keep longitudes continuous across the antimeridian so the line doesn't streak across the map.
        while (lon - previousLon > 180) lon -= 360
        while (lon - previousLon < -180) lon += 360
        previousLon = lon
        GeoPoint(lat, lon)
    }
}
