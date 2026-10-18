package com.example.smartblindstick.eyetest

import java.util.Locale

data class LetterEvaluation(
    val targetLetter: Char,
    val spokenLetter: Char?,
    val isCorrect: Boolean
)

data class EvaluationResult(
    val letterEvaluations: List<LetterEvaluation>,
    val heardFormatted: String,
    val accuracyPercent: Int,
    val correctCount: Int,
    val totalCount: Int,
    val misreadCount: Int,
    val isPassThreshold: Boolean, // >= 65%
    val rawSpokenText: String
)

object EyeTestVoiceEvaluator {

    /**
     * Comprehensive dictionary mapping phonetic spellings, homophones,
     * and military NATO phonetics to uppercase English letters.
     */
    val PHONETIC_MAP = mapOf(
        // Z
        "zee" to 'Z', "zed" to 'Z', "zulu" to 'Z', "zeta" to 'Z', "z" to 'Z', "set" to 'Z',
        // L
        "el" to 'L', "ell" to 'L', "lima" to 'L', "al" to 'L', "l" to 'L',
        // P
        "pee" to 'P', "pea" to 'P', "papa" to 'P', "pi" to 'P', "p" to 'P',
        // E
        "ee" to 'E', "echo" to 'E', "e" to 'E',
        // D
        "dee" to 'D', "delta" to 'D', "the" to 'D', "d" to 'D',
        // T
        "tee" to 'T', "tea" to 'T', "tango" to 'T', "ti" to 'T', "t" to 'T',
        // O
        "oh" to 'O', "oscar" to 'O', "zero" to 'O', "0" to 'O', "o" to 'O',
        // C
        "see" to 'C', "sea" to 'C', "charlie" to 'C', "si" to 'C', "c" to 'C',
        // F
        "eff" to 'F', "foxtrot" to 'F', "if" to 'F', "f" to 'F',
        // B
        "bee" to 'B', "bravo" to 'B', "be" to 'B', "b" to 'B',
        // A
        "ay" to 'A', "alpha" to 'A', "hey" to 'A', "a" to 'A',
        // K
        "kay" to 'K', "kilo" to 'K', "k" to 'K',
        // M
        "em" to 'M', "mike" to 'M', "m" to 'M',
        // N
        "en" to 'N', "november" to 'N', "an" to 'N', "n" to 'N',
        // Q
        "cue" to 'Q', "quebec" to 'Q', "queue" to 'Q', "q" to 'Q',
        // R
        "are" to 'R', "romeo" to 'R', "our" to 'R', "r" to 'R',
        // S
        "ess" to 'S', "es" to 'S', "sierra" to 'S', "s" to 'S',
        // U
        "you" to 'U', "uniform" to 'U', "u" to 'U',
        // V
        "vee" to 'V', "victor" to 'V', "we" to 'V', "v" to 'V',
        // W
        "double u" to 'W', "whiskey" to 'W', "w" to 'W',
        // X
        "ex" to 'X', "x-ray" to 'X', "x" to 'X',
        // Y
        "why" to 'Y', "yankee" to 'Y', "y" to 'Y',
        // G
        "gee" to 'G', "golf" to 'G', "g" to 'G',
        // H
        "aitch" to 'H', "hotel" to 'H', "h" to 'H',
        // J
        "jay" to 'J', "juliet" to 'J', "j" to 'J',
        // I
        "eye" to 'I', "india" to 'I', "i" to 'I', "1" to 'I'
    )

    /**
     * Common conversational words that should NEVER be chopped into letters.
     */
    private val CONVERSATIONAL_WORDS = setOf(
        "i", "cannot", "cant", "can't", "see", "anything", "nothing", "blind", "blurry",
        "shape", "shapes", "dark", "pitch", "black", "reading", "letters", "the", "looks",
        "like", "too", "small", "hard", "help", "next", "skip", "repeat", "what", "is",
        "it", "dont", "know", "blank", "hello", "hi", "test", "voice", "some", "more",
        "much", "talk", "said", "read", "good", "fine", "size", "screen", "phone", "eyes",
        "eye", "words", "word", "letter", "line", "unable", "invisible", "visible"
    )

    fun isNextCommand(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        return lower.contains("next") || lower.contains("proceed") || lower.contains("continue") ||
                lower.contains("forward") || lower.contains("advance") || lower.contains("skip")
    }

    fun isBlindOrCannotSee(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        return lower.contains("can't see") || lower.contains("cannot see") ||
                lower.contains("cant see") || lower.contains("unable to see") ||
                lower.contains("i am blind") || lower.contains("i'm blind") ||
                lower.contains("completely blind") || lower.contains("partially blind") ||
                lower.contains("nothing") || lower.contains("can't read") || lower.contains("cannot read") ||
                lower.contains("cant read") || lower.contains("blank") || lower.contains("pitch black") ||
                lower.contains("too blurry") || lower.contains("blurry") || lower.contains("dark") ||
                lower.contains("too small") || lower.contains("no idea") || lower.contains("not visible") ||
                lower.contains("invisible") || lower.contains("zero vision") || lower.contains("can not see")
    }

    fun isInstructionsCommand(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        return lower.contains("instruction") || lower.contains("help") || lower.contains("repeat") ||
                lower.contains("how to") || lower.contains("what to do") || lower.contains("say again")
    }

    /**
     * Extracts intended uppercase letters from user speech.
     * Prevents conversational phrases from being chopped into arbitrary letters.
     */
    fun extractLetters(spokenText: String): List<Char> {
        if (isBlindOrCannotSee(spokenText) || isNextCommand(spokenText) || isInstructionsCommand(spokenText)) {
            return emptyList()
        }

        var cleaned = spokenText.lowercase(Locale.ROOT)
            .replace("-", " ")
            .replace(".", " ")
            .replace(",", " ")
            .replace(";", " ")
            .replace(":", " ")
            .replace("_", " ")

        // Remove common speech preamble phrases
        val prefixes = listOf(
            "i think it is", "i think it's", "the letters are", "letters are",
            "i see the letters", "i see", "it looks like", "i can see", "i read", "letters"
        )
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.removePrefix(prefix).trim()
            }
        }

        val tokens = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val result = mutableListOf<Char>()

        for (token in tokens) {
            val mapped = PHONETIC_MAP[token]
            if (mapped != null) {
                result.add(mapped)
            } else if (token.length == 1 && token[0].isLetter()) {
                result.add(token[0].uppercaseChar())
            } else if (!CONVERSATIONAL_WORDS.contains(token)) {
                // If the user spoke a fused letter acronym like "tzo" or "def" or "ptoc"
                val allChars = token.filter { it.isLetter() }
                if (allChars.isNotEmpty() && allChars.length <= 8) {
                    for (c in allChars) {
                        result.add(c.uppercaseChar())
                    }
                }
            }
        }
        return result
    }

    /**
     * Evaluates extracted spoken letters against target line letters.
     */
    fun evaluate(targetLetters: List<Char>, rawSpoken: String): EvaluationResult {
        val spoken = extractLetters(rawSpoken)

        val evaluations = mutableListOf<LetterEvaluation>()
        var correctCount = 0

        for (i in targetLetters.indices) {
            val target = targetLetters[i]
            val heard = spoken.getOrNull(i)
            val isMatch = heard != null && heard.equals(target, ignoreCase = true)
            if (isMatch) correctCount++
            evaluations.add(
                LetterEvaluation(
                    targetLetter = target,
                    spokenLetter = heard,
                    isCorrect = isMatch
                )
            )
        }

        val total = targetLetters.size
        val accuracyPercent = if (total > 0) ((correctCount.toFloat() / total) * 100).toInt() else 0
        val misreads = total - correctCount

        // Clean formatting: if letters were extracted, show formatted letters.
        // If conversational words were spoken (e.g. "I can't see"), show the actual spoken text!
        val heardFormatted = if (spoken.isNotEmpty()) {
            spoken.take(total).map { it.uppercaseChar() }.joinToString(" • ")
        } else {
            rawSpoken.trim().ifEmpty { "Listening..." }
        }

        val isPass = accuracyPercent >= 65 // Clinical threshold: 65% or better to advance

        return EvaluationResult(
            letterEvaluations = evaluations,
            heardFormatted = heardFormatted,
            accuracyPercent = accuracyPercent,
            correctCount = correctCount,
            totalCount = total,
            misreadCount = misreads,
            isPassThreshold = isPass,
            rawSpokenText = rawSpoken
        )
    }
}
