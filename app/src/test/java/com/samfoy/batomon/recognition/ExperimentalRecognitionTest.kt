package com.samfoy.batomon.recognition

import org.junit.Assert.*
import org.junit.Test

class ExperimentalRecognitionTest {
    @Test fun nullFrameRemainsUnknown() {
        val result = ExperimentalSceneRecognizer().recognize(null)
        assertEquals(Scene.UNKNOWN, result?.scene); assertEquals(0f, result?.confidence)
    }
    @Test fun lowConfidenceUnknownIsNotACommittedScene() {
        val gate = ObservationGate(requiredMatches = 2, minimumConfidence = .8f)
        val candidate = Recognition(Scene.UNKNOWN, .18f, "no fixture")
        assertFalse(gate.accept(candidate).committed); assertFalse(gate.accept(candidate).committed)
    }
}
