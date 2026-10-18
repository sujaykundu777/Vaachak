package com.example.smartblindstick.eyetest

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Data models and visual acuity standards derived from the Precision Vision
 * Visual Acuity chart (eye test.png).
 */
data class AcuityLine(
    val lineNumber: Int,
    val standard: String,          // e.g. "20/80", "20/63", "20/50", "20/40", "20/32", "20/25", "20/20"
    val letters: List<Char>,       // The exact letters from eye test.png
    val category: String,          // Clinical category name for diagnosis badge
    val thresholdDescription: String
) {
    val displayLetters: String
        get() = letters.joinToString("  ")
}

enum class VisionClassification(val title: String, val badgeColorHex: Long, val description: String) {
    COMPLETELY_BLIND(
        "Completely Blind",
        0xFFEF4444,
        "Total visual impairment detected. Full audio guidance, continuous spatial sound, and haptic alerts are active."
    ),
    PARTIALLY_BLIND(
        "Partially Blind (Low Vision)",
        0xFFF59E0B,
        "Moderate-to-severe visual impairment. High-contrast typography, screen magnification, and audio prompts are active."
    ),
    MODERATE_MYOPIA(
        "Moderate Myopia",
        0xFF3B82F6,
        "Mild-to-moderate low vision. Standard assistance with high-visibility UI."
    ),
    NORMAL_VISION(
        "Normal 20/20 Vision",
        0xFF10B981,
        "Normal visual acuity. Standard assistive features enabled."
    )
}

object EyeTestData {

    /**
     * Exact rows from eye test.png:
     * Line 1 (20/80):  T Z O
     * Line 2 (20/63):  P T O C
     * Line 3 (20/50):  Z L P E D
     * Line 4 (20/40):  E T O D C F
     * Line 5 (20/32):  D P C Z L F T
     * Line 6 (20/25):  C F D T E O P L
     * Line 7 (20/20):  L D C Z O T E P
     * Line 8 (20/16):  F P C D T Z L E
     * Line 9 (20/12.5): O L E F D T Z P
     */
    val CHART_LINES = listOf(
        AcuityLine(
            lineNumber = 1,
            standard = "20/80",
            letters = listOf('T', 'Z', 'O'),
            category = "Severe Low Vision Baseline",
            thresholdDescription = "Huge baseline letters. Essential test for detecting legal and complete blindness."
        ),
        AcuityLine(
            lineNumber = 2,
            standard = "20/63",
            letters = listOf('P', 'T', 'O', 'C'),
            category = "Partially Blind",
            thresholdDescription = "Very large print. Differentiates severe low vision from partial blindness."
        ),
        AcuityLine(
            lineNumber = 3,
            standard = "20/50",
            letters = listOf('Z', 'L', 'P', 'E', 'D'),
            category = "Moderate Low Vision",
            thresholdDescription = "Large print standard. Common threshold for low-vision magnification."
        ),
        AcuityLine(
            lineNumber = 4,
            standard = "20/40",
            letters = listOf('E', 'T', 'O', 'D', 'C', 'F'),
            category = "Mild Myopia",
            thresholdDescription = "Medium print. Driving license visual standard in many jurisdictions."
        ),
        AcuityLine(
            lineNumber = 5,
            standard = "20/32",
            letters = listOf('D', 'P', 'C', 'Z', 'L', 'F', 'T'),
            category = "Near Normal",
            thresholdDescription = "Standard newsprint font size at 14 inches."
        ),
        AcuityLine(
            lineNumber = 6,
            standard = "20/25",
            letters = listOf('C', 'F', 'D', 'T', 'E', 'O', 'P', 'L'),
            category = "High Acuity",
            thresholdDescription = "Fine print acuity. Approaching standard 20/20 resolution."
        ),
        AcuityLine(
            lineNumber = 7,
            standard = "20/20",
            letters = listOf('L', 'D', 'C', 'Z', 'O', 'T', 'E', 'P'),
            category = "Normal 20/20 Vision",
            thresholdDescription = "Clinical gold standard 20/20 visual acuity."
        ),
        AcuityLine(
            lineNumber = 8,
            standard = "20/16",
            letters = listOf('F', 'P', 'C', 'D', 'T', 'Z', 'L', 'E'),
            category = "Superior Acuity",
            thresholdDescription = "Ultra fine optical resolution."
        ),
        AcuityLine(
            lineNumber = 9,
            standard = "20/12.5",
            letters = listOf('O', 'L', 'E', 'F', 'D', 'T', 'Z', 'P'),
            category = "Maximum Acuity",
            thresholdDescription = "Maximum optical visual acuity."
        )
    )

    /**
     * DYNAMIC FONT SIZING: The more the user speaks and advances,
     * the smaller the letters get, matching standard Snellen optical angles.
     */
    fun getAcuityFontSize(lineIndex: Int): TextUnit {
        return when (lineIndex) {
            0 -> 76.sp   // Step 1 (20/80): Giant baseline letters (huge print)
            1 -> 62.sp   // Step 2 (20/63): Very large print
            2 -> 48.sp   // Step 3 (20/50): Large print
            3 -> 38.sp   // Step 4 (20/40): Medium print
            4 -> 30.sp   // Step 5 (20/32): Standard print
            5 -> 24.sp   // Step 6 (20/25): Fine print
            6 -> 18.sp   // Step 7 (20/20): Precision print
            else -> 16.sp
        }
    }

    fun getAcuityScaleLabel(lineIndex: Int): String {
        return when (lineIndex) {
            0 -> "Huge Print (20/80)"
            1 -> "Very Large Print (20/63)"
            2 -> "Large Print (20/50)"
            3 -> "Medium Print (20/40)"
            4 -> "Standard Print (20/32)"
            5 -> "Fine Print (20/25)"
            6 -> "Precision 20/20"
            else -> "Acuity ${CHART_LINES.getOrNull(lineIndex)?.standard ?: "Standard"}"
        }
    }

    /**
     * Classifies vision based on the highest passed line.
     * Line 0 = 20/80 (Largest letters).
     * If user fails Line 0, they cannot see even huge letters -> Completely Blind.
     * If user passes Line 0 or 1, but fails subsequent lines -> Partially Blind.
     * If user reaches Line 3 or 4 -> Moderate Myopia.
     * If user reaches Line 5 or 6 -> Normal Vision.
     */
    fun classifyVision(passedLineIndex: Int): VisionClassification {
        return when {
            passedLineIndex < 0 -> VisionClassification.COMPLETELY_BLIND
            passedLineIndex == 0 -> VisionClassification.COMPLETELY_BLIND // Failed 20/80 baseline
            passedLineIndex in 1..2 -> VisionClassification.PARTIALLY_BLIND // Reads only 20/80 or 20/63
            passedLineIndex in 3..4 -> VisionClassification.MODERATE_MYOPIA // Reads down to 20/40 or 20/32
            else -> VisionClassification.NORMAL_VISION // Reads 20/25, 20/20 or better
        }
    }

    /**
     * Calculates an overall visual acuity score (0 to 100) combining the highest
     * passed line and current line accuracy.
     */
    fun calculateAcuityScore(passedLineIndex: Int, currentAccuracy: Int): Int {
        if (passedLineIndex < 0) {
            return (currentAccuracy * 0.15f).toInt().coerceIn(0, 15)
        }
        return when (passedLineIndex) {
            0 -> (15 + currentAccuracy * 0.2f).toInt().coerceIn(15, 35)
            1 -> (35 + currentAccuracy * 0.15f).toInt().coerceIn(35, 50)
            2 -> (50 + currentAccuracy * 0.15f).toInt().coerceIn(50, 65)
            3 -> (65 + currentAccuracy * 0.15f).toInt().coerceIn(65, 80)
            4 -> (80 + currentAccuracy * 0.1f).toInt().coerceIn(80, 90)
            else -> (90 + currentAccuracy * 0.1f).toInt().coerceIn(90, 100)
        }
    }
}
