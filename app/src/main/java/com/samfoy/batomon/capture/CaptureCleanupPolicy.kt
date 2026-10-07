package com.samfoy.batomon.capture

/** Pure lifecycle decision so partial-startup cleanup is regression-testable on the JVM. */
internal object CaptureCleanupPolicy {
    fun needsCleanup(running: Boolean, resourcesAllocated: Boolean): Boolean = running || resourcesAllocated
}
