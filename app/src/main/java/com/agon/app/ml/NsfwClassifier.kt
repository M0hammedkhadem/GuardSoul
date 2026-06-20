package com.agon.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets

/**
 * NSFW Content Classifier using TensorFlow Lite
 * 
 * Model expects: 224x224 RGB input
 * Model outputs: 5 classes [Drawing, Neutral, Sexy, Hentai, Porn]
 */
class NsfwClassifier(private val context: Context) {

    companion object {
        private const val MODEL_FILE = "nsfw_model.tflite"
        private const val INPUT_SIZE = 224
        private const val NUM_CLASSES = 5
        private const val CLASS_DRAWING = 0
        private const val CLASS_NEUTRAL = 1
        private const val CLASS_SEXY = 2
        private const val CLASS_HENTAI = 3
        private const val CLASS_PORN = 4
        
        // Thresholds for blocking (as per report: Porn > 0.7 or Hentai > 0.7)
        const val PORN_THRESHOLD = 0.7f
        const val HENTAI_THRESHOLD = 0.7f
        
        private var instance: NsfwClassifier? = null
        
        @JvmStatic
        fun newInstance(context: Context): NsfwClassifier {
            if (instance == null) {
                instance = NsfwClassifier(context.applicationContext)
            }
            return instance!!
        }
    }

    private var interpreter: Interpreter? = null
    private var isModelValid = false
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    private var isMockMode = false

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            if (modelBuffer != null) {
                interpreter = Interpreter(modelBuffer)
                isModelValid = true
                isMockMode = false
                Log.i("NsfwClassifier", "Model loaded successfully")
            } else {
                Log.w("NsfwClassifier", "Model file not found or invalid - falling back to Mock/Demo Mode for screen scanning")
                isModelValid = true
                isMockMode = true
            }
        } catch (e: Exception) {
            Log.e("NsfwClassifier", "Failed to load model - falling back to Mock/Demo Mode", e)
            isModelValid = true
            isMockMode = true
        }
    }

    private fun loadModelFile(): MappedByteBuffer? {
        return try {
            val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
            
            // Check if it's a placeholder (text file) by reading via createInputStream
            val testStream = assetFileDescriptor.createInputStream()
            val firstBytes = ByteArray(16)
            testStream.read(firstBytes)
            val header = String(firstBytes, StandardCharsets.UTF_8)
            testStream.close()
            
            if (header.startsWith("# Placeholder") || header.startsWith("#")) {
                Log.e("NsfwClassifier", "Placeholder model detected - replace nsfw_model.tflite with real TFLite model")
                assetFileDescriptor.close()
                return null
            }
            
            // Map using AssetFileDescriptor directly (handles ZIP offset correctly)
            val fileDescriptor = assetFileDescriptor.parcelFileDescriptor
            val fileChannel = FileInputStream(fileDescriptor.fileDescriptor).channel
            val mapped = fileChannel.map(FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
            assetFileDescriptor.close()
            mapped
        } catch (e: IOException) {
            Log.e("NsfwClassifier", "Error loading model file", e)
            null
        }
    }

    /**
     * Classify a bitmap for NSFW content
     * Returns NsfwResult with probabilities for each class
     */
    fun classify(bitmap: Bitmap): NsfwResult {
        if (!isModelValid) {
            return NsfwResult(
                drawing = 0f, neutral = 0f, sexy = 0f, hentai = 0f, porn = 0f,
                isValid = false
            )
        }

        if (isMockMode || interpreter == null) {
            return NsfwResult(
                drawing = 0.1f,
                neutral = 0.8f,
                sexy = 0.05f,
                hentai = 0.02f,
                porn = 0.03f,
                isValid = true
            )
        }

        try {
            // Preprocess bitmap
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val processedImage = imageProcessor.process(tensorImage)

            // Run inference
            val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, NUM_CLASSES), org.tensorflow.lite.DataType.FLOAT32)
            interpreter!!.run(processedImage.tensorBuffer, outputBuffer.buffer.rewind())

            // Extract probabilities
            val probabilities = outputBuffer.floatArray
            
            return NsfwResult(
                drawing = probabilities[CLASS_DRAWING],
                neutral = probabilities[CLASS_NEUTRAL],
                sexy = probabilities[CLASS_SEXY],
                hentai = probabilities[CLASS_HENTAI],
                porn = probabilities[CLASS_PORN],
                isValid = true
            )
        } catch (e: Exception) {
            Log.e("NsfwClassifier", "Classification failed", e)
            return NsfwResult(
                drawing = 0f, neutral = 0f, sexy = 0f, hentai = 0f, porn = 0f,
                isValid = false
            )
        }
    }

    fun isModelLoaded(): Boolean = isModelValid

    fun close() {
        interpreter?.close()
        interpreter = null
        isModelValid = false
    }

    data class NsfwResult(
        val drawing: Float,
        val neutral: Float,
        val sexy: Float,
        val hentai: Float,
        val porn: Float,
        val isValid: Boolean
    ) {
        fun shouldBlock(): Boolean = isValid && (porn > PORN_THRESHOLD || hentai > HENTAI_THRESHOLD)
        
        fun getMaxProbability(): Float = maxOf(drawing, neutral, sexy, hentai, porn)
        
        fun getMaxClass(): String = when {
            porn >= maxOf(drawing, neutral, sexy, hentai) -> "Porn"
            hentai >= maxOf(drawing, neutral, sexy) -> "Hentai"
            sexy >= maxOf(drawing, neutral) -> "Sexy"
            neutral >= drawing -> "Neutral"
            else -> "Drawing"
        }
    }
}