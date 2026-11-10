package com.example.smartdrive

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.SystemClock
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.smartdrive.ui.theme.SmartDriveTheme
import com.google.android.gms.location.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Background = Color(0xFF10141B)
private val CardColor = Color(0xFF1B222D)
private val Accent = Color(0xFF39D5F6)
private val SecondaryText = Color(0xFF8D9AAA)

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var locationCallback: LocationCallback? = null

    private val speedKmh = mutableFloatStateOf(0f)
    private val distanceKm = mutableDoubleStateOf(0.0)

    private val isRecording = mutableStateOf(false)
    private val gpsConnected = mutableStateOf(false)

    private val elapsedSeconds = mutableLongStateOf(0L)

    private var startTime = 0L
    private var lastLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        setContent {
            SmartDriveTheme {

                DrivingScreen(
                    isRecording = isRecording.value,
                    gpsConnected = gpsConnected.value,
                    speedKmh = speedKmh.value,
                    distanceKm = distanceKm.value,
                    elapsedSeconds = elapsedSeconds.value,
                    onStart = { startTracking() },
                    onStop = { stopTracking() }
                )
            }
        }
    }

    private fun startTracking() {

        if (isRecording.value) return

        distanceKm.value = 0.0
        speedKmh.value = 0f
        elapsedSeconds.value = 0L

        lastLocation = null
        startTime = SystemClock.elapsedRealtime()

        isRecording.value = true
        lifecycleScope.launch {
            while (isRecording.value) {
                elapsedSeconds.value =
                    (SystemClock.elapsedRealtime() - startTime) / 1000L

                delay(1000L)
            }
        }


        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        )
            .setMinUpdateIntervalMillis(500L)
            .build()

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(
                result: LocationResult
            ) {

                val location = result.lastLocation ?: return

                gpsConnected.value = true

                // GPS精度が低いデータは除外
                if (location.accuracy > 50f) return

                val previousLocation = lastLocation

                if (previousLocation != null) {

                    val distance =
                        previousLocation.distanceTo(location)

                    // GPSの微小な揺れを除外
                    if (distance >= 3f && distance <= 200f) {

                        distanceKm.value +=
                            distance.toDouble() / 1000.0
                    }

                    // 速度計測
                    if (location.hasSpeed()) {

                        speedKmh.value =
                            location.speed * 3.6f

                    } else if (
                        location.elapsedRealtimeNanos >
                        previousLocation.elapsedRealtimeNanos
                    ) {

                        val timeDifference =
                            (
                                    location.elapsedRealtimeNanos -
                                            previousLocation.elapsedRealtimeNanos
                                    ) / 1_000_000_000.0

                        if (timeDifference > 0) {

                            speedKmh.value =
                                (distance / timeDifference * 3.6)
                                    .toFloat()
                        }
                    }
                }

                lastLocation = location
            }
        }

        try {

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

        } catch (e: SecurityException) {

            isRecording.value = false

            Toast.makeText(
                this,
                "位置情報の利用許可が必要です",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun stopTracking() {

        locationCallback?.let {

            fusedLocationClient.removeLocationUpdates(it)
        }

        locationCallback = null

        isRecording.value = false
        gpsConnected.value = false
        speedKmh.value = 0f

        lastLocation = null
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }
}

@Composable
fun DrivingScreen(
    isRecording: Boolean,
    gpsConnected: Boolean,
    speedKmh: Float,
    distanceKm: Double,
    elapsedSeconds: Long,
    onStart: () -> Unit,
    onStop: () -> Unit
) {

    val context = LocalContext.current

    var permissionDenied by remember {
        mutableStateOf(false)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {

                permissionDenied = false
                onStart()

            } else {

                permissionDenied = true
            }
        }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60

    Scaffold(
        containerColor = Background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SMART DRIVE",
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(35.dp))

            Text(
                text = "DRIVING MONITOR",
                color = SecondaryText,
                fontSize = 13.sp,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(25.dp))

            // 速度表示
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CardColor
                )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "CURRENT SPEED",
                        color = SecondaryText,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(15.dp))

                    Text(
                        text = String.format(
                            java.util.Locale.US,
                            "%.1f",
                            speedKmh
                        ),
                        color = Accent,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "km/h",
                        color = SecondaryText,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // GPS状態
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "●",
                    color = if (gpsConnected) Accent else SecondaryText
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (gpsConnected) {
                        "GPS CONNECTED"
                    } else {
                        "WAITING FOR GPS"
                    },
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 走行情報
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                DrivingInfoCard(
                    title = "DISTANCE",
                    value = String.format(
                        java.util.Locale.US,
                        "%.2f km",
                        distanceKm
                    ),
                    modifier = Modifier.weight(1f)
                )

                DrivingInfoCard(
                    title = "DURATION",
                    value = String.format(
                        java.util.Locale.US,
                        "%02d:%02d:%02d",
                        hours,
                        minutes,
                        seconds
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (permissionDenied) {

                Text(
                    text = "位置情報の利用許可が必要です",
                    color = Color.Red,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            // 開始・終了ボタン
            Button(
                onClick = {

                    if (isRecording) {

                        onStop()

                    } else {

                        val fineGranted =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                        val coarseGranted =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                        if (fineGranted || coarseGranted) {

                            onStart()

                        } else {

                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) {
                        Color(0xFFFF5252)
                    } else {
                        Accent
                    },
                    contentColor = Background
                )
            ) {

                Text(
                    text = if (isRecording) {
                        "STOP DRIVING"
                    } else {
                        "START DRIVING"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(25.dp))
        }
    }
}

@Composable
fun DrivingInfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardColor
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {

            Text(
                text = title,
                color = SecondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = value,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}