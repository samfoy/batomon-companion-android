package com.samfoy.batomon

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Requires the host script to set overlay_display_devices before this class runs. */
@RunWith(AndroidJUnit4::class)
class MainActivitySecondaryDisplayTest {
    @Test
    fun routesTheCompanionActivityOffTheDefaultDisplay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })

        assertTrue("Companion UI did not appear", device.wait(Until.hasObject(By.textContains("AUTOMATIC CAPTURE")), 15_000))
        val activityDump = device.executeShellCommand("dumpsys activity activities")
        assertTrue(
            "MainActivity was not reported on a secondary display:\n$activityDump",
            Regex("com\\.samfoy\\.batomon[^\\n]*displayId=([1-9][0-9]*)").containsMatchIn(activityDump)
        )
    }
}
