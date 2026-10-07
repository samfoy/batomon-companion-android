package com.samfoy.batomon

import android.content.Intent
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Requires the host script to set overlay_display_devices before this class runs. */
@RunWith(AndroidJUnit4::class)
class MainActivitySecondaryDisplayTest {
    @Test
    fun routesTheCompanionActivityOffTheDefaultDisplay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })

        var resumedActivity: MainActivity? = null
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                resumedActivity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .firstOrNull()
            }
            val displayId = resumedActivity?.display?.displayId
            if (displayId != null && displayId != Display.DEFAULT_DISPLAY) break
            Thread.sleep(100)
        }

        val activity = resumedActivity ?: throw AssertionError("Companion activity did not resume")
        assertTrue(
            "MainActivity remained on the default display ${activity.display?.displayId}",
            activity.display?.displayId != Display.DEFAULT_DISPLAY
        )
        assertTrue("Companion surface title was missing", activity.title == "Batomon Companion")
    }
}
