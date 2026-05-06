package com.yourname.blebeacon

data class BleBeacon(
    val id: String,
    val name: String,
    val rssi: Int,
    val distance: Float,
    val x: Float = 0f,
    val y: Float = 0f,
    val lastSeen: Long = System.currentTimeMillis()
) {
    val signalStrength: SignalStrength get() = when {
        rssi > -70 -> SignalStrength.STRONG
        rssi > -85 -> SignalStrength.WEAK
        else -> SignalStrength.LOST
    }

    val rssiBarPercent: Int get() =
        ((rssi + 100) / 50f * 100).toInt().coerceIn(0, 100)
}

enum class SignalStrength { STRONG, WEAK, LOST }

object BleCalculator {
    private const val TX_POWER = -59f
    private const val N_FACTOR = 2.5f

    fun rssiToDistance(rssi: Int): Float {
        return Math.pow(10.0, (TX_POWER - rssi) / (10.0 * N_FACTOR)).toFloat()
    }

    fun trilaterate(beacons: List<BleBeacon>): Pair<Float, Float>? {
        if (beacons.size < 3) return null
        val sorted = beacons.sortedBy { it.distance }.take(4)
        var x = 0f; var y = 0f; var w = 0f
        sorted.forEach { b ->
            val weight = 1f / (b.distance * b.distance + 0.01f)
            x += b.x * weight
            y += b.y * weight
            w += weight
        }
        return Pair(x / w, y / w)
    }
}