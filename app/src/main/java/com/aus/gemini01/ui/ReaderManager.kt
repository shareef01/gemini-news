package com.aus.gemini01.ui

import com.aus.gemini01.data.Article
import com.aus.gemini01.data.ai.AiCacheKeys
import com.aus.gemini01.data.ai.AiError
import com.aus.gemini01.data.ai.AiFeature
import com.aus.gemini01.data.ai.AiRepository
import com.aus.gemini01.data.ai.AiRequestException
import com.aus.gemini01.data.ai.AiResult
import com.google.firebase.ai.GenerativeModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ReaderManager(
    private val aiRepository: AiRepository,
    private val generativeModel: GenerativeModel,
    private val ttsManager: TtsManager,
    private val scope: CoroutineScope,
    private val errorEvents: MutableSharedFlow<String>? = null
) {
    private val _readerViewContent = MutableStateFlow<AiResult?>(null)
    val readerViewContent: StateFlow<AiResult?> = _readerViewContent.asStateFlow()

    private val _isGeneratingReaderView = MutableStateFlow(false)
    val isGeneratingReaderView: StateFlow<Boolean> = _isGeneratingReaderView.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var readerJob: Job? = null
    private var currentReaderUrl: String? = null
    private var readerGeneration = 0L

    init {
        // Wire playback finish callback
        // ttsManager's playback finished lambda will notify _isSpeaking
    }

    fun onSpeakingStateChanged(speaking: Boolean) {
        _isSpeaking.value = speaking
    }

    fun fetchReaderView(article: Article, preferredLanguage: String) {
        stopReadAloud()
        val generation = ++readerGeneration
        _isGeneratingReaderView.value = true
        _readerViewContent.value = null

        readerJob?.cancel()
        currentReaderUrl = article.url
        readerJob = scope.launch(Dispatchers.IO) {
            try {
                val prompt = """
                    $PROMPT_INJECTION_GUARD

                    Transform this news article into a clean, distraction-free Reading Mode version.
                    
                    Instructions:
                    - Remove all ads, navigation menus, and technical clutter.
                    - Keep the original meaning and core story intact.
                    - Format the output clearly using Markdown:
                        - Use # for the main title.
                        - Use ## for sections if applicable.
                        - Use bullet points for lists.
                        - Ensure paragraphs are well-separated.
                    - Provide the content in $preferredLanguage.
                    - Do not invent facts that are absent from the source data.
                    - You are reformatting the provided feed fields only; you cannot access paywalled publisher HTML.

                    [[DATA]]
                    Title: ${sanitizeForPrompt(article.title)}
                    Description: ${sanitizeForPrompt(article.description)}
                    Source: ${sanitizeForPrompt(article.source.name)}
                    Content: ${sanitizeForPrompt(article.content)}
                    [[/DATA]]
                """.trimIndent()

                val text = aiRepository.cachedOrFetch(
                    cacheKey = AiCacheKeys.reader(
                        article.url,
                        preferredLanguage,
                        AiCacheKeys.articleEvidenceFingerprint(
                            article.title,
                            article.description,
                            article.content,
                            article.source.name
                        )
                    ),
                    feature = AiFeature.READER,
                    articleUrl = article.url
                ) {
                    withTimeout(90_000) { generativeModel.generateContent(prompt) }.text
                        ?: throw IllegalStateException("Empty reader response")
                }

                if (generation == readerGeneration && currentReaderUrl == article.url) {
                    _readerViewContent.value = AiResult.Success(text)
                }
            } catch (e: TimeoutCancellationException) {
                if (generation == readerGeneration) {
                    _readerViewContent.value = AiResult.Failure(AiError.Timeout)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiRequestException) {
                if (generation == readerGeneration) {
                    _readerViewContent.value = AiResult.Failure(e.error)
                }
            } catch (e: Exception) {
                if (generation == readerGeneration) {
                    _readerViewContent.value = AiResult.Failure(AiError.Unknown(e.message))
                }
            } finally {
                if (generation == readerGeneration) {
                    _isGeneratingReaderView.value = false
                }
            }
        }
    }

    fun clearReaderView() {
        readerJob?.cancel()
        currentReaderUrl = null
        _readerViewContent.value = null
        _isGeneratingReaderView.value = false
        stopReadAloud()
    }

    fun toggleReadAloud(text: String, languageName: String = "English") {
        if (_isSpeaking.value) {
            stopReadAloud()
        } else {
            val started = ttsManager.speak(text, languageName)
            _isSpeaking.value = started
            if (!started) {
                scope.launch {
                    errorEvents?.emit("Read aloud isn't available right now.")
                }
            }
        }
    }

    fun stopReadAloud() {
        _isSpeaking.value = false
        ttsManager.stop()
    }

    fun cancelActiveJob() {
        readerGeneration++
        readerJob?.cancel()
        currentReaderUrl = null
        _isGeneratingReaderView.value = false
    }
}
