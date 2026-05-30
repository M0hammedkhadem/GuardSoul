package com.agon.app.utils

import javax.inject.Inject
import javax.inject.Singleton

enum class DetectionLayer {
    ACCESSIBILITY,
    NETWORK,
    AI_VISION
}

@Singleton
class SmartDetectionEngine @Inject constructor() {

    private val weights = mapOf(
        DetectionLayer.ACCESSIBILITY to 0.60f,
        DetectionLayer.NETWORK       to 0.25f,
        DetectionLayer.AI_VISION     to 0.15f
    )

    private val BLOCK_THRESHOLD = 0.55f

    fun evaluate(signals: Map<DetectionLayer, Float>): DetectionResult {
        val score = signals.entries.sumOf { (layer, confidence) ->
            (weights[layer] ?: 0f).toDouble() * confidence.toDouble()
        }.toFloat()

        return DetectionResult(
            shouldBlock = score >= BLOCK_THRESHOLD,
            confidence = score,
            triggeredBy = signals.filter { it.value > 0.5f }.keys
        )
    }
}

data class DetectionResult(
    val shouldBlock: Boolean,
    val confidence: Float,
    val triggeredBy: Set<DetectionLayer>
)
