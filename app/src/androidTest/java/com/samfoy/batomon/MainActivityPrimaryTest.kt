package com.samfoy.batomon

import android.view.Display
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Runs before the CI job enables an overlay display. */
@RunWith(AndroidJUnit4::class)
class MainActivityPrimaryTest {
    @Test
    fun launchesOnTheDefaultDisplayWithTheCompanionSurface() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(Display.DEFAULT_DISPLAY, activity.display?.displayId)
                assertEquals("Batomon Companion", activity.title)
            }
        }
    }
}
