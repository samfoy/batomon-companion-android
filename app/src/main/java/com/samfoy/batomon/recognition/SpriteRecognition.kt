package com.samfoy.batomon.recognition

import android.graphics.Bitmap
import android.graphics.Color
import org.json.JSONObject
import kotlin.math.sqrt

data class SpriteSignature(val hash64: Long, val averageRed: Int, val averageGreen: Int, val averageBlue: Int, val edgeDensity: Float)
data class SpriteTemplate(val id: String, val name: String, val signature: SpriteSignature)

object SpriteSignatureFactory {
    fun fromBitmap(bitmap: Bitmap, rect: NormalizedRect? = null): SpriteSignature {
        val bounds = rect?.toPixels(bitmap.width, bitmap.height) ?: PixelRect(0, 0, bitmap.width, bitmap.height)
        val cropWidth = bounds.width.coerceAtMost(bitmap.width - bounds.left).coerceAtLeast(1); val cropHeight = bounds.height.coerceAtMost(bitmap.height - bounds.top).coerceAtLeast(1)
        val crop = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, cropWidth, cropHeight); val small = Bitmap.createScaledBitmap(crop, 16, 16, true); val hashBitmap = Bitmap.createScaledBitmap(crop, 8, 8, true)
        val luma = IntArray(64) { index -> val p = hashBitmap.getPixel(index % 8, index / 8); (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000 }
        val hash = PerceptualHash.signature(luma); var red = 0L; var green = 0L; var blue = 0L
        for (y in 0 until 16) for (x in 0 until 16) { val p = small.getPixel(x, y); red += Color.red(p); green += Color.green(p); blue += Color.blue(p) }
        val gray = IntArray(256) { i -> val p = small.getPixel(i % 16, i / 16); (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000 }; var edges = 0
        for (y in 0 until 16) for (x in 0 until 15) if (kotlin.math.abs(gray[y * 16 + x] - gray[y * 16 + x + 1]) >= 24) edges++
        for (y in 0 until 15) for (x in 0 until 16) if (kotlin.math.abs(gray[y * 16 + x] - gray[(y + 1) * 16 + x]) >= 24) edges++
        if (small !== crop) small.recycle(); hashBitmap.recycle(); crop.recycle()
        return SpriteSignature(hash, (red / 256).toInt(), (green / 256).toInt(), (blue / 256).toInt(), edges / 480f)
    }
}

class SpriteBoardMatcher(private val templates: List<SpriteTemplate>, private val board: NormalizedRect = ThorRegions.board, private val minimumScore: Float = .86f) {
    fun match(frame: Bitmap): List<Pair<String, Float>> {
        if (templates.isEmpty()) return emptyList()
        val bounds = board.toPixels(frame.width, frame.height); val slotWidth = (bounds.width / 6).coerceAtLeast(1)
        return (0 until 6).mapNotNull { slot ->
            val rect = PixelRect(bounds.left + slot * slotWidth, bounds.top, if (slot == 5) bounds.width - slot * slotWidth else slotWidth, bounds.height)
            val sig = SpriteSignatureFactory.fromBitmap(frame, NormalizedRect(rect.left.toFloat() / frame.width, rect.top.toFloat() / frame.height, rect.width.toFloat() / frame.width, rect.height.toFloat() / frame.height))
            val best = templates.maxByOrNull { score(sig, it.signature) } ?: return@mapNotNull null; val confidence = score(sig, best.signature)
            if (confidence >= minimumScore) best.name to confidence else null
        }
    }
    private fun score(actual: SpriteSignature, expected: SpriteSignature): Float { val hash = PerceptualHash.confidence(PerceptualHash.hammingDistance(actual.hash64, expected.hash64)); val color = (1f - sqrt(((actual.averageRed - expected.averageRed) * (actual.averageRed - expected.averageRed) + (actual.averageGreen - expected.averageGreen) * (actual.averageGreen - expected.averageGreen) + (actual.averageBlue - expected.averageBlue) * (actual.averageBlue - expected.averageBlue)).toFloat()) / 441f).coerceIn(0f, 1f); val edge = (1f - kotlin.math.abs(actual.edgeDensity - expected.edgeDensity)).coerceIn(0f, 1f); return hash * .65f + color * .25f + edge * .1f }
}

class ExperimentalSceneRecognizer(private val boardMatcher: SpriteBoardMatcher? = null) : FrameRecognizer {
    override fun recognize(frame: Bitmap?): Recognition? {
        if (frame == null) return Recognition(Scene.UNKNOWN, 0f, "no frame")
        val stats = FrameStats.fromBitmap(frame); val scene = when {
            stats.greenRatio > .16f -> Recognition(Scene.BATTLE, (.48f + (stats.greenRatio - .16f)).coerceAtMost(.72f), "inferred from green arena-like pixels; uncalibrated")
            stats.centerBrightRatio > .42f && stats.edgeDensity < .18f -> Recognition(Scene.RESULT, .52f, "inferred from bright centered overlay; uncalibrated")
            else -> Recognition(Scene.UNKNOWN, .18f, "no robust public scene signature; needs Thor fixture")
        }
        val board = boardMatcher?.match(frame).orEmpty(); return if (board.isEmpty()) scene else scene.copy(details = scene.details + "; board candidates=" + board.joinToString { "${it.first}(${"%.2f".format(it.second)})" })
    }
}

data class FrameStats(val greenRatio: Float, val centerBrightRatio: Float, val edgeDensity: Float) {
    companion object { fun fromBitmap(bitmap: Bitmap): FrameStats { val scaled = Bitmap.createScaledBitmap(bitmap, 32, 18, true); var green = 0; var centerBright = 0; var edges = 0; val luminance = IntArray(32 * 18); for (y in 0 until 18) for (x in 0 until 32) { val p = scaled.getPixel(x, y); val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p); if (g > r * 1.15f && g > b * 1.08f) green++; if (x in 8..23 && y in 4..13 && r + g + b > 600) centerBright++; luminance[y * 32 + x] = (r * 299 + g * 587 + b * 114) / 1000 }; for (y in 0 until 18) for (x in 0 until 31) if (kotlin.math.abs(luminance[y * 32 + x] - luminance[y * 32 + x + 1]) >= 24) edges++; for (y in 0 until 17) for (x in 0 until 32) if (kotlin.math.abs(luminance[y * 32 + x] - luminance[(y + 1) * 32 + x]) >= 24) edges++; scaled.recycle(); return FrameStats(green / 576f, centerBright / 160f, edges / 1088f) } }
}
