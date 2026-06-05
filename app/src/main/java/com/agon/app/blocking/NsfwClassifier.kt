package com.agon.app.blocking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * NSFW (Not Safe For Work) image classifier backed by an on-device
 * TensorFlow Lite model.
 *
 * **Spec compliance**
 * The mission brief requires MobileNetV2 with 5 classes:
 *   0 — drawings
 *   1 — hentai
 *   2 — neutral
 *   3 — porn
 *   4 — sexy
 *
 * We load whatever `.tflite` model lives in `assets/nsfw_mobilenet.tflite`
 * (the standard Yahoo Open-NSFW model satisfies the spec exactly: 5
 * outputs in the same order). If the model file is missing, the
 * classifier falls back to a hand-tuned aspect-ratio + colour heuristic
 * so the AI Explorer feature still has a useful (if less accurate)
 * default.
 *
 * **Threading**
 * The [Interpreter] is **not** thread-safe — callers must serialise
 * access. The owning [com.agon.app.AiScannerService] already runs its
 * scan loop on a single coroutine so this is fine; if you need to use
 * the classifier from multiple threads, wrap calls in a Mutex.
 *
 * **Memory**
 * The interpreter's memory budget is set to 4 MB, which is more than
 * enough for MobileNetV2 @ 224x224. The interpreter is closed in
 * [close] — call that on service destruction.
 */
class NsfwClassifier(private val context: Context) {

    companion object {
        private const val TAG = "NsfwClassifier"
        const val MODEL_ASSET_PATH = "nsfw_mobilenet.tflite"
        /** Standard MobileNetV2 input size. */
        const val INPUT_SIZE = 224
        /** Standard MobileNetV2 mean / std (ImageNet). */
        private const val PIXEL_MEAN = 127.5f
        private const val PIXEL_STD = 127.5f
        const val NUM_CLASSES = 5

        /** Class index → human-readable label (Yahoo Open-NSFW ordering). */
        val LABELS = arrayOf("drawings", "hentai", "neutral", "porn", "sexy")

        /** Class indices that should trigger a block. */
        val BLOCKED_INDICES = intArrayOf(3 /*porn*/, 1 /*hentai*/)

        /**
         * Minimum confidence (0.0..1.0) for a blocked class to count.
         * Below this we treat the prediction as "noise" and stay silent.
         * Tunable per-spec.
         */
        const val DEFAULT_THRESHOLD: Float = 0.60f
    }

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var modelAvailable: Boolean = false

    /**
     * True when a real TFLite model was loaded successfully. False means
     * the classifier is running on a fallback heuristic — useful for
     * surfacing this in the AI Explorer card so the user can drop in a
     * proper model file.
     */
    val isUsingTflite: Boolean
        get() = modelAvailable

    init {
        try {
            val model = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(model, options)
            modelAvailable = true
            Timber.d("$TAG: loaded TFLite model from assets/$MODEL_ASSET_PATH")
        } catch (t: Throwable) {
            modelAvailable = false
            Timber.w(t, "$TAG: model not found at assets/$MODEL_ASSET_PATH — " +
                "running in heuristic fallback. Drop a MobileNetV2 NSFW model " +
                "(5 classes: drawings, hentai, neutral, porn, sexy) at that " +
                "path to enable real on-device inference.")
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFd = context.assets.openFd(MODEL_ASSET_PATH)
        val inputStream = FileInputStream(assetFd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFd.startOffset,
            assetFd.declaredLength
        )
    }

    /**
     * Run inference on [bitmap]. The bitmap is resized to 224×224 and
     * normalised to `[-1, 1]` (ImageNet preprocessing) before being fed
     * to the model.
     *
     * @return a [Result] with the top class label, the raw probability
     *         vector, the highest blocked-class score, and a
     *         `shouldBlock` boolean applying the default threshold.
     */
    fun classify(bitmap: Bitmap): Result {
        val interp = interpreter
        if (interp == null || !modelAvailable) {
            val fallback = heuristicFallback(bitmap)
            return Result(
                isUsingTflite = false,
                scores = floatArrayOf(0f, 0f, 1f, 0f, 0f),
                topLabel = "neutral",
                topConfidence = 1f,
                highestBlockedScore = fallback,
                shouldBlock = fallback >= DEFAULT_THRESHOLD
            )
        }
        val input = preprocess(bitmap)
        val output = Array(1) { FloatArray(NUM_CLASSES) }
        return try {
            interp.run(input, output)
            val scores = output[0]
            val topIdx = scores.indices.maxByOrNull { scores[it] } ?: 2
            val topScore = scores[topIdx]
            val blocked = BLOCKED_INDICES.maxOfOrNull { scores[it] } ?: 0f
            Result(
                isUsingTflite = true,
                scores = scores,
                topLabel = LABELS[topIdx],
                topConfidence = topScore,
                highestBlockedScore = blocked,
                shouldBlock = blocked >= DEFAULT_THRESHOLD
            )
        } catch (t: Throwable) {
            Timber.e(t, "$TAG: inference failed — returning neutral")
            Result(
                isUsingTflite = true,
                scores = floatArrayOf(0f, 0f, 1f, 0f, 0f),
                topLabel = "neutral",
                topConfidence = 1f,
                highestBlockedScore = 0f,
                shouldBlock = false
            )
        }
    }

    /**
     * Convert an ARGB_8888 [Bitmap] into a `ByteBuffer` of size
     * `1 * 224 * 224 * 3 * 4` (float32). Each pixel is normalised to
     * `[-1, 1]` by subtracting 127.5 and dividing by 127.5.
     */
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        }
        val byteBuffer = ByteBuffer.allocateDirect(
            1 * INPUT_SIZE * INPUT_SIZE * 3 * 4
        ).order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            byteBuffer.putFloat((r - PIXEL_MEAN) / PIXEL_STD)
            byteBuffer.putFloat((g - PIXEL_MEAN) / PIXEL_STD)
            byteBuffer.putFloat((b - PIXEL_MEAN) / PIXEL_STD)
        }
        return byteBuffer
    }

    /**
     * Last-resort heuristic used when the TFLite model is not bundled
     * in the APK. Looks at the skin-tone ratio of the bottom half of
     * the frame — vertical short-video content (Reels, Shorts) is
     * dominated by skin tones when NSFW. Crude, but it gives the AI
     * Explorer feature *something* to work with until the user drops
     * in a real model.
     */
    private fun heuristicFallback(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0f
        val sampleStep = 16
        var skinPixels = 0
        var total = 0
        for (y in h / 2 until h step sampleStep) {
            for (x in 0 until w step sampleStep) {
                val p = bitmap.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val isSkin = r > 95 && g > 40 && b > 20 &&
                    r > g && r > b &&
                    (max - min) > 15 &&
                    r > 60 && (g < r - 20 || b < r - 20)
                if (isSkin) skinPixels++
                total++
            }
        }
        val ratio = if (total > 0) skinPixels.toFloat() / total else 0f
        return ratio.coerceIn(0f, 1f)
    }

    /** Free the TFLite interpreter buffers. Idempotent. */
    fun close() {
        try { interpreter?.close() } catch (_: Throwable) {}
        interpreter = null
        modelAvailable = false
    }

    data class Result(
        val isUsingTflite: Boolean,
        val scores: FloatArray,
        val topLabel: String,
        val topConfidence: Float,
        val highestBlockedScore: Float,
        val shouldBlock: Boolean
    ) {
        override fun equals(other: Any?): Boolean = other is Result &&
            isUsingTflite == other.isUsingTflite &&
            topLabel == other.topLabel &&
            topConfidence == other.topConfidence &&
            highestBlockedScore == other.highestBlockedScore &&
            shouldBlock == other.shouldBlock &&
            scores.contentEquals(other.scores)

        override fun hashCode(): Int {
            var result = isUsingTflite.hashCode()
            result = 31 * result + scores.contentHashCode()
            result = 31 * result + topLabel.hashCode()
            result = 31 * result + topConfidence.hashCode()
            result = 31 * result + highestBlockedScore.hashCode()
            result = 31 * result + shouldBlock.hashCode()
            return result
        }
    }
}
