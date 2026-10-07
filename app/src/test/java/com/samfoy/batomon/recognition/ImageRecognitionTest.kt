package com.samfoy.batomon.recognition

import org.junit.Assert.*
import org.junit.Test

class ImageRecognitionTest {
    @Test fun hashIsStableAndConfidenceIsBounded() {
        val samples = IntArray(64) { it * 3 }
        val hash = PerceptualHash.signature(samples)
        assertEquals(0, PerceptualHash.hammingDistance(hash, hash))
        assertEquals(1f, PerceptualHash.confidence(0))
        assertEquals(0f, PerceptualHash.confidence(64))
    }

    @Test fun normalizedRegionsMapAcrossResolutions() {
        assertEquals(PixelRect(192, 108, 960, 972), NormalizedRect(.1f, .1f, .5f, .9f).toPixels(1920, 1080))
        assertEquals(PixelRect(124, 108, 620, 972), NormalizedRect(.1f, .1f, .5f, .9f).toPixels(1240, 1080))
    }
}
