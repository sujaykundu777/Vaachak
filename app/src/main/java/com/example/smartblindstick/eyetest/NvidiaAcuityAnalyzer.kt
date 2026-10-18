package com.example.smartblindstick.eyetest

import android.util.Log
import com.example.smartblindstick.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class AiAcuityAnalysis(
    val accuracyScore: Int,
    val correctCount: Int,
    val misreadCount: Int,
    val heardLetters: List<String>,
    val classification: VisionClassification,
    val clinicalExplanation: String,
    val isAiPowered: Boolean = true
)

object NvidiaAcuityAnalyzer {

    private const val TAG = "NvidiaAcuityAnalyzer"
    private const val API_URL = "https://integrate.api.nvidia.com/v1/chat/completions"

    // Primary model active on the provided NVIDIA API key
    private const val PRIMARY_MODEL = "meta/llama-3.2-11b-vision-instruct"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Suspending version of analyzeSpeech for Coroutines / LaunchedEffect.
     */
    suspend fun analyzeSpeech(
        targetLine: AcuityLine,
        spokenText: String,
        stepIndex: Int = 0,
        highestPassedIndex: Int = -1
    ): AiAcuityAnalysis = withContext(Dispatchers.IO) {
        suspendCoroutine { cont ->
            analyzeSpeech(targetLine, spokenText, stepIndex, highestPassedIndex) { result ->
                cont.resume(result)
            }
        }
    }

    /**
     * Asynchronous callback version of analyzeSpeech.
     */
    fun analyzeSpeech(
        targetLine: AcuityLine,
        spokenText: String,
        stepIndex: Int = 0,
        highestPassedIndex: Int = -1,
        onResult: (AiAcuityAnalysis) -> Unit
    ) {
        val apiKey = BuildConfig.NVIDIA_GEMMA_API_KEY.ifEmpty {
            BuildConfig.NVIDIA_API_KEY
        }

        if (apiKey.isEmpty()) {
            Log.w(TAG, "No NVIDIA API key found. Falling back to on-device evaluator.")
            fallbackLocalAnalysis(targetLine, spokenText, stepIndex, highestPassedIndex, onResult)
            return
        }

        val systemPrompt = """
            You are an expert clinical optometrist and AI speech evaluator for the Vaachak visual acuity eye test.
            You evaluate what letters or statements the user spoke when attempting to read the target letters shown on screen at a specific Snellen acuity distance.
            
            Chart progression:
            - Step 1: 20/80 (Huge letters: ${EyeTestData.CHART_LINES[0].displayLetters}) - Baseline test for blindness.
            - Step 2: 20/63 (Very large print: ${EyeTestData.CHART_LINES[1].displayLetters})
            - Step 3: 20/50 (Large print: ${EyeTestData.CHART_LINES[2].displayLetters})
            - Step 4: 20/40 (Medium print: ${EyeTestData.CHART_LINES[3].displayLetters})
            - Step 5: 20/32 (Standard print: ${EyeTestData.CHART_LINES[4].displayLetters})
            - Step 6: 20/20 (Precision print: ${EyeTestData.CHART_LINES[6].displayLetters})
            
            Determine the visual impairment score (0 to 100) and optical determination:
            - COMPLETELY_BLIND: When the user states they cannot see anything, reports darkness/blindness, or fails on Step 1 (the huge 20/80 baseline letters). Score: 0 to 20.
            - PARTIALLY_BLIND: When the user can read Step 1 (20/80) but fails on Step 2 (20/63) or Step 3 (20/50). Indicates low vision / partial blindness. Score: 25 to 55.
            - MODERATE_MYOPIA: When the user reads large lines well but makes errors on Step 4 (20/40) or Step 5 (20/32). Score: 60 to 80.
            - NORMAL_VISION: When the user accurately reads Step 6 (20/20 precision line). Score: 85 to 100.
            
            Map spoken words into uppercase letters:
            - "zee"/"zed" -> Z, "el" -> L, "pee"/"pea" -> P, "ee" -> E, "dee" -> D, "tee"/"tea" -> T, "oh"/"zero" -> O, "see"/"sea" -> C, "eff" -> F, "bee" -> B, "kay" -> K, "em" -> M, "en" -> N.
            - If user speaks conversational words indicating inability to see, set heard_letters to [] and determination to COMPLETELY_BLIND.
            
            Return JSON ONLY with exact keys:
            {
               "accuracy_score": number (0-100),
               "correct_count": number,
               "misread_count": number,
               "heard_letters": ["A", "B", ...],
               "determination": "COMPLETELY_BLIND" | "PARTIALLY_BLIND" | "MODERATE_MYOPIA" | "NORMAL_VISION",
               "explanation": "concise clinical summary"
            }
        """.trimIndent()

        val userPrompt = "Current Step: ${stepIndex + 1} (Acuity: ${targetLine.standard}) - Expected letters: '${targetLine.displayLetters}'. Highest passed step so far: ${highestPassedIndex + 1}. The user spoke: '$spokenText'."

        val json = JSONObject()
        json.put("model", PRIMARY_MODEL)
        json.put("temperature", 0.1)
        json.put("max_tokens", 350)

        val messages = JSONArray()
        val sysMsg = JSONObject()
        sysMsg.put("role", "system")
        sysMsg.put("content", systemPrompt)
        messages.put(sysMsg)

        val usrMsg = JSONObject()
        usrMsg.put("role", "user")
        usrMsg.put("content", userPrompt)
        messages.put(usrMsg)

        json.put("messages", messages)

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(API_URL)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "NVIDIA API call failed, using local fallback", e)
                fallbackLocalAnalysis(targetLine, spokenText, stepIndex, highestPassedIndex, onResult)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val parsed = parseAiResponse(body, targetLine, spokenText, stepIndex, highestPassedIndex)
                        onResult(parsed)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse NVIDIA response: $body", e)
                        fallbackLocalAnalysis(targetLine, spokenText, stepIndex, highestPassedIndex, onResult)
                    }
                } else {
                    Log.e(TAG, "NVIDIA API returned error ${response.code}: $body")
                    fallbackLocalAnalysis(targetLine, spokenText, stepIndex, highestPassedIndex, onResult)
                }
            }
        })
    }

    private fun parseAiResponse(
        rawJson: String,
        targetLine: AcuityLine,
        spokenText: String,
        stepIndex: Int,
        highestPassedIndex: Int
    ): AiAcuityAnalysis {
        val jsonObject = JSONObject(rawJson)
        val content = jsonObject.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        // Clean any markdown code fences if present (e.g. ```json ... ```)
        val cleaned = content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val data = if (cleaned.startsWith("[")) {
            JSONArray(cleaned).getJSONObject(0)
        } else {
            JSONObject(cleaned)
        }

        val accuracyScore = data.optInt("accuracy_score", 0)
        val correctCount = data.optInt("correct_count", 0)
        val misreadCount = data.optInt("misread_count", 0)
        val explanation = data.optString("explanation", "NVIDIA AI visual acuity analysis complete.")

        val heardLetters = mutableListOf<String>()
        val lettersArray = data.optJSONArray("heard_letters")
        if (lettersArray != null) {
            for (i in 0 until lettersArray.length()) {
                heardLetters.add(lettersArray.getString(i))
            }
        } else {
            val lettersStr = data.optString("heard_letters", "")
            if (lettersStr.isNotEmpty()) {
                val tokens = lettersStr.split(" ", ",").filter { it.isNotBlank() }
                heardLetters.addAll(tokens)
            }
        }

        val determinationStr = data.optString("determination", "MODERATE_MYOPIA")
        val classification = when (determinationStr.uppercase()) {
            "COMPLETELY_BLIND" -> VisionClassification.COMPLETELY_BLIND
            "PARTIALLY_BLIND" -> VisionClassification.PARTIALLY_BLIND
            "MODERATE_MYOPIA" -> VisionClassification.MODERATE_MYOPIA
            "NORMAL_VISION" -> VisionClassification.NORMAL_VISION
            else -> {
                if (stepIndex == 0 && accuracyScore < 50) VisionClassification.COMPLETELY_BLIND
                else if (stepIndex <= 2 && accuracyScore < 50) VisionClassification.PARTIALLY_BLIND
                else if (stepIndex <= 4) VisionClassification.MODERATE_MYOPIA
                else VisionClassification.NORMAL_VISION
            }
        }

        return AiAcuityAnalysis(
            accuracyScore = accuracyScore,
            correctCount = correctCount,
            misreadCount = misreadCount,
            heardLetters = heardLetters,
            classification = classification,
            clinicalExplanation = explanation,
            isAiPowered = true
        )
    }

    private fun fallbackLocalAnalysis(
        targetLine: AcuityLine,
        spokenText: String,
        stepIndex: Int,
        highestPassedIndex: Int,
        onResult: (AiAcuityAnalysis) -> Unit
    ) {
        val localEval = EyeTestVoiceEvaluator.evaluate(targetLine.letters, spokenText)
        val isBlindStatement = EyeTestVoiceEvaluator.isBlindOrCannotSee(spokenText)

        val newPassedIndex = if (localEval.isPassThreshold && !isBlindStatement) {
            maxOf(highestPassedIndex, stepIndex)
        } else {
            highestPassedIndex
        }

        val classification = if (isBlindStatement || (stepIndex == 0 && !localEval.isPassThreshold)) {
            VisionClassification.COMPLETELY_BLIND
        } else {
            EyeTestData.classifyVision(newPassedIndex)
        }

        val overallScore = if (isBlindStatement) {
            0
        } else {
            EyeTestData.calculateAcuityScore(newPassedIndex, localEval.accuracyPercent)
        }

        val explanation = when (classification) {
            VisionClassification.COMPLETELY_BLIND ->
                "Unable to identify the largest baseline 20/80 letters. Completely blind profile detected."
            VisionClassification.PARTIALLY_BLIND ->
                "Can distinguish large baseline print, but low vision prevents reading standard 20/50 acuity targets. Partially blind profile."
            VisionClassification.MODERATE_MYOPIA ->
                "Reads large and medium print with moderate visual acuity (${localEval.correctCount} of ${localEval.totalCount} correct)."
            VisionClassification.NORMAL_VISION ->
                "High visual acuity. Accurately resolved fine print 20/20 target lines."
        }

        val analysis = AiAcuityAnalysis(
            accuracyScore = overallScore,
            correctCount = localEval.correctCount,
            misreadCount = localEval.misreadCount,
            heardLetters = localEval.letterEvaluations.mapNotNull { it.spokenLetter?.toString() },
            classification = classification,
            clinicalExplanation = explanation,
            isAiPowered = false
        )
        onResult(analysis)
    }
}
