package com.samfoy.batomon.recognition

import android.graphics.Bitmap
import kotlin.math.roundToInt

/** Coordinates expressed as fractions of the captured default display. */
data class NormalizedRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun toPixels(frameWidth: Int, frameHeight: Int): PixelRect = PixelRect(
        (left * frameWidth).roundToInt().coerceIn(0, frameWidth),
        (top * frameHeight).roundToInt().coerceIn(0, frameHeight),
        (width * frameWidth).roundToInt().coerceAtLeast(1).coerceAtMost(frameWidth),
        (height * frameHeight).roundToInt().coerceAtLeast(1).coerceAtMost(frameHeight)
    )
}

data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int)

/** Stable screen regions; percentages survive letterboxing and Thor/display scaling. */
object ThorRegions {
    val title = NormalizedRect(.25f, .05f, .50f, .16f)
    val round = NormalizedRect(.03f, .03f, .18f, .12f)
    val board = NormalizedRect(.10f, .25f, .80f, .45f)
    val result = NormalizedRect(.25f, .35f, .50f, .30f)
}

object PerceptualHash {
    fun hammingDistance(left: Long, right: Long): Int = java.lang.Long.bitCount(left xor right)
    fun confidence(distance: Int, bits: Int = 64): Float = (1f - distance.toFloat() / bits).coerceIn(0f, 1f)

    /** Average luminance hash over a fixed grid. Pure array input keeps golden tests JVM-only. */
    fun signature(luminance: IntArray, grid: Int = 8): Long {
        require(luminance.size == grid * grid) { "expected ${grid * grid} luminance samples" }
        val average = luminance.average()
        var result = 0L
        luminance.forEachIndexed { index, value -> if (value >= average) result = result or (1L shl index) }
        return result
    }

    fun fromBitmap(bitmap: Bitmap, rect: NormalizedRect): Long {
        val bounds = rect.toPixels(bitmap.width, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width.coerceAtMost(bitmap.width - bounds.left), bounds.height.coerceAtMost(bitmap.height - bounds.top))
        val scaled = Bitmap.createScaledBitmap(crop, 8, 8, true)
        val samples = IntArray(64) { index ->
            val pixel = scaled.getPixel(index % 8, index / 8)
            ((android.graphics.Color.red(pixel) * 299 + android.graphics.Color.green(pixel) * 587 + android.graphics.Color.blue(pixel) * 114) / 1000)
        }
        if (scaled !== crop) scaled.recycle(); crop.recycle()
        return signature(samples)
    }
}

data class SceneTemplate(val scene: Scene, val region: NormalizedRect, val hash: Long, val minimumConfidence: Float = .82f)

/** Template recognizer intentionally requires supplied, lawful fixtures; no bundled game frames. */
class TemplateSceneRecognizer(private val templates: List<SceneTemplate>) : FrameRecognizer {
    override fun recognize(frame: Bitmap?): Recognition? {
        if (frame == null || templates.isEmpty()) return null
        return templates.asSequence().map { template ->
            val confidence = PerceptualHash.confidence(PerceptualHash.hammingDistance(template.hash, PerceptualHash.fromBitmap(frame, template.region)))
            template to confidence
        }.maxByOrNull { it.second }?.takeIf { it.second >= it.first.minimumConfidence }?.let { Recognition(it.first.scene, it.second, "perceptual template") }
    }
}
