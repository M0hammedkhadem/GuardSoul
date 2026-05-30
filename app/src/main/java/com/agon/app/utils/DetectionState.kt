package com.agon.app.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DetectionState {
    private val _networkConfidence = MutableStateFlow(0f)
    val networkConfidence = _networkConfidence.asStateFlow()

    private val _aiVisionConfidence = MutableStateFlow(0f)
    val aiVisionConfidence = _aiVisionConfidence.asStateFlow()

    fun updateNetworkConfidence(confidence: Float) {
        _networkConfidence.value = confidence
    }

    fun updateAiVisionConfidence(confidence: Float) {
        _aiVisionConfidence.value = confidence
    }
}
