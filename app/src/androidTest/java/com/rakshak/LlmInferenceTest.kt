package com.rakshak

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log

/**
 * Minimal standalone test to prove the MediaPipe Tasks GenAI model loads and generates text.
 * Requires the quantized Gemma .bin model to be placed at /data/local/tmp/gemma2b.bin on the device.
 */
@RunWith(AndroidJUnit4::class)
class LlmInferenceTest {

    @Test
    fun testGemmaLoadAndInference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelPath = "/data/local/tmp/gemma2b.bin" // You MUST adb push the model here

        Log.i("LlmInferenceTest", "Starting LLM Load Test...")
        val startTime = System.currentTimeMillis()

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(100)
                .build()

            // 1. Measure Load Time
            val llmInference = LlmInference.createFromOptions(context, options)
            val loadTimeMs = System.currentTimeMillis() - startTime
            Log.i("LlmInferenceTest", "MODEL LOADED SUCCESSFULLY! Load Time: ${loadTimeMs}ms")

            // 2. Measure First Inference Latency
            val prompt = "Based on NHTSA data, what is the fatality risk of a 30mph motorcycle crash without gear?"
            Log.i("LlmInferenceTest", "Running prompt: $prompt")
            
            val inferenceStartTime = System.currentTimeMillis()
            val response = llmInference.generateResponse(prompt)
            val inferenceTimeMs = System.currentTimeMillis() - inferenceStartTime

            Log.i("LlmInferenceTest", "INFERENCE COMPLETE! Time: ${inferenceTimeMs}ms")
            Log.i("LlmInferenceTest", "Response: $response")

            llmInference.close()
        } catch (e: Exception) {
            Log.e("LlmInferenceTest", "Failed to load or run LLM: ${e.message}", e)
            throw e
        }
    }
}
