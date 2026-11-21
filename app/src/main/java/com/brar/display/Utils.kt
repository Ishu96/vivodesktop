package com.brar.display

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Display
import android.view.WindowManager

object Utils {
    fun decodeDisplayFlags(flags: Int): String {
        val strings = buildList {
            if (flags and Display.FLAG_PRESENTATION != 0) add("PRESENTATION (dedicated display)")
            if (flags and Display.FLAG_PRIVATE != 0) add("PRIVATE (only owner can access)")
            if (flags and Display.FLAG_SECURE != 0) add("SECURE (protected content)")
            if (flags and Display.FLAG_SUPPORTS_PROTECTED_BUFFERS != 0) add("PROTECTED_BUFFERS (DRM content)")
            if (flags and Display.FLAG_ROUND != 0) add("ROUND (circular display)")
        }
        return if (strings.isEmpty()) "• No special flags set\n"
        else strings.joinToString("\n") { "• $it" } + "\n"
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun getDisplayInfo(display: Display, appContext: Context): String {
        val displayContext = appContext.createDisplayContext(display)
        val bounds = (displayContext.getSystemService(WindowManager::class.java)
            as WindowManager).currentWindowMetrics.bounds

        return buildString {
            append("Display ID: ").append(display.displayId).append('\n')
            append("Name: ").append(display.name).append('\n')
            append("State: ").append(if (display.state == Display.STATE_ON) "ON" else "OFF").append('\n')
            append("Width: ").append(bounds.width()).append("px\n")
            append("Height: ").append(bounds.height()).append("px\n")
            append("Density: ").append(appContext.resources.displayMetrics.densityDpi).append("dpi\n")
            append("Refresh Rate: ").append(display.refreshRate).append("Hz\n")
            append("HDR Capabilities: ").append(display.hdrCapabilities).append('\n')
            append("Flags: ").append(decodeDisplayFlags(display.flags)).append('\n')
            append("Is Valid: ").append(display.isValid).append('\n')
        }
    }
}
