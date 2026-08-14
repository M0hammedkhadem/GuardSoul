package com.agon.app.engine

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * On-device NSFW image classifier — Yahoo open_nsfw ported to TFLite.
 *
 * Input : [1, 224, 224, 3] float32, BGR order, Caffe mean subtraction
 *         (B-104, G-117, R-123). Images are resized to 256 then center
 *         cropped to 224 (VGG-style preprocessing).
 * Output: [1, 2] = (sfw score, nsfw score).
 *
 * Everything runs locally; no image ever leaves the device.
 */
class NsfwClassifier(context: Context) {

    companion object {
        private const val MODEL_ASSET = "nsfw.tflite"
        private const val INPUT = 224
        private const val RESIZE = 256
        const val BLOCK_THRESHOLD = 0.85f
    }

    private var interpreter: Interpreter? = null
    private val lock = Any()

    val isReady: Boolean get() = interpreter != null

    init {
        runCatching {
            val buffer = loadModel(context)
            val options = Interpreter.Options().apply { numThreads = 2 }
            interpreter = Interpreter(buffer, options)
        }
    }

    private fun loadModel(context: Context): MappedByteBuffer {
        // tflite is flagged noCompress, so we can memory-map straight from the APK.
        val afd = context.assets.openFd(MODEL_ASSET)
        FileInputStream(afd.fileDescriptor).use { stream ->
            return stream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength,
            )
        }
    }

    /** Returns the NSFW probability in [0,1], or -1 if the model is unavailable. */
    fun nsfwScore(bitmap: Bitmap): Float {
        val engine = interpreter ?: return -1f
        return runCatching {
            val input = preprocess(bitmap)
            val output = Array(1) { FloatArray(2) }
            synchronized(lock) { engine.run(input, output) }
            output[0][1]
        }.getOrDefault(-1f)
    }

    private fun preprocess(src: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(src, RESIZE, RESIZE, true)
        val offset = (RESIZE - INPUT) / 2
        val cropped = Bitmap.createBitmap(resized, offset, offset, INPUT, INPUT)
        if (cropped != resized) resized.recycle()

        val buffer = ByteBuffer
            .allocateDirect(INPUT * INPUT * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT * INPUT)
        cropped.getPixels(pixels, 0, INPUT, 0, 0, INPUT, INPUT)
        cropped.recycle()

        for (p in pixels) {
            val r = (p shr 16 and 0xFF).toFloat()
            val g = (p shr 8 and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()
            // BGR order + Caffe means, matching the original open_nsfw pipeline.
            buffer.putFloat(b - 104f)
            buffer.putFloat(g - 117f)
            buffer.putFloat(r - 123f)
        }
        buffer.rewind()
        return buffer
    }

    fun close() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null
        }
    }
}
