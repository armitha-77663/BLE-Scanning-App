package com.example.blebeacon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class PositionMapView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var beacons: List<BleBeacon> = emptyList()
        set(value) { field = value; invalidate() }

    private val bgPaint = Paint().apply { color = Color.parseColor("#F8F8F6") }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(20, 0, 0, 0); strokeWidth = 1f }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; textAlign = Paint.Align.CENTER; color = Color.WHITE }
    private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; textAlign = Paint.Align.LEFT }
    private val posPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun colorFor(b: BleBeacon): Int = when (b.signalStrength) {
        SignalStrength.STRONG -> Color.parseColor("#1D9E75")
        SignalStrength.WEAK   -> Color.parseColor("#BA7517")
        SignalStrength.LOST   -> Color.parseColor("#E24B4A")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        if (beacons.isEmpty()) {
            metaPaint.color = Color.GRAY
            metaPaint.textAlign = Paint.Align.CENTER
            metaPaint.textSize = 28f
            canvas.drawText("No beacons detected", w / 2f, h / 2f, metaPaint)
            metaPaint.textAlign = Paint.Align.LEFT
            return
        }

        val pad = 60f
        val minX = beacons.minOf { it.x }
        val maxX = beacons.maxOf { it.x }
        val minY = beacons.minOf { it.y }
        val maxY = beacons.maxOf { it.y }
        val rangeX = maxOf(maxX - minX, 1f)
        val rangeY = maxOf(maxY - minY, 1f)
        val scale = minOf((w - pad * 2) / rangeX, (h - pad * 2) / rangeY) * 0.75f
        val offX = w / 2f - (minX + maxX) / 2f * scale
        val offY = h / 2f - (minY + maxY) / 2f * scale

        fun sx(x: Float) = x * scale + offX
        fun sy(y: Float) = y * scale + offY

        // Grid lines
        for (gx in (minX - 1).toInt()..(maxX + 1).toInt()) {
            canvas.drawLine(sx(gx.toFloat()), 0f, sx(gx.toFloat()), h, gridPaint)
        }
        for (gy in (minY - 1).toInt()..(maxY + 1).toInt()) {
            canvas.drawLine(0f, sy(gy.toFloat()), w, sy(gy.toFloat()), gridPaint)
        }

        // Draw each beacon
        beacons.forEach { b ->
            val bx = sx(b.x)
            val by = sy(b.y)
            val color = colorFor(b)
            val ringR = (b.distance * scale * 0.5f).coerceAtMost(200f)

            // Range ring
            ringPaint.color = color
            ringPaint.alpha = 80
            canvas.drawCircle(bx, by, ringR, ringPaint)

            // Ring fill
            fillPaint.color = color
            fillPaint.alpha = 25
            canvas.drawCircle(bx, by, ringR, fillPaint)

            // Beacon dot
            circlePaint.color = color
            circlePaint.alpha = 255
            canvas.drawCircle(bx, by, 22f, circlePaint)

            // Initials label
            labelPaint.color = Color.WHITE
            canvas.drawText(b.name.take(2).uppercase(), bx, by + 7f, labelPaint)

            // RSSI text
            metaPaint.color = Color.DKGRAY
            metaPaint.textSize = 24f
            metaPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${b.rssi}dBm", bx + 26f, by - 6f, metaPaint)

            // Distance text
            metaPaint.color = Color.GRAY
            metaPaint.textSize = 22f
            canvas.drawText("${"%.1f".format(b.distance)}m", bx + 26f, by + 18f, metaPaint)
        }

        // Estimated position (blue dot) — only shown when 3+ beacons
        BleCalculator.trilaterate(beacons)?.let { (px, py) ->
            val spx = sx(px)
            val spy = sy(py)

            posPaint.color = Color.argb(50, 24, 95, 165)
            canvas.drawCircle(spx, spy, 28f, posPaint)

            posPaint.color = Color.parseColor("#185FA5")
            canvas.drawCircle(spx, spy, 16f, posPaint)

            posPaint.color = Color.WHITE
            canvas.drawCircle(spx, spy, 8f, posPaint)

            metaPaint.color = Color.parseColor("#185FA5")
            metaPaint.textSize = 26f
            metaPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("You", spx + 20f, spy - 6f, metaPaint)
        }
    }
}