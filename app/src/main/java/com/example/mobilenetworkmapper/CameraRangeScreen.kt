package com.example.mobilenetworkmapper

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

@Composable
fun CameraRangeScreen(
    currentSignalStrength: String,
    currentSignalCategory: String,
    operator: String,
    networkType: String,
    currentLat: String,
    currentLon: String,
    measurements: List<NetworkMeasurement>,
    onBackClick: () -> Unit,
    onCaptureClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val dbmValue = currentSignalStrength.removeSuffix(" dBm").toIntOrNull()
    val percentage = dbmValue?.let { dbmToPercentage(it) } ?: 0

    val signalColor = when (currentSignalCategory.uppercase()) {
        "STRONG" -> Color(0xFF18864B)
        "MODERATE" -> Color(0xFFE3A008)
        "WEAK" -> Color(0xFFD93636)
        else -> Color.Gray
    }

    // Filter nearby measurements within 2 km
    val myLat = currentLat.toDoubleOrNull()
    val myLon = currentLon.toDoubleOrNull()

    val nearbySpots = remember(measurements, myLat, myLon) {
        if (myLat == null || myLon == null) emptyList()
        else {
            measurements.mapNotNull { m ->
                val mLat = m.latitude
                val mLon = m.longitude
                val results = FloatArray(1)
                Location.distanceBetween(myLat, myLon, mLat, mLon, results)
                val distanceMeters = results[0].roundToInt()
                if (distanceMeters in 1..2000) {
                    m to distanceMeters
                } else null
            }.sortedBy { it.second }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            // Live Camera Viewfinder
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Camera Permission Required",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        // Top Overlay: Live Signal Strength HUD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBackClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    )
                ) {
                    Text("BACK")
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        text = "CAMERA RANGE FINDER",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CURRENT CAMERA LOCATION RANGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray
                            )
                            Text(
                                text = "$currentSignalStrength ($percentage%)",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = signalColor)
                        ) {
                            Text(
                                text = currentSignalCategory,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Text(
                        text = "Operator: $operator • $networkType",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }
        }

        // Center AR Targeting Reticle
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.Center)
                .border(2.dp, signalColor, CircleShape)
                .background(signalColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Text(
                    text = if (percentage >= 50) "GOOD RANGE" else "POOR RANGE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        // Bottom Overlay: Nearby Saved Spots & Action Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (nearbySpots.isNotEmpty()) {
                Text(
                    text = "NEARBY RECORDED SPOTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = nearbySpots,
                        key = { it.first.id }
                    ) { (m, dist) ->
                        val spotDbm = dbmToPercentage(m.signalDbm)
                        val spotColor = when {
                            m.signalDbm >= -85 -> Color(0xFF18864B)
                            m.signalDbm >= -100 -> Color(0xFFE3A008)
                            else -> Color(0xFFD93636)
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(spotColor)
                                )
                                Column {
                                    Text(
                                        text = "${m.signalDbm} dBm ($spotDbm%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${dist}m away • ${m.operator}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onCaptureClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF087F8C)
                )
            ) {
                Text(
                    text = "RECORD SPOT AT THIS VIEW",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
