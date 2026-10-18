package com.example.smartblindstick.eyetest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Clean, Accessible, Minimalist Visual Acuity Eye Test Screen designed specifically
 * for visually impaired and blind users.
 *
 * Features:
 *  - DYNAMIC LETTER SIZING: Letters visibly shrink step-by-step as the user talks and advances
 *    (76sp down to 18sp).
 *  - ACCURATE BLINDNESS EVALUATION: Accurately differentiates Completely Blind vs Partially Blind
 *    vs Myopia vs Normal Vision powered by Vaachak AI.
 *  - CLEAN SPEECH RECORDING: Correctly parses letters and displays what was spoken without corruption.
 *  - MINIMALIST & ACCESSIBLE UI: Large high-contrast touch targets (60-70dp buttons), uncluttered layout,
 *    and audio-first accessibility.
 */
@Composable
fun EyeTestScreen(
    latestSpokenText: String = "",
    rmsLevel: Float = 0f,
    onSpeakInstruction: (String) -> Unit,
    onSpeechRecognitionRequest: () -> Unit,
    onNavigateToSurroundings: () -> Unit,
    onNavigateToTextReader: () -> Unit,
    onNavigateToHelp: () -> Unit,
    activeTab: String = "Eye Test",
    modifier: Modifier = Modifier
) {
    val totalSteps = 6

    // Starts on Step 1 (Index 0: 20/80 - Giant Letters T Z O) to calibrate blindness baseline
    var currentLineIndex by remember { mutableIntStateOf(0) }
    var highestPassedLineIndex by remember { mutableIntStateOf(-1) }
    var isDarkHighContrast by remember { mutableStateOf(true) } // High contrast dark theme by default for accessibility
    var showDiagnosisDialog by remember { mutableStateOf(false) }
    var detectedClassification by remember { mutableStateOf(VisionClassification.COMPLETELY_BLIND) }

    val currentLine = EyeTestData.CHART_LINES[currentLineIndex]

    // Dynamic Font Size that shrinks every time user speaks/advances
    val targetFontSize = EyeTestData.getAcuityFontSize(currentLineIndex).value
    val animatedFontSize by animateFloatAsState(
        targetValue = targetFontSize,
        label = "acuityFontSize"
    )

    // Current Line Evaluation
    var currentEvaluation by remember {
        mutableStateOf(
            EvaluationResult(
                letterEvaluations = currentLine.letters.map { LetterEvaluation(it, null, false) },
                heardFormatted = "Listening for your voice...",
                accuracyPercent = 0,
                correctCount = 0,
                totalCount = currentLine.letters.size,
                misreadCount = 0,
                isPassThreshold = false,
                rawSpokenText = ""
            )
        )
    }

    // AI analysis result from Vaachak API
    var latestAiAnalysis by remember { mutableStateOf<AiAcuityAnalysis?>(null) }
    var isAiAnalyzing by remember { mutableStateOf(false) }

    // Audio instruction on initial load or step change
    fun speakStepPrompt(index: Int) {
        val line = EyeTestData.CHART_LINES[index]
        val prompt = "Step ${index + 1} of $totalSteps. Acuity ${line.standard}. " +
                "Letters are: ${line.letters.joinToString(", ")}. " +
                "Hold your phone 14 inches from your eyes and read aloud, or tap the big green button to speak."
        onSpeakInstruction(prompt)
    }

    // Advance to smaller font size upon reading
    fun advanceToNextLine() {
        if (currentLineIndex < totalSteps - 1) {
            val nextIndex = currentLineIndex + 1
            currentLineIndex = nextIndex
            val nextLine = EyeTestData.CHART_LINES[nextIndex]

            currentEvaluation = EvaluationResult(
                letterEvaluations = nextLine.letters.map { LetterEvaluation(it, null, false) },
                heardFormatted = "Listening...",
                accuracyPercent = 0,
                correctCount = 0,
                totalCount = nextLine.letters.size,
                misreadCount = 0,
                isPassThreshold = false,
                rawSpokenText = ""
            )
            latestAiAnalysis = null

            speakStepPrompt(nextIndex)
            onSpeechRecognitionRequest()
        } else {
            // Reached end of 20/20 chart
            detectedClassification = EyeTestData.classifyVision(highestPassedLineIndex)
            showDiagnosisDialog = true
            onSpeakInstruction("Acuity calibration finished. Final vision diagnosis: ${detectedClassification.title}.")
        }
    }

    // Handle user saying "I cannot see"
    fun handleCannotSee() {
        val classification = if (currentLineIndex <= 1) {
            VisionClassification.COMPLETELY_BLIND
        } else {
            VisionClassification.PARTIALLY_BLIND
        }
        val score = if (classification == VisionClassification.COMPLETELY_BLIND) 0 else 35

        detectedClassification = classification
        latestAiAnalysis = AiAcuityAnalysis(
            accuracyScore = score,
            correctCount = 0,
            misreadCount = currentLine.letters.size,
            heardLetters = emptyList(),
            classification = classification,
            clinicalExplanation = "User indicated inability to see letters at ${currentLine.standard} distance.",
            isAiPowered = false
        )
        currentEvaluation = currentEvaluation.copy(
            heardFormatted = "User indicated: Cannot see letters",
            accuracyPercent = score
        )
        showDiagnosisDialog = true
        onSpeakInstruction("Vision impairment detected: ${classification.title}. Activating full audio guidance.")
    }

    // Process incoming voice speech
    LaunchedEffect(latestSpokenText) {
        val trimmed = latestSpokenText.trim()
        if (trimmed.isNotEmpty()) {
            if (EyeTestVoiceEvaluator.isBlindOrCannotSee(trimmed)) {
                handleCannotSee()
            } else if (EyeTestVoiceEvaluator.isNextCommand(trimmed)) {
                advanceToNextLine()
            } else if (EyeTestVoiceEvaluator.isInstructionsCommand(trimmed)) {
                speakStepPrompt(currentLineIndex)
            } else {
                isAiAnalyzing = true
                val aiResult = NvidiaAcuityAnalyzer.analyzeSpeech(
                    targetLine = currentLine,
                    spokenText = trimmed,
                    stepIndex = currentLineIndex,
                    highestPassedIndex = highestPassedLineIndex
                )
                isAiAnalyzing = false
                latestAiAnalysis = aiResult

                val localEval = EyeTestVoiceEvaluator.evaluate(currentLine.letters, trimmed)
                currentEvaluation = localEval.copy(
                    accuracyPercent = aiResult.accuracyScore,
                    correctCount = aiResult.correctCount,
                    misreadCount = aiResult.misreadCount
                )

                if (localEval.isPassThreshold || aiResult.accuracyScore >= 60) {
                    highestPassedLineIndex = maxOf(highestPassedLineIndex, currentLineIndex)
                }

                // Overall clinical classification updated based on progress
                detectedClassification = if (highestPassedLineIndex < 0 && currentLineIndex == 0 && aiResult.accuracyScore < 40) {
                    VisionClassification.COMPLETELY_BLIND
                } else if (highestPassedLineIndex in 0..2) {
                    VisionClassification.PARTIALLY_BLIND
                } else if (highestPassedLineIndex in 3..4) {
                    VisionClassification.MODERATE_MYOPIA
                } else {
                    VisionClassification.NORMAL_VISION
                }

                // Announce feedback clearly through TTS
                val speechFeedback = buildString {
                    append("Heard: ${currentEvaluation.heardFormatted}. ")
                    append("Score: ${aiResult.accuracyScore} percent. ")
                    if (currentLineIndex < totalSteps - 1) {
                        append("Letters will now get smaller for next level.")
                    }
                }
                onSpeakInstruction(speechFeedback)

                // If user fails completely on Step 1 baseline, trigger diagnosis modal
                if (currentLineIndex == 0 && aiResult.accuracyScore < 40 && localEval.correctCount == 0) {
                    delay(1500)
                    showDiagnosisDialog = true
                } else {
                    // Automatically shrinks letters and advances to next line after speaking
                    delay(3000)
                    advanceToNextLine()
                }
            }
        }
    }

    // High Contrast Theme Colors for Vision Accessibility
    val bgColor = if (isDarkHighContrast) Color(0xFF000000) else Color(0xFFFFFFFF)
    val cardBg = if (isDarkHighContrast) Color(0xFF121212) else Color(0xFFF8FAFC)
    val cardBorder = if (isDarkHighContrast) Color(0xFF333333) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkHighContrast) Color(0xFFFFFFFF) else Color(0xFF0F172A)
    val textSecondary = if (isDarkHighContrast) Color(0xFFA1A1AA) else Color(0xFF475569)
    val accentGreen = Color(0xFF00E676)
    val primaryBtnBg = if (isDarkHighContrast) Color(0xFF00E676) else Color(0xFF059669)
    val primaryBtnText = if (isDarkHighContrast) Color(0xFF000000) else Color(0xFFFFFFFF)
    val misreadColor = Color(0xFFFF5252)

    Scaffold(
        bottomBar = {
            EyeTestBottomNavBar(
                activeTab = activeTab,
                onTabSelected = { tab ->
                    when (tab) {
                        "Surroundings" -> onNavigateToSurroundings()
                        "Text Reader" -> onNavigateToTextReader()
                        "Help" -> onNavigateToHelp()
                    }
                },
                isDark = isDarkHighContrast
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. MINIMALIST TOP BAR: App Title, Level Indicator, Contrast Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "VAACHAK EYE TEST",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = textPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Step ${currentLineIndex + 1} of $totalSteps • ${EyeTestData.getAcuityScaleLabel(currentLineIndex)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentGreen
                    )
                }

                // Big accessible High-Contrast Toggle Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isDarkHighContrast) Color(0xFF27272A) else Color(0xFFE2E8F0))
                        .clickable { isDarkHighContrast = !isDarkHighContrast },
                    contentAlignment = Alignment.Center
                ) {
                    EyeTestIcons.ContrastToggleIcon(
                        modifier = Modifier.size(24.dp),
                        color = textPrimary
                    )
                }
            }

            // 2. HERO ACUITY LETTER DISPLAY AREA
            // Dynamically decreases in font size as the user talks and advances!
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(2.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Acuity Standard Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDarkHighContrast) Color(0xFF1E293B) else Color(0xFFE0E7FF))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${currentLine.standard} Acuity Standard",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDarkHighContrast) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // DYNAMIC LETTERS: Font size animates smaller with each step!
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        currentLine.letters.forEachIndexed { index, letter ->
                            val eval = currentEvaluation.letterEvaluations.getOrNull(index)
                            val isMisread = eval != null && eval.spokenLetter != null && !eval.isCorrect
                            val isCorrect = eval != null && eval.isCorrect

                            val letterColor = when {
                                isMisread -> misreadColor
                                isCorrect -> accentGreen
                                else -> textPrimary
                            }

                            Text(
                                text = letter.toString(),
                                fontSize = animatedFontSize.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Serif,
                                color = letterColor,
                                modifier = Modifier.padding(horizontal = if (currentLine.letters.size > 5) 4.dp else 10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Hold phone 14 inches away. Size decreases as you speak.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 3. MINIMALIST VOICE TRANSCRIPTION & ACUITY STATUS
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header row: Status & Score
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EyeTestIcons.MicIcon(
                                modifier = Modifier.size(20.dp),
                                color = accentGreen
                            )
                            Text(
                                text = if (isAiAnalyzing) "Analyzing Voice..." else "Voice Status",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        }

                        // Acuity Score Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDarkHighContrast) Color(0xFF064E3B) else Color(0xFFD1FAE5))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Score: ${currentEvaluation.accuracyPercent}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isDarkHighContrast) Color(0xFF6EE7B7) else Color(0xFF065F46)
                            )
                        }
                    }

                    // Large clearly readable Heard Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDarkHighContrast) Color(0xFF18181B) else Color(0xFFF1F5F9))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column {
                            Text(
                                text = "HEARD FROM YOU:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentEvaluation.heardFormatted,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary
                            )
                        }
                    }

                    // Diagnosis diagnosis status
                    latestAiAnalysis?.let { ai ->
                        Text(
                            text = "Vaachak AI: ${ai.classification.title} — ${ai.clinicalExplanation}",
                            fontSize = 12.sp,
                            color = textSecondary,
                            lineHeight = 16.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }

            // 4. HUGE ACCESSIBLE ACTION BUTTONS (Designed for visually impaired users)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary Giant Button: Tap to Speak / Next Step (Height 70dp)
                Button(
                    onClick = {
                        onSpeechRecognitionRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryBtnBg,
                        contentColor = primaryBtnText
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        EyeTestIcons.MicIcon(
                            modifier = Modifier.size(26.dp),
                            color = primaryBtnText
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Tap to Speak / Read",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Secondary Giant Button: Read Aloud Instructions & Letters (Height 62dp)
                OutlinedButton(
                    onClick = {
                        speakStepPrompt(currentLineIndex)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = cardBg,
                        contentColor = textPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(2.dp, cardBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        EyeTestIcons.SpeakerIcon(
                            modifier = Modifier.size(24.dp),
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Listen to Letters & Help",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }
                }

                // Third Accessible Button: "I Cannot See" for Blind / Low Vision Users (Height 58dp)
                Button(
                    onClick = {
                        handleCannotSee()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkHighContrast) Color(0xFF450A0A) else Color(0xFFFEE2E2),
                        contentColor = Color(0xFFEF4444)
                    )
                ) {
                    Text(
                        text = "I Cannot See The Letters",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFDC2626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // 5. ACCESSIBLE DIAGNOSIS MODAL
        if (showDiagnosisDialog) {
            AlertDialog(
                onDismissRequest = { showDiagnosisDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(detectedClassification.badgeColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            EyeTestIcons.EyeIcon(modifier = Modifier.size(22.dp), color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Vision Assessment",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Diagnosis: ${detectedClassification.title}",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color(detectedClassification.badgeColorHex)
                        )

                        Text(
                            text = "Acuity Impairment Score: ${currentEvaluation.accuracyPercent} / 100",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )

                        Text(
                            text = latestAiAnalysis?.clinicalExplanation ?: detectedClassification.description,
                            fontSize = 14.sp,
                            color = textSecondary
                        )

                        HorizontalDivider()

                        Text(
                            text = when (detectedClassification) {
                                VisionClassification.COMPLETELY_BLIND ->
                                    "Completely blind mode engaged. Continuous audio narration and ultrasonic obstacle detection are now active."
                                VisionClassification.PARTIALLY_BLIND ->
                                    "Partially blind mode engaged. High-contrast typography and camera text reading are now active."
                                else ->
                                    "Standard assistive mode configured based on your visual acuity profile."
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                    }
                },
                confirmButton = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                showDiagnosisDialog = false
                                onNavigateToSurroundings()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBtnBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("Open Surroundings Mode", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryBtnText)
                        }

                        OutlinedButton(
                            onClick = {
                                showDiagnosisDialog = false
                                onNavigateToTextReader()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text("Open Text Reader", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDiagnosisDialog = false
                        currentLineIndex = 0
                        highestPassedLineIndex = -1
                        speakStepPrompt(0)
                        onSpeechRecognitionRequest()
                    }) {
                        Text("Retest From Beginning", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

/**
 * Bottom Navigation Bar matching the 4 tabs:
 * - Eye Test (Eye icon)
 * - Surroundings (Compass icon)
 * - Text Reader (Document icon)
 * - Help (Question mark icon)
 */
@Composable
fun EyeTestBottomNavBar(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val navBg = if (isDark) Color(0xFF121212) else Color.White
    val selectedGreen = Color(0xFF00E676)
    val unselectedGray = if (isDark) Color(0xFF71717A) else Color(0xFF9CA3AF)

    val items = listOf(
        Pair("Eye Test", "eye"),
        Pair("Surroundings", "compass"),
        Pair("Text Reader", "text"),
        Pair("Help", "help")
    )

    Surface(
        color = navBg,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF27272A) else Color(0xFFE5E7EB)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (title, type) ->
                val isSelected = activeTab == title
                val color = if (isSelected) selectedGreen else unselectedGray

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onTabSelected(title) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    when (type) {
                        "eye" -> EyeTestIcons.EyeIcon(modifier = Modifier.size(24.dp), color = color)
                        "compass" -> EyeTestIcons.CompassIcon(modifier = Modifier.size(24.dp), color = color)
                        "text" -> EyeTestIcons.TextReaderIcon(modifier = Modifier.size(24.dp), color = color)
                        "help" -> EyeTestIcons.HelpIcon(modifier = Modifier.size(24.dp), color = color)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = color
                    )
                }
            }
        }
    }
}
