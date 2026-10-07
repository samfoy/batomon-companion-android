package com.samfoy.batomon.recognition

import com.samfoy.batomon.capture.CaptureCleanupPolicy
import org.junit.Assert.*
import org.junit.Test

class CaptureCleanupPolicyTest {
    @Test fun partialStartupStillNeedsCleanup() {
        assertTrue(CaptureCleanupPolicy.needsCleanup(running = false, resourcesAllocated = true))
        assertTrue(CaptureCleanupPolicy.needsCleanup(running = true, resourcesAllocated = false))
        assertFalse(CaptureCleanupPolicy.needsCleanup(running = false, resourcesAllocated = false))
    }
}
