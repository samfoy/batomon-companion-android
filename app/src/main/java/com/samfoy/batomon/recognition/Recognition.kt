package com.samfoy.batomon.recognition

import android.graphics.Bitmap

enum class Scene { UNKNOWN, TITLE, SHOP, BATTLE, RESULT }
data class Recognition(val scene: Scene, val confidence: Float, val details: String = "")

interface FrameRecognizer { fun recognize(frame: Bitmap?): Recognition? }

/** Recognition is deliberately pluggable: no real Batomon frame fixtures are bundled yet. */
class SceneRecognizer(private val recognizers: List<FrameRecognizer> = emptyList()) : FrameRecognizer {
    override fun recognize(frame: Bitmap?): Recognition {
        val result = recognizers.asSequence().mapNotNull { it.recognize(frame) }.maxByOrNull { it.confidence }
        return result ?: Recognition(Scene.UNKNOWN, 0f, "No calibrated recognizer; add a Thor fixture")
    }
}

class TemporalDebouncer(private val requiredMatches: Int = 3) {
    private var last: Recognition? = null; private var matches = 0
    fun accept(next: Recognition): Recognition? {
        if (next.scene == last?.scene) matches++ else { last = next; matches = 1 }
        return if (matches >= requiredMatches) next else null
    }
}

data class ObservationDecision(val candidate: Recognition, val committed: Boolean)

class ObservationGate(private val requiredMatches: Int = 3, private val minimumConfidence: Float = .8f) {
    private val debouncer = TemporalDebouncer(requiredMatches)
    fun accept(candidate: Recognition): ObservationDecision {
        val stable = debouncer.accept(candidate)
        return ObservationDecision(candidate, stable != null && stable.scene != Scene.UNKNOWN && stable.confidence >= minimumConfidence)
    }
}
