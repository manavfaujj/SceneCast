package com.example.monitoring

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class StreamHealthGrade(val label: String, val colorHex: Long) {
    EXCELLENT("EXCELLENT", 0xFF00E676),
    GOOD("STABLE", 0xFF00E5FF),
    WARNING("THERMAL / NET WARN", 0xFFFFB300),
    CRITICAL("CRITICAL", 0xFFFF3B5C)
}

data class DeviceDiagnosticsState(
    val cpuUsagePercent: Int = 12,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 31.0f,
    val thermalStatusLabel: String = "Nominal",
    val thermalWarningActive: Boolean = false,
    val networkTypeLabel: String = "Wi-Fi",
    val estimatedUplinkKbps: Int = 15000,
    val networkConnected: Boolean = true,
    val healthGrade: StreamHealthGrade = StreamHealthGrade.EXCELLENT,
    val warningBanner: String? = null
)

/**
 * Monitors real device CPU usage (process CPU time delta), battery level & temperature,
 * Android PowerManager thermal status, and network uplink capabilities in real time.
 */
class StreamMonitorManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private val _diagnostics = MutableStateFlow(DeviceDiagnosticsState())
    val diagnostics: StateFlow<DeviceDiagnosticsState> = _diagnostics.asStateFlow()

    private var lastCpuTimeMs = Process.getElapsedCpuTime()
    private var lastWallTimeMs = SystemClock.elapsedRealtime()

    fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                sampleDeviceMetrics()
                delay(2000L)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun sampleDeviceMetrics() {
        // 1. Process CPU usage relative to available cores
        val nowCpuMs = Process.getElapsedCpuTime()
        val nowWallMs = SystemClock.elapsedRealtime()
        val wallDelta = (nowWallMs - lastWallTimeMs).coerceAtLeast(1L)
        val cpuDelta = (nowCpuMs - lastCpuTimeMs).coerceAtLeast(0L)
        lastCpuTimeMs = nowCpuMs
        lastWallTimeMs = nowWallMs
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cpuPercent = ((cpuDelta * 100L) / (wallDelta * cores)).toInt().coerceIn(3, 99)

        // 2. Battery level, charging state, and temperature
        val batteryIntent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 85) ?: 85
        val scale = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val batteryPct = ((level * 100) / scale).coerceIn(0, 100)
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 315) ?: 315
        val tempC = tempTenths / 10f

        // 3. Android 10+ PowerManager thermal status
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val (thermalLabel, isThermalHot) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal" to false
                PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth" to false
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Warmth" to false
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe Thermal Throttling" to true
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Critical Overheat" to true
                else -> "Nominal" to false
            }
        } else {
            if (tempC >= 43f) "High Temp" to true else "Nominal" to false
        }

        // 4. Network connectivity & link upstream bandwidth
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = if (activeNet != null) cm.getNetworkCapabilities(activeNet) else null
        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netType = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet LAN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G / Cellular"
            else -> "Connected"
        }
        val upKbps = (caps?.linkUpstreamBandwidthKbps ?: 12000).coerceIn(500, 100000)

        // 5. Evaluate overall health & warning banners
        val thermalWarn = isThermalHot || tempC >= 42.5f
        val lowBatteryWarn = batteryPct <= 15 && !isCharging
        val warningText = when {
            !isConnected -> "Network disconnected — auto-reconnect armed"
            thermalWarn -> "Device temperature high (${String.format("%.1f", tempC)}°C) — consider 720p 30fps"
            lowBatteryWarn -> "Low battery ($batteryPct%) — connect charger for continuous streaming"
            upKbps < 2500 -> "Low network upload bandwidth (${upKbps} kbps)"
            else -> null
        }

        val grade = when {
            !isConnected -> StreamHealthGrade.CRITICAL
            thermalWarn || lowBatteryWarn || upKbps < 2500 -> StreamHealthGrade.WARNING
            cpuPercent > 80 -> StreamHealthGrade.GOOD
            else -> StreamHealthGrade.EXCELLENT
        }

        _diagnostics.value = DeviceDiagnosticsState(
            cpuUsagePercent = cpuPercent,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempCelsius = tempC,
            thermalStatusLabel = thermalLabel,
            thermalWarningActive = thermalWarn,
            networkTypeLabel = netType,
            estimatedUplinkKbps = upKbps,
            networkConnected = isConnected,
            healthGrade = grade,
            warningBanner = warningText
        )
    }
}
