package com.example.mobilenetworkmapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt


class MainActivity : ComponentActivity() {

    private lateinit var databaseHelper: NetworkDatabaseHelper

    // Current network information
    private var operator by mutableStateOf("Not measured")
    private var networkType by mutableStateOf("Not measured")
    private var signalStrength by mutableStateOf("Not measured")
    private var signalCategory by mutableStateOf("Not measured")

    // Current GPS information
    private var latitude by mutableStateOf("Not measured")
    private var longitude by mutableStateOf("Not measured")
    private var gpsAccuracy by mutableStateOf("High Precision")
    private var currentLocation: Location? = null
    private var showCoverageMap by mutableStateOf(false)
    private var showCameraRangeScreen by mutableStateOf(false)

    private val locationListener = object : android.location.LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            latitude = String.format(Locale.US, "%.6f", location.latitude)
            longitude = String.format(Locale.US, "%.6f", location.longitude)
            gpsAccuracy = "±${location.accuracy.roundToInt()}m"
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    // Continuous measuring state
    private var isMeasuring by mutableStateOf(false)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val measurementRunnable = object : Runnable {
        override fun run() {
            if (isMeasuring) {
                measureAndSave(silent = true)
                handler.postDelayed(this, 3000L)
            }
        }
    }

    private var mapMeasurements by
    mutableStateOf<List<NetworkMeasurement>>(emptyList())

    // Measurement history screen state
    private var showHistory by mutableStateOf(false)
    private var measurementHistory by mutableStateOf(emptyList<NetworkMeasurement>())

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val locationGranted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            val phoneGranted =
                permissions[Manifest.permission.READ_PHONE_STATE] == true

            if (locationGranted && phoneGranted) {
                startMeasuring()
            } else {
                operator = "Permission required"
                networkType = "Permission required"
                signalStrength = "Permission required"
                signalCategory = "Permission required"
                latitude = "Permission required"
                longitude = "Permission required"
                Toast.makeText(
                    this,
                    "Location and phone permissions are required to measure.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        databaseHelper = NetworkDatabaseHelper(this)

        setContent {

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {

                when {
                    showCameraRangeScreen -> {
                        BackHandler {
                            showCameraRangeScreen = false
                        }
                        CameraRangeScreen(
                            currentSignalStrength = signalStrength,
                            currentSignalCategory = signalCategory,
                            operator = operator,
                            networkType = networkType,
                            currentLat = latitude,
                            currentLon = longitude,
                            measurements = mapMeasurements,
                            onBackClick = {
                                showCameraRangeScreen = false
                            },
                            onCaptureClick = {
                                startMeasuring()
                            }
                        )
                    }

                    showCoverageMap -> {
                        BackHandler {
                            showCoverageMap = false
                        }
                        CoverageMapScreen(
                            measurements = mapMeasurements,
                            currentLat = latitude,
                            currentLon = longitude,
                            isMeasuring = isMeasuring,
                            onBackClick = {
                                showCoverageMap = false
                            },
                            onRefreshClick = {
                                mapMeasurements = databaseHelper.getAllMeasurements()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Map refreshed: ${mapMeasurements.size} measurements",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDeleteMeasurement = { id ->
                                databaseHelper.deleteMeasurement(id)
                                mapMeasurements = databaseHelper.getAllMeasurements()
                                measurementHistory = databaseHelper.getAllMeasurements()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Measurement deleted",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDeleteMultipleMeasurements = { ids ->
                                val count = databaseHelper.deleteMeasurements(ids)
                                mapMeasurements = databaseHelper.getAllMeasurements()
                                measurementHistory = databaseHelper.getAllMeasurements()
                                Toast.makeText(
                                    this@MainActivity,
                                    "$count measurements deleted",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    showHistory -> {
                        BackHandler {
                            showHistory = false
                        }
                        MeasurementHistoryScreen(
                            measurements = measurementHistory,
                            onBackClick = {
                                showHistory = false
                            },
                            onRefreshClick = {
                                measurementHistory = databaseHelper.getAllMeasurements()
                            },
                            onDeleteMeasurement = { id ->
                                databaseHelper.deleteMeasurement(id)
                                measurementHistory = databaseHelper.getAllMeasurements()
                                mapMeasurements = databaseHelper.getAllMeasurements()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Measurement deleted",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    else -> {
                        NetworkScreen(
                            operator = operator,
                            networkType = networkType,
                            signalStrength = signalStrength,
                            signalCategory = signalCategory,
                            latitude = latitude,
                            longitude = longitude,
                            gpsAccuracy = gpsAccuracy,
                            isMeasuring = isMeasuring,
                            onStartMeasuringClick = { startMeasuring() },
                            onStopMeasuringClick = { stopMeasuring() },
                            onHistoryClick = { openMeasurementHistory() },
                            onMapClick = {
                                mapMeasurements = databaseHelper.getAllMeasurements()

                                Toast.makeText(
                                    this@MainActivity,
                                    "Saved measurements: ${mapMeasurements.size}",
                                    Toast.LENGTH_LONG
                                ).show()

                                showCoverageMap = true
                            },
                            onCameraRangeClick = {
                                mapMeasurements = databaseHelper.getAllMeasurements()
                                showCameraRangeScreen = true
                            }
                        )
                    }
                }

            }
        }
    }

    private fun startMeasuring() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )

        val permissionsNeeded = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            isMeasuring = true
            handler.removeCallbacks(measurementRunnable)
            measureAndSave(silent = false)
            handler.postDelayed(measurementRunnable, 3000L)
            Toast.makeText(this, "Measuring started - Dots will be added live", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopMeasuring() {
        isMeasuring = false
        handler.removeCallbacks(measurementRunnable)
        Toast.makeText(this, "Measuring stopped", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun readNetworkInformation() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            operator = "Permission required"
            networkType = "Permission required"
            signalStrength = "Permission required"
            signalCategory = "Permission required"
            return
        }

        try {
            val telephonyManager =
                getSystemService(TELEPHONY_SERVICE) as TelephonyManager

            operator = telephonyManager.networkOperatorName.ifEmpty { "Unknown" }
            networkType = getNetworkTypeText(telephonyManager.dataNetworkType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val dbm = telephonyManager.signalStrength
                    ?.cellSignalStrengths
                    ?.map { it.dbm }
                    ?.filter { it != Int.MAX_VALUE }
                    ?.maxOrNull()

                if (dbm != null) {
                    signalStrength = "$dbm dBm"
                    signalCategory = classifySignal(dbm)
                } else {
                    signalStrength = "Unavailable"
                    signalCategory = "Unavailable"
                }
            } else {
                signalStrength = "Requires Android 10+"
                signalCategory = "Unavailable"
            }
        } catch (_: SecurityException) {
            operator = "Permission required"
            networkType = "Permission required"
            signalStrength = "Unavailable"
            signalCategory = "Unavailable"
        } catch (_: Exception) {
            operator = "Error"
            networkType = "Error"
            signalStrength = "Error"
            signalCategory = "Error"
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return

        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.5f,
                    locationListener
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0.5f,
                    locationListener
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun stopLocationUpdates() {
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        stopLocationUpdates()
        super.onPause()
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(): Location? {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted && !coarseLocationGranted) return null

        if (currentLocation != null) return currentLocation

        val locationManager =
            getSystemService(LOCATION_SERVICE) as? LocationManager ?: return null

        val gpsLocation = if (
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        ) {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } else null

        val networkLocation = if (
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } else null

        val passiveLocation = try {
            locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (_: Exception) { null }

        return gpsLocation ?: networkLocation ?: passiveLocation
    }

    private fun getNetworkTypeText(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G (GPRS)"
        TelephonyManager.NETWORK_TYPE_EDGE -> "2G (EDGE)"
        TelephonyManager.NETWORK_TYPE_GSM -> "2G (GSM)"
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G (UMTS)"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "3G (HSDPA)"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "3G (HSUPA)"
        TelephonyManager.NETWORK_TYPE_HSPA -> "3G (HSPA)"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G (LTE)"
        TelephonyManager.NETWORK_TYPE_NR -> "5G (NR)"
        else -> "Unknown"
    }

    private fun classifySignal(dbm: Int): String = when {
        dbm >= -85 -> "STRONG"
        dbm >= -100 -> "MODERATE"
        else -> "WEAK"
    }

    private fun measureAndSave(silent: Boolean = false) {
        readNetworkInformation()

        val location = getCurrentLocation()
        if (location != null) {
            latitude = String.format(Locale.US, "%.6f", location.latitude)
            longitude = String.format(Locale.US, "%.6f", location.longitude)
        } else {
            if (!silent && (latitude == "Not measured" || latitude == "Location unavailable")) {
                Toast.makeText(
                    this,
                    "Waiting for GPS location...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        saveMeasurement(silent = silent)
    }

    private fun saveMeasurement(silent: Boolean = false) {
        val lat = latitude.toDoubleOrNull()
        val lon = longitude.toDoubleOrNull()

        if (lat == null || lon == null) {
            if (!silent) Toast.makeText(this, "GPS location unavailable - Turn on GPS", Toast.LENGTH_SHORT).show()
            return
        }

        val rawDbm = signalStrength.replace(Regex("[^0-9-]"), "").toIntOrNull()
        val dbm = rawDbm ?: -85
        val quality = if (signalCategory == "Unavailable" || signalCategory == "Not measured" || signalCategory == "Permission required") {
            classifySignal(dbm)
        } else signalCategory

        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        try {
            databaseHelper.insertMeasurement(
                timestamp = timestamp,
                operator = if (operator == "Permission required" || operator == "Not measured") "Mobile Network" else operator,
                networkType = if (networkType == "Permission required" || networkType == "Not measured") "4G (LTE)" else networkType,
                signalDbm = dbm,
                signalQuality = quality,
                latitude = lat,
                longitude = lon
            )
            val updatedList = databaseHelper.getAllMeasurements()
            mapMeasurements = emptyList()
            mapMeasurements = updatedList
            measurementHistory = updatedList
            if (!silent) {
                Toast.makeText(this, "Measurement saved! Total: ${updatedList.size}", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            if (!silent) {
                Toast.makeText(this, "Could not save measurement", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openMeasurementHistory() {
        measurementHistory = databaseHelper.getAllMeasurements()
        showHistory = true
    }

    override fun onDestroy() {
        isMeasuring = false
        handler.removeCallbacks(measurementRunnable)
        if (::databaseHelper.isInitialized) databaseHelper.close()
        super.onDestroy()
    }
}


fun dbmToPercentage(dbm: Int): Int {
    return when {
        dbm <= -113 -> 0
        dbm >= -50 -> 100
        else -> (((dbm + 113).toFloat() / 63f) * 100f).roundToInt().coerceIn(0, 100)
    }
}


@Composable
fun NetworkScreen(
    operator: String,
    networkType: String,
    signalStrength: String,
    signalCategory: String,
    latitude: String,
    longitude: String,
    gpsAccuracy: String = "High Precision",
    isMeasuring: Boolean,
    onStartMeasuringClick: () -> Unit,
    onStopMeasuringClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onMapClick: () -> Unit,
    onCameraRangeClick: () -> Unit
) {
    val navy = Color(0xFF14243A)
    val teal = Color(0xFF087F8C)
    val background = Color(0xFFF4F7FB)

    val signalColor = when (signalCategory.uppercase()) {
        "STRONG" -> Color(0xFF18864B)
        "MODERATE" -> Color(0xFFE3A008)
        "WEAK" -> Color(0xFFD93636)
        else -> Color.Gray
    }

    val dbmValue = signalStrength.removeSuffix(" dBm").toIntOrNull()
    val signalPercentage = dbmValue?.let { dbmToPercentage(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "NETWORK MAPPER",
            style = MaterialTheme.typography.labelLarge,
            color = teal
        )

        Text(
            text = "Network Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            color = navy
        )

        Text(
            text = "Monitor your mobile signal and coverage.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.DarkGray
        )

        if (isMeasuring) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8F5E9)
                ),
                border = BorderStroke(1.dp, Color(0xFF18864B))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF18864B))
                    )
                    Column {
                        Text(
                            text = "Live Measuring Active...",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF18864B)
                        )
                        Text(
                            text = "Recording signal & GPS dots on map every 3 seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }

        // Main signal card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = navy
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SIGNAL STRENGTH",
                        color = Color(0xFFD2DCE8),
                        style = MaterialTheme.typography.labelLarge
                    )

                    if (signalPercentage != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = signalColor
                            )
                        ) {
                            Text(
                                text = "$signalPercentage%",
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 4.dp
                                ),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Text(
                    text = if (signalPercentage != null) "$signalStrength ($signalPercentage%)" else signalStrength,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = signalColor
                        )
                    ) {
                        Text(
                            text = signalCategory,
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 6.dp
                            ),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Text(
                        text = "Current reading",
                        color = Color(0xFFD2DCE8),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (signalPercentage != null) {
                    LinearProgressIndicator(
                        progress = { signalPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = signalColor,
                        trackColor = Color(0xFF1E3553)
                    )
                }
            }
        }

        // Dedicated Signal Percentage Meter Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "SIGNAL PERCENTAGE METER",
                    style = MaterialTheme.typography.labelSmall,
                    color = teal
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (signalPercentage != null) "$signalPercentage%" else "--%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = navy
                    )

                    Text(
                        text = if (signalPercentage != null) "Level: $signalCategory" else "Not Measured",
                        style = MaterialTheme.typography.titleMedium,
                        color = signalColor
                    )
                }

                LinearProgressIndicator(
                    progress = { (signalPercentage ?: 0) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = signalColor,
                    trackColor = Color(0xFFE0E6ED)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0% (Weak)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "50% (Moderate)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "100% (Strong)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }

        // Network details
        Text(
            text = "NETWORK DETAILS",
            style = MaterialTheme.typography.titleMedium,
            color = navy
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "NETWORK TYPE",
                        style = MaterialTheme.typography.labelSmall,
                        color = teal
                    )
                    Text(
                        text = networkType,
                        style = MaterialTheme.typography.titleMedium,
                        color = navy
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "OPERATOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = teal
                    )
                    Text(
                        text = operator,
                        style = MaterialTheme.typography.titleMedium,
                        color = navy
                    )
                }
            }
        }

        // Location card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
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
                    Text(
                        text = "MEASUREMENT LOCATION",
                        style = MaterialTheme.typography.titleMedium,
                        color = navy
                    )

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        )
                    ) {
                        Text(
                            text = "GPS: $gpsAccuracy",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color(0xFF18864B),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Text(
                    text = "Latitude: $latitude",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Longitude: $longitude",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStartMeasuringClick,
                enabled = !isMeasuring,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = teal,
                    disabledContainerColor = teal.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = "START MEASURING",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Button(
                onClick = onStopMeasuringClick,
                enabled = isMeasuring,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD93636),
                    disabledContainerColor = Color(0xFFD93636).copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = "STOP MEASURING",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        // Camera Range Finder action
        Button(
            onClick = onCameraRangeClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = navy
            )
        ) {
            Text("📷 CAMERA RANGE FINDER (AR)")
        }

        // Map action
        OutlinedButton(
            onClick = onMapClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("VIEW COVERAGE MAP")
        }

        // History action
        OutlinedButton(
            onClick = onHistoryClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("VIEW MEASUREMENT HISTORY")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Mobile Network Mapper • Network Survey",
            modifier = Modifier.fillMaxWidth(),
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
    }
}



@Composable
fun MeasurementHistoryScreen(
    measurements: List<NetworkMeasurement>,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onDeleteMeasurement: (Int) -> Unit = {}
) {
    val navy = Color(0xFF14243A)
    val teal = Color(0xFF087F8C)
    val background = Color(0xFFF4F7FB)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "NETWORK MAPPER",
            color = teal,
            style = MaterialTheme.typography.labelLarge
        )

        Text(
            text = "Measurement History",
            color = navy,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "${measurements.size} saved measurements",
            color = Color.DarkGray,
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("BACK")
            }

            Button(
                onClick = onRefreshClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = teal
                )
            ) {
                Text("REFRESH")
            }
        }

        if (measurements.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Text(
                    text = "No measurements saved yet.",
                    modifier = Modifier.padding(20.dp),
                    color = navy
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = measurements,
                    key = { it.id }
                ) { measurement ->

                    val percentage = dbmToPercentage(measurement.signalDbm)
                    val signalColor = when {
                        measurement.signalDbm >= -85 ->
                            Color(0xFF18864B)

                        measurement.signalDbm >= -100 ->
                            Color(0xFFE3A008)

                        else ->
                            Color(0xFFD93636)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 2.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Measurement #${measurement.id}",
                                        color = navy,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = measurement.timestamp,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "$percentage%",
                                        color = signalColor,
                                        style = MaterialTheme.typography.titleLarge
                                    )

                                    Button(
                                        onClick = { onDeleteMeasurement(measurement.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFD93636)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("Delete", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Text(
                                text = "${measurement.signalDbm} dBm ($percentage%)",
                                color = signalColor,
                                style = MaterialTheme.typography.headlineSmall
                            )

                            LinearProgressIndicator(
                                progress = { percentage / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = signalColor,
                                trackColor = Color(0xFFE0E6ED)
                            )

                            Text("Operator: ${measurement.operator}")
                            Text("Network: ${measurement.networkType}")
                            Text("Quality: ${measurement.signalQuality}")

                            Text(
                                text = "Location: ${measurement.latitude}, " +
                                        "${measurement.longitude}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

