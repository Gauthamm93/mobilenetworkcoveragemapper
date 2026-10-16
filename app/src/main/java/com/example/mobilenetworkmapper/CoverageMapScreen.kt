package com.example.mobilenetworkmapper

import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
fun CoverageMapScreen(
    measurements: List<NetworkMeasurement>,
    currentLat: String = "",
    currentLon: String = "",
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit = {},
    onDeleteMeasurement: (Int) -> Unit = {},
    onDeleteMultipleMeasurements: (List<Int>) -> Unit = {},
    isMeasuring: Boolean = false
) {
    var selectedLocationMeasurements by remember { mutableStateOf<List<NetworkMeasurement>?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "NETWORK COVERAGE MAP",
            style = MaterialTheme.typography.titleLarge
        )

        if (isMeasuring) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF18864B))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF18864B))
                    )
                    Text(
                        text = "Live Measuring Active • Dots updating automatically",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF18864B)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onBackClick) {
                Text("BACK")
            }

            Button(
                onClick = {
                    val myLatNum = currentLat.toDoubleOrNull()
                    val myLonNum = currentLon.toDoubleOrNull()
                    if (myLatNum != null && myLonNum != null && mapViewRef != null) {
                        mapViewRef?.controller?.animateTo(GeoPoint(myLatNum, myLonNum))
                        mapViewRef?.controller?.setZoom(17.0)
                    } else {
                        onRefreshClick()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF)
                )
            ) {
                Text("MY LOCATION")
            }

            Button(onClick = onRefreshClick) {
                Text("REFRESH MAP")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    Configuration.getInstance().userAgentValue =
                        "MobileNetworkMapper/1.0 (com.example.mobilenetworkmapper)"
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        controller.setZoom(18.0)
                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    mapView.overlays.removeAll(
                        mapView.overlays.filterIsInstance<Marker>()
                    )
                    mapView.overlays.removeAll(
                        mapView.overlays.filterIsInstance<Polygon>()
                    )

                    val validMeasurements = measurements.filter {
                        val lat = it.latitude.toString().toDoubleOrNull()
                        val lon = it.longitude.toString().toDoubleOrNull()

                        lat != null && lon != null &&
                                lat in -90.0..90.0 &&
                                lon in -180.0..180.0 &&
                                (lat != 0.0 || lon != 0.0)
                    }

                    // 1. Add measurement markers
                    validMeasurements.forEachIndexed { index, measurement ->
                        val baseLat = measurement.latitude.toString().toDouble()
                        val baseLon = measurement.longitude.toString().toDouble()

                        // Calculate offset if multiple dots are recorded at identical spot
                        val sameSpotIndex = validMeasurements.take(index).count {
                            val mLat = it.latitude.toString().toDoubleOrNull() ?: 0.0
                            val mLon = it.longitude.toString().toDoubleOrNull() ?: 0.0
                            kotlin.math.abs(mLat - baseLat) < 0.00008 && kotlin.math.abs(mLon - baseLon) < 0.00008
                        }

                        val offsetLat = sameSpotIndex * 0.000025
                        val offsetLon = sameSpotIndex * 0.000025
                        val lat = baseLat + offsetLat
                        val lon = baseLon + offsetLon

                        val countAtLocation = validMeasurements.count {
                            val mLat = it.latitude.toString().toDoubleOrNull() ?: 0.0
                            val mLon = it.longitude.toString().toDoubleOrNull() ?: 0.0
                            kotlin.math.abs(mLat - baseLat) < 0.00015 && kotlin.math.abs(mLon - baseLon) < 0.00015
                        }

                        val dbm = measurement.signalDbm.toString().toIntOrNull()

                        val color = when {
                            dbm == null -> android.graphics.Color.GRAY
                            dbm >= -85 -> android.graphics.Color.rgb(40, 160, 70)
                            dbm >= -100 -> android.graphics.Color.rgb(240, 180, 0)
                            else -> android.graphics.Color.RED
                        }

                        val marker = Marker(mapView).apply {
                            position = GeoPoint(lat, lon)
                            title = if (countAtLocation > 1) {
                                "${measurement.operator} - ${measurement.networkType} ($countAtLocation items)"
                            } else {
                                "${measurement.operator} - ${measurement.networkType}"
                            }

                            val percentage = dbmToPercentage(measurement.signalDbm)
                            snippet = "Signal: ${measurement.signalDbm} dBm ($percentage%)\nQuality: ${measurement.signalQuality}\nTime: ${measurement.timestamp}"

                            icon = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(color)
                                setStroke(if (countAtLocation > 1) 5 else 3, android.graphics.Color.WHITE)
                                setSize(if (countAtLocation > 1) 40 else 34, if (countAtLocation > 1) 40 else 34)
                            }

                            setAnchor(
                                Marker.ANCHOR_CENTER,
                                Marker.ANCHOR_CENTER
                            )

                            setOnMarkerClickListener { _, _ ->
                                val group = validMeasurements.filter {
                                    val mLat = it.latitude.toString().toDoubleOrNull() ?: 0.0
                                    val mLon = it.longitude.toString().toDoubleOrNull() ?: 0.0
                                    kotlin.math.abs(mLat - baseLat) < 0.00015 && kotlin.math.abs(mLon - baseLon) < 0.00015
                                }
                                selectedLocationMeasurements = group
                                true
                            }
                        }

                        mapView.overlays.add(marker)
                    }

                    // 2. Add My Location Marker
                    val myLatNum = currentLat.toDoubleOrNull()
                    val myLonNum = currentLon.toDoubleOrNull()

                    if (myLatNum != null && myLonNum != null &&
                        myLatNum in -90.0..90.0 && myLonNum in -180.0..180.0 &&
                        (myLatNum != 0.0 || myLonNum != 0.0)
                    ) {
                        val myGeoPoint = GeoPoint(myLatNum, myLonNum)

                        val accuracyCircle = Polygon(mapView).apply {
                            points = Polygon.pointsAsCircle(myGeoPoint, 15.0)
                            fillPaint.color = android.graphics.Color.argb(40, 0, 122, 255)
                            outlinePaint.color = android.graphics.Color.rgb(0, 122, 255)
                            outlinePaint.strokeWidth = 2f
                        }
                        mapView.overlays.add(accuracyCircle)

                        val myLocationMarker = Marker(mapView).apply {
                            position = myGeoPoint
                            title = "My Current Location"
                            snippet = "You are here (High Accuracy GPS)\nLat: $currentLat\nLon: $currentLon"

                            icon = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(android.graphics.Color.rgb(0, 122, 255))
                                setStroke(5, android.graphics.Color.WHITE)
                                setSize(48, 48)
                            }

                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            setOnMarkerClickListener { clicked, _ ->
                                clicked.showInfoWindow()
                                true
                            }
                        }
                        mapView.overlays.add(myLocationMarker)

                        // Center on user position
                        mapView.controller.setCenter(myGeoPoint)
                    } else if (validMeasurements.isNotEmpty()) {
                        val latest = validMeasurements.last()
                        mapView.controller.setCenter(
                            GeoPoint(
                                latest.latitude.toString().toDouble(),
                                latest.longitude.toString().toDouble()
                            )
                        )
                    }

                    mapView.invalidate()
                }
            )

            if (measurements.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "No saved measurements to display.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.DarkGray
                    )
                }
            }

            // Signal Legend on Map
            MapLegendCard(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }

    // Overlapped Measurements Dialog
    if (selectedLocationMeasurements != null) {
        val itemsAtLocation = selectedLocationMeasurements!!

        AlertDialog(
            onDismissRequest = { selectedLocationMeasurements = null },
            title = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (itemsAtLocation.size > 1) "Overlapped Measurements" else "Measurement Details",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${itemsAtLocation.size} measurement(s) at this location",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsAtLocation.forEach { measurement ->
                        val percentage = dbmToPercentage(measurement.signalDbm)
                        val signalColor = when {
                            measurement.signalDbm >= -85 -> Color(0xFF18864B)
                            measurement.signalDbm >= -100 -> Color(0xFFE3A008)
                            else -> Color(0xFFD93636)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF4F7FB)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${measurement.id} • ${measurement.timestamp}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.DarkGray
                                    )
                                    Button(
                                        onClick = {
                                            onDeleteMeasurement(measurement.id)
                                            val remaining = itemsAtLocation.filter { it.id != measurement.id }
                                            selectedLocationMeasurements = if (remaining.isNotEmpty()) remaining else null
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFD93636)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("Delete", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Text(
                                    text = "${measurement.signalDbm} dBm ($percentage%) • ${measurement.signalQuality}",
                                    color = signalColor,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = "Operator: ${measurement.operator} (${measurement.networkType})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (itemsAtLocation.size > 1) {
                    Button(
                        onClick = {
                            val idsToDelete = itemsAtLocation.map { it.id }
                            onDeleteMultipleMeasurements(idsToDelete)
                            selectedLocationMeasurements = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD93636)
                        )
                    ) {
                        Text("Delete All Overlapped")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { selectedLocationMeasurements = null }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun MapLegendCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "SIGNAL LEGEND",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF14243A)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(
                    color = Color(0xFF007AFF),
                    label = "You",
                    description = "GPS"
                )
                LegendItem(
                    color = Color(40, 160, 70),
                    label = "Good",
                    description = "≥ -85 dBm"
                )
                LegendItem(
                    color = Color(240, 180, 0),
                    label = "Moderate",
                    description = "-86..-100"
                )
                LegendItem(
                    color = Color(217, 54, 54),
                    label = "Weak",
                    description = "< -100"
                )
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.DarkGray
            )
        }
    }
}
