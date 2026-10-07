package com.samfoy.batomon.recognition

import org.junit.Assert.*
import org.junit.Test

class RecognitionTest {
    @Test fun unknownWithoutFixturesIsHonest() {
        // The empty recognizer chain never touches a frame; avoid Android graphics stubs in a JVM test.
        val result = SceneRecognizer().recognize(null)
        assertEquals(Scene.UNKNOWN, result.scene)
        assertEquals(0f, result.confidence)
    }

    @Test fun debounceRequiresStableFrames() {
        val debouncer = TemporalDebouncer(3)
        val battle = Recognition(Scene.BATTLE, .8f)
        assertNull(debouncer.accept(battle)); assertNull(debouncer.accept(battle)); assertNotNull(debouncer.accept(battle))
    }
}
