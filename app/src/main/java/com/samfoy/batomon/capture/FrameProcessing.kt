package com.samfoy.batomon.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.Image
import kotlin.math.max
import kotlin.math.min

data class FrameBufferLayout(val width: Int, val height: Int, val rowStride: Int, val pixelStride: Int) {
    val rowPadding: Int get() = rowStride - width * pixelStride
    val paddedWidth: Int get() = width + rowPadding / pixelStride
    init { require(width > 0 && height > 0 && pixelStride > 0 && rowStride >= width * pixelStride) }
}

data class ProcessedFrame(val bitmap: Bitmap, val rotationDegrees: Int, val letterboxRemoved: Boolean)

object FrameBitmapConverter {
    fun fromImage(image: Image, rotationDegrees: Int = 0, maxDimension: Int = 640): ProcessedFrame {
        val plane = image.planes.firstOrNull() ?: error("Image has no planes")
        val layout = FrameBufferLayout(image.width, image.height, plane.rowStride, plane.pixelStride)
        val padded = Bitmap.createBitmap(layout.paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        var bitmap = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        if (rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { rotated -> if (rotated !== bitmap) bitmap.recycle() }
        }
        val (cropped, wasCropped) = LetterboxCrop.crop(bitmap)
        if (cropped !== bitmap) bitmap.recycle()
        if (max(cropped.width, cropped.height) > maxDimension) {
            val scale = maxDimension.toFloat() / max(cropped.width, cropped.height)
            val scaled = Bitmap.createScaledBitmap(cropped, (cropped.width * scale).toInt().coerceAtLeast(1), (cropped.height * scale).toInt().coerceAtLeast(1), true)
            cropped.recycle(); return ProcessedFrame(scaled, ((rotationDegrees % 360) + 360) % 360, wasCropped)
        }
        return ProcessedFrame(cropped, ((rotationDegrees % 360) + 360) % 360, wasCropped)
    }
}

/** Only removes a uniform, near-black border. Content is otherwise left untouched. */
object LetterboxCrop {
    fun crop(bitmap: Bitmap): Pair<Bitmap, Boolean> {
        if (bitmap.width < 64 || bitmap.height < 64) return bitmap to false
        val maxInsetX = (bitmap.width * .12f).toInt(); val maxInsetY = (bitmap.height * .12f).toInt()
        fun darkRow(y: Int): Boolean { var dark = 0; val step = max(1, bitmap.width / 32); for (x in 0 until bitmap.width step step) { val p = bitmap.getPixel(x, y); if (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p) < 30) dark++ }; return dark >= 24 }
        fun darkColumn(x: Int): Boolean { var dark = 0; val step = max(1, bitmap.height / 32); for (y in 0 until bitmap.height step step) { val p = bitmap.getPixel(x, y); if (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p) < 30) dark++ }; return dark >= 24 }
        var top = 0; while (top < maxInsetY && darkRow(top)) top++
        var bottom = bitmap.height; while (bottom - 1 > top + 32 && bitmap.height - bottom < maxInsetY && darkRow(bottom - 1)) bottom--
        var left = 0; while (left < maxInsetX && darkColumn(left)) left++
        var right = bitmap.width; while (right - 1 > left + 32 && bitmap.width - right < maxInsetX && darkColumn(right - 1)) right--
        if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height) return bitmap to false
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top) to true
    }
}
