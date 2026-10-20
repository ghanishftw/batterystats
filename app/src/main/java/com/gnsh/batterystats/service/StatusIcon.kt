package com.gnsh.batterystats.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.gnsh.batterystats.R
import com.gnsh.batterystats.data.AppSettings
import com.gnsh.batterystats.data.BatterySnapshot
import com.gnsh.batterystats.data.IconType
import kotlin.math.abs
import kotlin.math.roundToInt

/** Draws the live value (level, current, temperature or power) as the status bar icon. */
class StatusIcon(context: Context) {
    private val size = (24 * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(48)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private var cacheKey: String? = null
    private var cached: IconCompat? = null

    fun icon(s: BatterySnapshot?, settings: AppSettings): IconCompat? {
        if (s == null) return null
        val (value, unit) = when (settings.iconType) {
            IconType.Level -> "${s.level}" to null
            IconType.Current -> "${abs(s.currentMa)}" to "mA"
            IconType.Temperature -> {
                val t = if (settings.fahrenheit) s.tempC * 9 / 5 + 32 else s.tempC
                "${t.roundToInt()}°" to null
            }
            IconType.Power -> "%.1f".format(abs(s.powerW)) to "W"
            IconType.AppIcon -> return null
        }
        val key = "$value|$unit"
        if (key == cacheKey) return cached
        return IconCompat.createWithBitmap(render(value, unit)).also { cacheKey = key; cached = it }
    }

    /** Value centered, with an optional small unit underneath, scaled to fit the icon. */
    private fun render(value: String, unit: String?): Bitmap {
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        val w = size.toFloat()
        val unitPaint = Paint(paint)

        paint.textSize = if (unit == null) w * 0.9f else w * 0.66f
        unitPaint.textSize = w * 0.34f
        val vb = Rect()
        val ub = Rect()
        fun measure() {
            paint.getTextBounds(value, 0, value.length, vb)
            if (unit != null) unitPaint.getTextBounds(unit, 0, unit.length, ub) else ub.setEmpty()
        }
        measure()
        val gap = if (unit == null) 0f else w * 0.07f
        // Shrink until the value fits the width and the stack fits the height.
        val scale = minOf(1f, w * 0.96f / vb.width(), w * 0.96f / (vb.height() + gap + ub.height()))
        if (scale < 1f) {
            paint.textSize *= scale
            unitPaint.textSize *= scale
            measure()
        }
        val total = vb.height() + gap * scale + ub.height()
        val top = (w - total) / 2f
        canvas.drawText(value, w / 2f, top - vb.top, paint)
        if (unit != null) canvas.drawText(unit, w / 2f, top + vb.height() + gap * scale - ub.top, unitPaint)
        return bmp
    }

    companion object {
        const val FALLBACK = R.drawable.ic_stat_battery
    }
}
