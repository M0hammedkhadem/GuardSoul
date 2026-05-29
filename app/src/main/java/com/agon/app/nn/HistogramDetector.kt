package com.agon.app.nn

import android.graphics.Bitmap
import timber.log.Timber

class HistogramDetector {

    companion object {
        private const val HIST_BINS = 64
        private const val SKIN_LOWER_HUE = 0f
        private const val SKIN_UPPER_HUE = 50f
        private const val SKIN_LOWER_SAT = 0.10f
        private const val SKIN_UPPER_SAT = 0.90f
        private const val SKIN_LOWER_VAL = 0.20f
        private const val SKIN_UPPER_VAL = 0.95f

        private const val RED_LOWER_HUE = 330f
        private const val RED_UPPER_HUE = 360f
        private const val RED_LOWER_HUE2 = 0f
        private const val RED_UPPER_HUE2 = 15f

        private val NSFW_SKIN_HUES = floatArrayOf(
            0f, 5f, 10f, 15f, 20f, 25f, 30f, 35f, 40f, 45f
        )
    }

    data class HistogramFeatures(
        val skinPixels: Float,
        val redPixels: Float,
        val brightnessMean: Float,
        val contrast: Float,
        val saturationMean: Float,
        val darkRegionRatio: Float
    )

    fun detect(bitmap: Bitmap): Float {
        try {
            val features = extractFeatures(bitmap)
            val score = computeScore(features)
            Timber.d("HistogramDetector: skin=${"%.3f".format(features.skinPixels)}, " +
                    "red=${"%.3f".format(features.redPixels)}, " +
                    "brightness=${"%.1f".format(features.brightnessMean)}, " +
                    "contrast=${"%.1f".format(features.contrast)}, " +
                    "saturation=${"%.3f".format(features.saturationMean)}, " +
                    "score=${"%.3f".format(score)}")
            return score.coerceIn(0f, 1f)
        } catch (e: Exception) {
            Timber.e(e, "HistogramDetector: detection failed")
            return 0.1f
        }
    }

    private fun extractFeatures(bitmap: Bitmap): HistogramFeatures {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var skinCount = 0
        var redCount = 0
        var totalBrightness = 0f
        var totalSaturation = 0f
        var darkCount = 0
        var brightnessSum = 0f
        val brightnessValues = FloatArray(pixels.size)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff

            val min = minOf(r, g, b)
            val max = maxOf(r, g, b)

            val brightness = max / 255f
            brightnessValues[i] = brightness
            totalBrightness += brightness

            val delta = max - min
            val sat = if (max == 0) 0f else delta.toFloat() / max
            totalSaturation += sat

            var hue = 0f
            if (delta != 0) {
                when (max) {
                    r -> hue = 60f * (((g - b).toFloat() / delta) % 6f)
                    g -> hue = 60f * (((b - r).toFloat() / delta) + 2f)
                    b -> hue = 60f * (((r - g).toFloat() / delta) + 4f)
                }
                if (hue < 0) hue += 360f
            }

            if (sat >= SKIN_LOWER_SAT && sat <= SKIN_UPPER_SAT &&
                brightness >= SKIN_LOWER_VAL && brightness <= SKIN_UPPER_VAL
            ) {
                if ((hue >= SKIN_LOWER_HUE && hue <= SKIN_UPPER_HUE) ||
                    (hue >= RED_LOWER_HUE && hue <= RED_UPPER_HUE) ||
                    (hue >= RED_LOWER_HUE2 && hue <= RED_UPPER_HUE2)
                ) {
                    skinCount++
                }
            }

            if ((hue >= RED_LOWER_HUE || hue <= RED_UPPER_HUE2) &&
                sat > 0.5f && brightness > 0.3f
            ) {
                redCount++
            }

            if (brightness < 0.15f) {
                darkCount++
            }
        }

        val total = pixels.size.toFloat()
        val brightnessMean = totalBrightness / total
        var contrastSum = 0f
        for (b in brightnessValues) {
            contrastSum += (b - brightnessMean) * (b - brightnessMean)
        }

        return HistogramFeatures(
            skinPixels = skinCount / total,
            redPixels = redCount / total,
            brightnessMean = brightnessMean * 255f,
            contrast = kotlin.math.sqrt(contrastSum / total) * 255f,
            saturationMean = totalSaturation / total,
            darkRegionRatio = darkCount / total
        )
    }

    private fun computeScore(features: HistogramFeatures): Float {
        var score = 0f

        val skinScore = when {
            features.skinPixels > 0.35f -> 0.85f + (features.skinPixels - 0.35f) * 0.5f
            features.skinPixels > 0.20f -> 0.60f + (features.skinPixels - 0.20f) * 1.67f
            features.skinPixels > 0.10f -> 0.30f + (features.skinPixels - 0.10f) * 3.0f
            features.skinPixels > 0.05f -> 0.15f + (features.skinPixels - 0.05f) * 3.0f
            else -> features.skinPixels * 3.0f
        }
        score += skinScore * 0.50f

        val redScore = features.redPixels * 3.0f
        score += redScore.coerceIn(0f, 0.15f)

        val satScore = if (features.saturationMean > 0.4f) {
            (features.saturationMean - 0.4f) * 2.0f
        } else 0f
        score += satScore.coerceIn(0f, 0.15f)

        val lowLightPenalty = if (features.darkRegionRatio > 0.6f) {
            -0.15f
        } else if (features.darkRegionRatio > 0.4f) {
            -0.08f
        } else 0f
        score += lowLightPenalty

        val extremeContrast = if (features.contrast > 80f) {
            (features.contrast - 80f) / 200f * 0.1f
        } else 0f
        score += extremeContrast.coerceIn(0f, 0.10f)

        return score.coerceIn(0.05f, 0.92f)
    }
}
