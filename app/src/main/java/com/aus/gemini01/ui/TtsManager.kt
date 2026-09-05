package com.aus.gemini01.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

class TtsManager(
    context: Context,
    private val onPlaybackFinished: () -> Unit = {}
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (utteranceId?.endsWith("_last") == true || utteranceId == "news_reader_single") {
                        mainHandler.post { onPlaybackFinished() }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { onPlaybackFinished() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post { onPlaybackFinished() }
                }
            })
        }
    }

    fun speak(text: String, languageName: String) {
        if (!isInitialized || text.isBlank()) return

        val locale = getLocaleForLanguage(languageName)
        tts?.language = locale

        val spoken = stripMarkdownForSpeech(text)
        if (spoken.isBlank()) return

        val maxLen = 3500
        if (spoken.length <= maxLen) {
            tts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "news_reader_single")
        } else {
            val chunks = chunkText(spoken, maxLen)
            chunks.forEachIndexed { index, chunk ->
                val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val utteranceId = if (index == chunks.lastIndex) "news_reader_chunk_${index}_last" else "news_reader_chunk_$index"
                tts?.speak(chunk, mode, null, utteranceId)
            }
        }
    }

    private fun chunkText(text: String, maxChunkSize: Int): List<String> {
        val paragraphs = text.split("\n\n")
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        fun flush() {
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
        }

        fun appendHardSplit(piece: String) {
            var remaining = piece
            while (remaining.length > maxChunkSize) {
                flush()
                chunks.add(remaining.substring(0, maxChunkSize))
                remaining = remaining.substring(maxChunkSize)
            }
            if (remaining.isNotEmpty()) {
                if (currentChunk.isNotEmpty()) currentChunk.append(' ')
                currentChunk.append(remaining)
            }
        }

        for (p in paragraphs) {
            if (currentChunk.length + p.length + 2 > maxChunkSize) {
                flush()
                if (p.length > maxChunkSize) {
                    val sentences = p.split(". ")
                    for (s in sentences) {
                        if (currentChunk.length + s.length + 2 > maxChunkSize) {
                            flush()
                            if (s.length > maxChunkSize) {
                                appendHardSplit(s.trim())
                            } else {
                                currentChunk.append(s)
                            }
                        } else {
                            if (currentChunk.isNotEmpty()) currentChunk.append(". ")
                            currentChunk.append(s)
                        }
                    }
                } else {
                    currentChunk.append(p).append("\n\n")
                }
            } else {
                currentChunk.append(p).append("\n\n")
            }
        }
        if (currentChunk.isNotBlank()) {
            chunks.add(currentChunk.toString().trim())
        }
        return if (chunks.isEmpty()) listOf(text) else chunks
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun getLocaleForLanguage(languageName: String): Locale {
        return when (languageName.lowercase()) {
            "spanish" -> Locale.forLanguageTag("es-ES")
            "french" -> Locale.forLanguageTag("fr-FR")
            "german" -> Locale.forLanguageTag("de-DE")
            "chinese" -> Locale.forLanguageTag("zh-CN")
            "arabic" -> Locale.forLanguageTag("ar")
            "portuguese" -> Locale.forLanguageTag("pt-PT")
            else -> Locale.US
        }
    }
}
