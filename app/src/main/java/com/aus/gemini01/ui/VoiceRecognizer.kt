package com.aus.gemini01.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onStateChange: (Boolean) -> Unit,
    private val onError: (Int) -> Unit = {}
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceRecognizer)
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
        onStateChange(true)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        onStateChange(false)
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            onResult(matches[0])
        }
        onStateChange(false)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        onStateChange(false)
    }
    override fun onError(error: Int) {
        onStateChange(false)
        // Surface recognizer failures (no match, network, busy, etc.) so they
        // reach the user via a snackbar instead of dying silently.
        onError(error)
    }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
