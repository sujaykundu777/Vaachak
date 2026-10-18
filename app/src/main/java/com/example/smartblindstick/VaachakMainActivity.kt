package com.example.smartblindstick

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.smartblindstick.eyetest.EyeTestBottomNavBar
import com.example.smartblindstick.eyetest.EyeTestScreen
import com.example.smartblindstick.eyetest.HelpScreen
import com.example.smartblindstick.eyetest.TextReaderScreen
import com.example.smartblindstick.ui.theme.SmartBlindStickTheme
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * ============================================================================
 * NEW APP FILE: Vaachak (Assistive App for the Visually Impaired)
 * ============================================================================
 * This is the new, enhanced application implementation featuring:
 *  - Automatic Visual Acuity Calibration at startup (Precision Chart / eye test.png)
 *  - Real-time speech transcription & automatic next-word printing upon completion
 *  - Waveform visualizer, accuracy % pill, and misread letter highlights
 *  - Clinical vision profile classification (Completely Blind vs Partially Blind vs Normal)
 *  - 4-tab modern accessibility navigation (Eye Test, Surroundings, Text Reader, Help)
 *  - Hand-free voice commands and adaptive high-contrast / audio modes
 * ============================================================================
 */
open class VaachakMainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    protected lateinit var cameraExecutor: ExecutorService
    protected lateinit var tts: TextToSpeech
    protected lateinit var compassManager: CompassManager
    
    protected lateinit var fallDetectionManager: FallDetectionManager
    protected lateinit var navigationManager: NavigationManager
    protected lateinit var visionProcessor: VisionProcessor
    protected lateinit var voiceAssistantManager: VoiceAssistantManager
    protected lateinit var ocrProcessor: OcrProcessor
    
    // Feature managers
    protected lateinit var spatialAudioManager: SpatialAudioManager
    protected lateinit var visualMemoryManager: VisualMemoryManager
    protected lateinit var microNavigationManager: MicroNavigationManager
    protected lateinit var autoFlashlightManager: AutoFlashlightManager
    
    @Volatile
    protected var isOcrRequested = false
    protected var ocrRequestTime = 0L
    @Volatile
    protected var isNvidiaRequested = false
    @Volatile
    protected var nvidiaPrompt = ""

    protected val currentTabState = mutableStateOf("Eye Test")
    protected val eyeTestSpeechText = mutableStateOf("")
    protected val eyeTestRms = mutableFloatStateOf(0f)

    protected val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.VIBRATE
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) allGranted = false
        }
        if (allGranted) {
            fallDetectionManager.startListening()
        } else {
            Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        tts = TextToSpeech(this, this)
        fallDetectionManager = FallDetectionManager(this)
        navigationManager = NavigationManager(this)
        visionProcessor = VisionProcessor(this)
        ocrProcessor = OcrProcessor(this)
        
        compassManager = CompassManager(this)
        spatialAudioManager = SpatialAudioManager { msg, pan, vol, pitch -> speak(msg, pan, vol, pitch, false) }
        visualMemoryManager = VisualMemoryManager(this) { msg, pan, vol, pitch -> speak(msg, pan, vol, pitch, true) }
        visualMemoryManager.compassManager = compassManager
        microNavigationManager = MicroNavigationManager(this) { msg -> speak(msg, isHighPriority = false) }
        autoFlashlightManager = AutoFlashlightManager(this) { msg -> speak(msg, isHighPriority = false) }
        
        visionProcessor.spatialAudioManager = spatialAudioManager
        visionProcessor.visualMemoryManager = visualMemoryManager
        visionProcessor.microNavigationManager = microNavigationManager
        visionProcessor.autoFlashlightManager = autoFlashlightManager
        
        autoFlashlightManager.startMonitoring()
        compassManager.start()
        
        voiceAssistantManager = VoiceAssistantManager(this) { command ->
            runOnUiThread {
                handleVoiceCommand(command)
            }
        }

        voiceAssistantManager.onEyeTestSpeechListener = { text ->
            runOnUiThread {
                eyeTestSpeechText.value = text
            }
        }
        voiceAssistantManager.onRmsChangedListener = { rms ->
            runOnUiThread {
                eyeTestRms.floatValue = rms
            }
        }
        voiceAssistantManager.isEyeTestMode = true
        
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            fallDetectionManager.startListening()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        setContent {
            SmartBlindStickTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentTabState.value) {
                        "Eye Test" -> {
                            voiceAssistantManager.isEyeTestMode = true
                            EyeTestScreen(
                                latestSpokenText = eyeTestSpeechText.value,
                                rmsLevel = eyeTestRms.floatValue,
                                onSpeakInstruction = { instruction ->
                                    speak(instruction, isHighPriority = true)
                                },
                                onSpeechRecognitionRequest = {
                                    tts.stop()
                                    voiceAssistantManager.startListening()
                                },
                                onNavigateToSurroundings = {
                                    voiceAssistantManager.isEyeTestMode = false
                                    currentTabState.value = "Surroundings"
                                },
                                onNavigateToTextReader = {
                                    voiceAssistantManager.isEyeTestMode = false
                                    currentTabState.value = "Text Reader"
                                },
                                onNavigateToHelp = {
                                    voiceAssistantManager.isEyeTestMode = false
                                    currentTabState.value = "Help"
                                },
                                activeTab = "Eye Test"
                            )
                        }
                        "Surroundings" -> {
                            voiceAssistantManager.isEyeTestMode = false
                            Box(modifier = Modifier.fillMaxSize()) {
                                MainScreen(
                                    cameraExecutor = cameraExecutor,
                                    visionProcessor = visionProcessor,
                                    onAnalyzeImage = { imageProxy ->
                                        if (isOcrRequested) {
                                            ocrProcessor.processImageProxy(imageProxy) { detectedText ->
                                                isOcrRequested = false
                                                speak("I read: $detectedText", isHighPriority = true)
                                            }
                                            if (System.currentTimeMillis() - ocrRequestTime > 5000 && isOcrRequested) {
                                                isOcrRequested = false
                                                speak("I couldn't find any clear text.", isHighPriority = true)
                                            }
                                        } else if (isNvidiaRequested) {
                                            isNvidiaRequested = false
                                            val currentPrompt = nvidiaPrompt
                                            visionProcessor.processForNvidia(imageProxy, currentPrompt) { answer ->
                                                speak(answer, isHighPriority = true)
                                            }
                                        } else {
                                            visionProcessor.processImageProxy(imageProxy)
                                        }
                                    },
                                    onCameraBound = { camera ->
                                        autoFlashlightManager.setCamera(camera)
                                    },
                                    onMicClick = {
                                        tts.stop()
                                        voiceAssistantManager.startListening()
                                    }
                                )

                                EyeTestBottomNavBar(
                                    activeTab = currentTabState.value,
                                    onTabSelected = { tab ->
                                        currentTabState.value = tab
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                        "Text Reader" -> {
                            voiceAssistantManager.isEyeTestMode = false
                            TextReaderScreen(
                                onReadRequested = {
                                    isOcrRequested = true
                                    ocrRequestTime = System.currentTimeMillis()
                                    currentTabState.value = "Surroundings"
                                    speak("Point your camera at text to read aloud", isHighPriority = true)
                                },
                                onTabSelected = { tab -> currentTabState.value = tab }
                            )
                        }
                        "Help" -> {
                            voiceAssistantManager.isEyeTestMode = false
                            HelpScreen(
                                onSpeak = { speak(it, isHighPriority = true) },
                                onTabSelected = { tab -> currentTabState.value = tab }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported")
            } else {
                speak("Welcome to Vaachak. Eye Test initialized. Read the displayed letters aloud to calibrate your vision profile.", isHighPriority = true)
            }
        }
    }

    protected open fun handleVoiceCommand(command: String) {
        when {
            command.contains("read") -> {
                isOcrRequested = true
                ocrRequestTime = System.currentTimeMillis()
                speak("Let me read that for you.", isHighPriority = true)
            }
            command.contains("stop") -> {
                tts.stop()
                speak("Stopped.", isHighPriority = true)
            }
            command.contains("turn on flashlight") || command.contains("turn flashlight on") || command.contains("torch on") -> {
                autoFlashlightManager.toggleTorch(true)
            }
            command.contains("turn off flashlight") || command.contains("turn flashlight off") || command.contains("torch off") -> {
                autoFlashlightManager.toggleTorch(false)
            }
            command.contains("auto flashlight") || command.contains("automatic flashlight") -> {
                autoFlashlightManager.resetToAuto()
            }
            command.contains("ask nvidia") || command.contains("nvidia") || command.contains("describe") -> {
                val prompt = command.replace("ask nvidia", "").replace("nvidia", "").trim()
                nvidiaPrompt = if (prompt.isEmpty()) "Describe what you see in front of me." else prompt
                isNvidiaRequested = true
                speak("Asking Nvidia...", isHighPriority = true)
            }
            command.contains("go to") || command.contains("navigate to") -> {
                val destination = command.replace("go to", "").replace("navigate to", "").trim()
                if (destination.isNotEmpty()) {
                    speak("Starting navigation to $destination", isHighPriority = true)
                    navigationManager.startNavigation(destination)
                } else {
                    speak("Please specify a destination.", isHighPriority = true)
                }
            }
            command.contains("switch to spatial") || command.contains("spacial") || command.contains("special") -> {
                spatialAudioManager.setEnabled(true)
                speak("Spatial audio enabled", isHighPriority = true)
            }
            command.contains("switch to voice") || command.contains("voice") -> {
                spatialAudioManager.setEnabled(false)
                speak("Spatial audio disabled, voice enabled", isHighPriority = true)
            }
            command.contains("where is my") || command.contains("where did i put my") -> {
                speak("Let me check my visual memory.", isHighPriority = true)
                visualMemoryManager.answerQuery(command)
            }
            command.contains("transit mode") || command.contains("find a door") -> {
                microNavigationManager.currentMode = NavMode.TRANSIT
                nvidiaPrompt = "I am looking for a door or an exit. Tell me exactly where it is using a 3x3 grid (e.g. 'door is center'). If you don't see one, say 'No doors in current view'."
                isNvidiaRequested = true
                speak("Scanning for doors and transit vehicles...", isHighPriority = true)
            }
            command.contains("find a seat") || command.contains("find a bed") -> {
                microNavigationManager.currentMode = NavMode.SEAT
                nvidiaPrompt = "Differentiate between any beds, chairs, couches, or doors in this view. Tell me their exact locations using a 3x3 grid (e.g. 'bed is bottom left', 'door is center')."
                isNvidiaRequested = true
                speak("Scanning room layout for seats, beds, and doors...", isHighPriority = true)
            }
            command.contains("find a cup") || command.contains("find a bottle") -> {
                microNavigationManager.currentMode = NavMode.CUP
            }
            command.startsWith("find ") -> {
                val target = command.replace("find ", "").trim()
                if (target.isNotEmpty()) {
                    microNavigationManager.startFinding(target)
                    nvidiaPrompt = "I am looking for a $target. Do you see it in this image? If so, tell me exactly where it is using a 3x3 grid (e.g. 'top left', 'center', 'bottom right') and provide a brief description. If you don't see it, reply EXACTLY with 'Not found in current view'."
                    isNvidiaRequested = true
                    speak("Scanning for $target...", isHighPriority = true)
                }
            }
            command.contains("normal mode") -> {
                microNavigationManager.currentMode = NavMode.NORMAL
            }
            else -> {
                speak("Command not recognized. Please try again.", isHighPriority = true)
            }
        }
    }

    protected var currentSpeechIsHighPriority = false

    fun speak(text: String, pan: Float = 0f, volume: Float = 1.0f, pitch: Float = 1.0f, isHighPriority: Boolean = false) {
        if (tts.isSpeaking && currentSpeechIsHighPriority && !isHighPriority) {
            return
        }
        
        currentSpeechIsHighPriority = isHighPriority
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, pan.coerceIn(-1.0f, 1.0f))
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0.0f, 1.0f))
        tts.setPitch(pitch.coerceIn(0.5f, 2.0f))
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "")
    }

    protected fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tts.stop()
        tts.shutdown()
        fallDetectionManager.stopListening()
        voiceAssistantManager.destroy()
        autoFlashlightManager.stopMonitoring()
        compassManager.stop()
    }
}
