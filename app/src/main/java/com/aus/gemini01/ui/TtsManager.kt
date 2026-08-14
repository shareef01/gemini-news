package com.aus.gemini01.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
        }
    }

    fun speak(text: String, languageName: String) {
        if (!isInitialized) return
        
        val locale = getLocaleForLanguage(languageName)
        tts?.language = locale
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "news_reader")
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
            "spanish" -> Locale("es", "ES")
            "french" -> Locale.FRENCH
            "german" -> Locale.GERMAN
            "chinese" -> Locale.CHINESE
            "arabic" -> Locale("ar")
            "portuguese" -> Locale("pt", "PT")
            else -> Locale.US
        }
    }
}
