package com.example.smartblindstick

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.BeforeClass
import android.os.ParcelFileDescriptor

class RenderUiTest {
    companion object {
        @BeforeClass @JvmStatic fun grantEmulatorPermissions() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            for (permission in listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO")) {
                val descriptor = instrumentation.uiAutomation.executeShellCommand("pm grant ${BuildConfig.APPLICATION_ID} $permission")
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        }
    }
    @get:Rule val compose = createAndroidComposeRule<VaachakMainActivity>()
    @Test fun appOpensPlaygroundAndCallsRender() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveRender") == "true")
        compose.waitUntil(15000) {
            runCatching { compose.onAllNodesWithText("Help").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithText("Help").performClick()
        compose.onNodeWithText("Open Cloud API Playground").performClick()
        compose.onNodeWithText("https://vaachakfastapibackend.onrender.com/").assertIsDisplayed()
        compose.onNodeWithText("Send request").performClick()
        compose.waitUntil(60000) {
            compose.onAllNodesWithText("HTTP 200 · healthy", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("HTTP 200 · healthy", substring = true).assertIsDisplayed()
    }
}
