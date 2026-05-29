package com.agon.app.nn

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import timber.log.Timber
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class NsfwDetector(private val context: Context) {

    companion object {
        private const val INPUT_SIZE = 224
        private const val NUM_THREADS = 4
        private const val MODEL_FILENAME = "nsfw_model.tflite"
    }

    private var tflite: Interpreter? = null
    private val fallbackDetector = HistogramDetector()

    val isModelLoaded: Boolean get() = tflite != null

    fun initialize() {
        try {
            val modelFile = loadModelFile()
            if (modelFile != null) {
                val options = Interpreter.Options().apply {
                    try {
                        addDelegate(NnApiDelegate())
                        Timber.d("NsfwDetector: NNAPI delegate added")
                    } catch (e: Exception) {
                        Timber.w("NsfwDetector: NNAPI not available, using CPU")
                    }
                    setNumThreads(NUM_THREADS)
                }
                tflite = Interpreter(modelFile, options)
                Timber.d("NsfwDetector: TFLite model loaded from internal storage")
            } else {
                Timber.w("NsfwDetector: model not found, using histogram fallback")
            }
        } catch (e: Exception) {
            Timber.e(e, "NsfwDetector: initialization failed, using fallback")
        }
    }

    fun detect(bitmap: Bitmap): Float {
        val interpreter = tflite
        if (interpreter != null) {
            return runTFLiteInference(interpreter, bitmap)
        }
        return fallbackDetector.detect(bitmap)
    }

    private fun runTFLiteInference(interpreter: Interpreter, bitmap: Bitmap): Float {
        try {
            val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

            val byteBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
            resized.getPixels(intValues, 0, resized.width, 0, 0, resized.width, resized.height)
            resized.recycle()

            for (pixel in intValues) {
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                byteBuffer.putFloat(r / 255.0f)
                byteBuffer.putFloat(g / 255.0f)
                byteBuffer.putFloat(b / 255.0f)
            }

            val output = Array(1) { FloatArray(2) }
            interpreter.run(byteBuffer, output)
            return output[0][1]
        } catch (e: Exception) {
            Timber.e(e, "NsfwDetector: TFLite inference failed")
            return fallbackDetector.detect(bitmap)
        }
    }

    private fun loadModelFile(): MappedByteBuffer? {
        val downloader = NsfwModelDownloader(context)
        val modelFile = downloader.getModelFile()
        if (!modelFile.exists()) {
            return try {
                val afd = context.assets.openFd(MODEL_FILENAME)
                val fis = FileInputStream(afd.fileDescriptor)
                val channel = fis.channel
                channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            } catch (e: Exception) {
                null
            }
        }
        return try {
            val fis = FileInputStream(modelFile)
            val channel = fis.channel
            channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        try {
            tflite?.close()
        } catch (_: Exception) {}
        tflite = null
    }
}
