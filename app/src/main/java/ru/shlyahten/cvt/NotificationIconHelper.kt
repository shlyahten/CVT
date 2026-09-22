package ru.shlyahten.cvt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import kotlin.math.roundToInt

object NotificationIconHelper {

    /**
     * Dynamically creates a status bar notification icon with the temperature value.
     * Uses a transparent background with white glyphs so that Android SystemUI
     * can cleanly render/tint the digits in the status bar or notification shade.
     */
    fun createTemperatureIcon(
        context: Context,
        tempCelsius: Double?,
    ): IconCompat {
        if (tempCelsius == null) {
            return IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        }

        return try {
            val sizePx = 72
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val rounded = tempCelsius.roundToInt()
            val text = when {
                rounded in -9..99 -> "$rounded°"
                rounded in 100..999 -> "$rounded"
                else -> "$rounded"
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = when {
                    text.length <= 2 -> 46f
                    text.length == 3 -> 38f
                    else -> 30f
                }
            }

            // Vertically center text bounds in canvas
            val textBounds = Rect()
            paint.getTextBounds(text, 0, text.length, textBounds)
            val x = sizePx / 2f
            val y = sizePx / 2f + textBounds.height() / 2f - textBounds.bottom

            canvas.drawText(text, x, y, paint)

            IconCompat.createWithBitmap(bitmap)
        } catch (e: Exception) {
            IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        }
    }
}
