package com.example.smartblindstick

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.camera.core.Camera

/**
 * Intelligent Automatic Flashlight Manager for Visually Impaired Assistance.
 *
 * Prevents premature startup activation:
 *  - Uses the hardware Ambient Light Sensor (Sensor.TYPE_LIGHT in Lux) combined with Camera Luminance.
 *  - When the surrounding environment is bright enough, the flashlight strictly stays OFF.
 *  - When the environment goes dark beyond the threshold (sustained low light), the torch automatically turns ON.
 *  - When the environment brightens up again, the torch automatically turns OFF.
 *  - Includes a startup grace period so initial camera exposure calibration never triggers false activation.
 */
class AutoFlashlightManager(
    private val context: Context,
    private val onSpeak: (String) -> Unit
) : SensorEventListener {

    private var camera: Camera? = null
    private var isTorchOn = false

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private var latestAmbientLux: Float? = null
    private var monitoringStartTime: Long = 0L

    // Debouncing/Hysteresis state
    private var darkFrameCount = 0
    private var brightFrameCount = 0

    // Startup grace period: Camera exposure takes 2.5s to stabilize; ignore initial dark frames
    private val STARTUP_GRACE_PERIOD_MS = 2500L

    // Optical Thresholds
    // Ambient Lux: > 25 is normal indoor lighting, < 8 is genuinely dark/night
    private val LUX_DARK_THRESHOLD = 8.0f
    private val LUX_BRIGHT_THRESHOLD = 25.0f

    // Camera Luminance (0 - 255): Normal lit room is 70-150; dark room is < 30
    private val LUMA_DARK_THRESHOLD = 30.0f
    private val LUMA_BRIGHT_THRESHOLD = 65.0f

    // Consecutive sustained readings to prevent flickering or momentary shadows
    private val REQUIRED_CONSECUTIVE_DARK = 8
    private val REQUIRED_CONSECUTIVE_BRIGHT = 5

    var isManualOverride = false

    fun setCamera(camera: Camera?) {
        this.camera = camera
        // Reset counters and update start time when a new camera session binds
        monitoringStartTime = System.currentTimeMillis()
        darkFrameCount = 0
        brightFrameCount = 0
    }

    fun startMonitoring() {
        monitoringStartTime = System.currentTimeMillis()
        darkFrameCount = 0
        brightFrameCount = 0

        lightSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("AutoFlashlight", "Ambient light sensor registered (Sensor.TYPE_LIGHT)")
        }
        Log.d("AutoFlashlight", "Started ambient light monitoring")
    }

    fun stopMonitoring() {
        sensorManager?.unregisterListener(this)
        setTorch(false)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            latestAmbientLux = lux
            evaluateEnvironmentalLight()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Called on every camera frame with calculated average luminance (0-255).
     */
    fun processLuminance(averageLuminance: Float) {
        if (camera == null || !camera!!.cameraInfo.hasFlashUnit() || isManualOverride) return

        // 1. Startup Grace Period check: do not auto-enable while camera is initializing
        val elapsed = System.currentTimeMillis() - monitoringStartTime
        if (elapsed < STARTUP_GRACE_PERIOD_MS) {
            darkFrameCount = 0
            return
        }

        // 2. If Ambient Light Sensor reported bright lux (> 25 lux), environment is bright enough: keep torch OFF
        val lux = latestAmbientLux
        if (lux != null && lux > LUX_BRIGHT_THRESHOLD) {
            darkFrameCount = 0
            if (isTorchOn) {
                brightFrameCount++
                if (brightFrameCount >= REQUIRED_CONSECUTIVE_BRIGHT) {
                    setTorch(false)
                    brightFrameCount = 0
                }
            }
            return
        }

        // 3. Luminance evaluation
        if (averageLuminance < LUMA_DARK_THRESHOLD && (!isTorchOn)) {
            // Check that hardware sensor doesn't contradict camera (if lux sensor is present)
            if (lux == null || lux < LUX_DARK_THRESHOLD) {
                darkFrameCount++
                brightFrameCount = 0
                if (darkFrameCount >= REQUIRED_CONSECUTIVE_DARK) {
                    setTorch(true)
                    onSpeak("Dark environment detected. Flashlight auto-enabled.")
                    darkFrameCount = 0
                }
            } else {
                darkFrameCount = 0
            }
        } else if (averageLuminance > LUMA_BRIGHT_THRESHOLD && isTorchOn) {
            brightFrameCount++
            darkFrameCount = 0
            if (brightFrameCount >= REQUIRED_CONSECUTIVE_BRIGHT) {
                setTorch(false)
                brightFrameCount = 0
            }
        } else {
            // Stable mid-range: reset counts
            darkFrameCount = 0
            brightFrameCount = 0
        }
    }

    private fun evaluateEnvironmentalLight() {
        val lux = latestAmbientLux ?: return
        if (camera == null || !camera!!.cameraInfo.hasFlashUnit() || isManualOverride) return

        val elapsed = System.currentTimeMillis() - monitoringStartTime
        if (elapsed < STARTUP_GRACE_PERIOD_MS) return

        // If surrounding environment is bright enough, turn torch OFF immediately
        if (lux >= LUX_BRIGHT_THRESHOLD && isTorchOn) {
            setTorch(false)
            darkFrameCount = 0
        }
    }

    fun toggleTorch(enable: Boolean) {
        isManualOverride = true
        setTorch(enable)
        if (enable) {
            onSpeak("Flashlight manually turned on. Auto mode paused.")
        } else {
            onSpeak("Flashlight manually turned off. Auto mode paused.")
        }
    }

    fun resetToAuto() {
        isManualOverride = false
        onSpeak("Auto flashlight resumed.")
    }

    private fun setTorch(enable: Boolean) {
        try {
            camera?.cameraControl?.enableTorch(enable)
            isTorchOn = enable
            Log.d("AutoFlashlight", "Torch set to $enable")
        } catch (e: Exception) {
            Log.e("AutoFlashlight", "Failed to set torch", e)
        }
    }
}
