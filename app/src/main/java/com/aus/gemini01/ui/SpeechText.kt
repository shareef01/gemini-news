package com.aus.gemini01.ui

/**
 * Strips common Markdown markers so TTS does not speak `#`, `**`, or list bullets.
 * Kept pure for unit testing — not a full Markdown renderer.
 */
internal fun stripMarkdownForSpeech(text: String): String {
    return text
        .lineSequence()
        .map { line ->
            line
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^\\s*[-*•]\\s+"), "")
                .replace(Regex("^\\s*\\d+[.)]\\s+"), "")
                .replace(Regex("^>\\s*"), "")
                .replace("**", "")
                .replace("__", "")
                .replace(Regex("`+"), "")
                .trimEnd()
        }
        .joinToString("\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
