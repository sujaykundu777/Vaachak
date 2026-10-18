package com.example.smartblindstick.eyetest

import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Test

class EyeTestUnitTest {

    @Test
    fun testChartLinesDataMatchesPrecisionVisionChart() {
        val lines = EyeTestData.CHART_LINES
        assertEquals(9, lines.size)

        // Line 1: T Z O (20/80)
        assertEquals("20/80", lines[0].standard)
        assertEquals(listOf('T', 'Z', 'O'), lines[0].letters)

        // Line 2: P T O C (20/63)
        assertEquals("20/63", lines[1].standard)
        assertEquals(listOf('P', 'T', 'O', 'C'), lines[1].letters)

        // Line 3: Z L P E D (20/50)
        assertEquals("20/50", lines[2].standard)
        assertEquals(listOf('Z', 'L', 'P', 'E', 'D'), lines[2].letters)

        // Line 4: E T O D C F (20/40)
        assertEquals("20/40", lines[3].standard)
        assertEquals(listOf('E', 'T', 'O', 'D', 'C', 'F'), lines[3].letters)

        // Line 5: D P C Z L F T (20/32)
        assertEquals("20/32", lines[4].standard)
        assertEquals(listOf('D', 'P', 'C', 'Z', 'L', 'F', 'T'), lines[4].letters)

        // Line 7: L D C Z O T E P (20/20)
        assertEquals("20/20", lines[6].standard)
        assertEquals(listOf('L', 'D', 'C', 'Z', 'O', 'T', 'E', 'P'), lines[6].letters)
    }

    @Test
    fun testDynamicFontSizeDecreasesProgressively() {
        // Font size MUST shrink as user talks and advances through the chart
        val sizeStep1 = EyeTestData.getAcuityFontSize(0).value // 20/80
        val sizeStep2 = EyeTestData.getAcuityFontSize(1).value // 20/63
        val sizeStep3 = EyeTestData.getAcuityFontSize(2).value // 20/50
        val sizeStep4 = EyeTestData.getAcuityFontSize(3).value // 20/40
        val sizeStep5 = EyeTestData.getAcuityFontSize(4).value // 20/32
        val sizeStep6 = EyeTestData.getAcuityFontSize(5).value // 20/25
        val sizeStep7 = EyeTestData.getAcuityFontSize(6).value // 20/20

        assertTrue(sizeStep1 > sizeStep2)
        assertTrue(sizeStep2 > sizeStep3)
        assertTrue(sizeStep3 > sizeStep4)
        assertTrue(sizeStep4 > sizeStep5)
        assertTrue(sizeStep5 > sizeStep6)
        assertTrue(sizeStep6 > sizeStep7)

        assertEquals(76f, sizeStep1)
        assertEquals(18f, sizeStep7)
    }

    @Test
    fun testExtractLettersPhoneticAndRaw() {
        // Space separated
        val letters1 = EyeTestVoiceEvaluator.extractLetters("z l p e b")
        assertEquals(listOf('Z', 'L', 'P', 'E', 'B'), letters1)

        // Spoken phonetic names
        val letters2 = EyeTestVoiceEvaluator.extractLetters("zee el pee ee bee")
        assertEquals(listOf('Z', 'L', 'P', 'E', 'B'), letters2)

        // Combined string
        val letters3 = EyeTestVoiceEvaluator.extractLetters("zlped")
        assertEquals(listOf('Z', 'L', 'P', 'E', 'D'), letters3)

        // Line 1: T Z O
        val letters4 = EyeTestVoiceEvaluator.extractLetters("tee zee oh")
        assertEquals(listOf('T', 'Z', 'O'), letters4)
    }

    @Test
    fun testConversationalPhrasesDoNotTurnIntoLetters() {
        // Blindness and conversational phrases must NOT be split into letters
        assertTrue(EyeTestVoiceEvaluator.extractLetters("I cannot see anything").isEmpty())
        assertTrue(EyeTestVoiceEvaluator.extractLetters("I'm blind").isEmpty())
        assertTrue(EyeTestVoiceEvaluator.extractLetters("too blurry to see").isEmpty())
        assertTrue(EyeTestVoiceEvaluator.extractLetters("next line").isEmpty())
        assertTrue(EyeTestVoiceEvaluator.extractLetters("help me with instructions").isEmpty())
    }

    @Test
    fun testEvaluationResultMatchesMockupScenario() {
        // Line 3: Z L P E D. User says "z l p e b" -> 4 of 5 (80%), 1 Misread
        val targetLetters = listOf('Z', 'L', 'P', 'E', 'D')
        val result = EyeTestVoiceEvaluator.evaluate(targetLetters, "z l p e b")

        assertEquals(4, result.correctCount)
        assertEquals(5, result.totalCount)
        assertEquals(80, result.accuracyPercent)
        assertEquals(1, result.misreadCount)
        assertEquals("Z • L • P • E • B", result.heardFormatted)
        assertTrue(result.isPassThreshold)

        // Check individual letter evaluations
        assertTrue(result.letterEvaluations[0].isCorrect) // Z == Z
        assertTrue(result.letterEvaluations[1].isCorrect) // L == L
        assertTrue(result.letterEvaluations[2].isCorrect) // P == P
        assertTrue(result.letterEvaluations[3].isCorrect) // E == E
        assertFalse(result.letterEvaluations[4].isCorrect) // D != B
        assertEquals('B', result.letterEvaluations[4].spokenLetter)
    }

    @Test
    fun testCommandKeywords() {
        assertTrue(EyeTestVoiceEvaluator.isNextCommand("next line please"))
        assertTrue(EyeTestVoiceEvaluator.isNextCommand("proceed"))
        assertTrue(EyeTestVoiceEvaluator.isNextCommand("continue"))

        assertTrue(EyeTestVoiceEvaluator.isBlindOrCannotSee("I can't see the letters"))
        assertTrue(EyeTestVoiceEvaluator.isBlindOrCannotSee("I am blind"))
        assertTrue(EyeTestVoiceEvaluator.isBlindOrCannotSee("cannot read anything, pitch black"))

        assertTrue(EyeTestVoiceEvaluator.isInstructionsCommand("what are the instructions"))
    }

    @Test
    fun testVisionClassification() {
        // Unable to pass line 1 (20/80) -> Completely Blind
        assertEquals(VisionClassification.COMPLETELY_BLIND, EyeTestData.classifyVision(-1))
        assertEquals(VisionClassification.COMPLETELY_BLIND, EyeTestData.classifyVision(0))

        // Can pass 20/63 or 20/50 but not smaller -> Partially Blind
        assertEquals(VisionClassification.PARTIALLY_BLIND, EyeTestData.classifyVision(1))
        assertEquals(VisionClassification.PARTIALLY_BLIND, EyeTestData.classifyVision(2))

        // Can pass 20/40 or 20/32 -> Moderate Myopia
        assertEquals(VisionClassification.MODERATE_MYOPIA, EyeTestData.classifyVision(3))
        assertEquals(VisionClassification.MODERATE_MYOPIA, EyeTestData.classifyVision(4))

        // Normal 20/20 vision
        assertEquals(VisionClassification.NORMAL_VISION, EyeTestData.classifyVision(6))
    }

    @Test
    fun testAcuityScoreCalculation() {
        // Failed on baseline
        assertEquals(0, EyeTestData.calculateAcuityScore(-1, 0))
        assertTrue(EyeTestData.calculateAcuityScore(-1, 50) <= 15)

        // Passed line 1 (20/80)
        val scoreLine1 = EyeTestData.calculateAcuityScore(0, 100)
        assertEquals(35, scoreLine1)

        // Passed up to line 6 (20/20)
        val scoreLine6 = EyeTestData.calculateAcuityScore(6, 100)
        assertEquals(100, scoreLine6)
    }
}
