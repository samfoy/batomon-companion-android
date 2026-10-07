package com.samfoy.batomon.capture

import org.junit.Assert.*
import org.junit.Test

class FrameProcessingTest {
    @Test fun rowStridePaddingIsCalculatedWithoutCopyingPixels() {
        val layout = FrameBufferLayout(width = 10, height = 4, rowStride = 48, pixelStride = 4)
        assertEquals(8, layout.rowPadding); assertEquals(12, layout.paddedWidth)
    }
    @Test fun invalidLayoutCannotSilentlyProduceCorruptBitmap() {
        assertThrows(IllegalArgumentException::class.java) { FrameBufferLayout(10, 4, 20, 4) }
    }
}
