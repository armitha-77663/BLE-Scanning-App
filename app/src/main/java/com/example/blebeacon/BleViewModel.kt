package com.example.blebeacon

import android.bluetooth.le.ScanResult
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class BleViewModel : ViewModel() {

    private val _beacons = MutableStateFlow<Map<String, BleBeacon>>(emptyMap())
    val beacons: StateFlow<Map<String, BleBeacon>> = _beacons.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun setScanning(scanning: Boolean) {
        _isScanning.value = scanning
    }

    fun processScanResult(result: ScanResult) {
        val id = result.device.address
        val name = result.scanRecord?.deviceName
            ?: result.device.name
            ?: "Beacon ${(_beacons.value.size + 1)}"
        val rssi = result.rssi
        val distance = BleCalculator.rssiToDistance(rssi)

        val existing = _beacons.value[id]
        val angle = existing?.let { atan2(it.y.toDouble(), it.x.toDouble()).toFloat() }
            ?: ((_beacons.value.size.toFloat() / 6f) * 2f * PI.toFloat())
        val r = distance.coerceIn(1f, 15f)

        val beacon = BleBeacon(
            id = id,
            name = name,
            rssi = rssi,
            distance = distance,
            x = existing?.x ?: (cos(angle) * r),
            y = existing?.y ?: (sin(angle) * r)
        )
        _beacons.value = _beacons.value + (id to beacon)
    }

    fun clearBeacons() {
        _beacons.value = emptyMap()
        addLog("Cleared all beacons")
    }

    fun addLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val entry = "[$time] $message"
        _logs.value = (listOf(entry) + _logs.value).take(40)
    }
}
