package com.aus.gemini01.ui

import com.aus.gemini01.data.Article
import com.aus.gemini01.data.ai.AiError
import com.aus.gemini01.data.ai.AiFeature
import com.aus.gemini01.data.ai.AiRepository
import com.aus.gemini01.data.ai.AiRequestException
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

class ChatManager(
    private val aiRepository: AiRepository,
    private val generativeModel: GenerativeModel,
    private val scope: CoroutineScope,
    private val errorEvents: MutableSharedFlow<String>
) {
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatting = MutableStateFlow(false)
    val isChatting: StateFlow<Boolean> = _isChatting.asStateFlow()

    private var chatJob: Job? = null
    private var chatGeneration = 0

    fun sendChatMessage(
        userMessage: String,
        currentArticles: List<Article>,
        preferredLanguage: String
    ) {
        if (userMessage.isBlank()) return

        val trimmedUserMessage = userMessage.trim()
        val currentList = _chatMessages.value.toMutableList()
        currentList.add(ChatMessage(trimmedUserMessage, isUser = true))
        _chatMessages.value = currentList
        _isChatting.value = true

        val currentTurn = ++chatGeneration
        chatJob?.cancel()
        chatJob = scope.launch(Dispatchers.IO) {
            try {
                val headlines = currentArticles.take(15).joinToString("\n") { article ->
                    val desc = article.description?.takeIf { it.isNotBlank() }
                    if (desc != null) {
                        "- ${sanitizeForPrompt(article.title)} (${sanitizeForPrompt(article.source.name)}): ${sanitizeForPrompt(desc)}"
                    } else {
                        "- ${sanitizeForPrompt(article.title)} (${sanitizeForPrompt(article.source.name)})"
                    }
                }
                val prompt = if (currentList.size <= 1) {
                    """
                        $PROMPT_INJECTION_GUARD

                        You are a news expert assistant.
                        Here are the currently available top headlines for context:

                        [[DATA]]
                        $headlines
                        [[/DATA]]

                        Answer the user's question clearly and concisely in $preferredLanguage.
                        If the question relates to the headlines above, use them as your primary source.
                        If it's about general knowledge or events not in the headlines, you may answer
                        using your general knowledge, but mention that it's based on general knowledge,
                        not today's loaded feed.

                        User: ${sanitizeForPrompt(trimmedUserMessage)}
                    """.trimIndent()
                } else {
                    val history = currentList.dropLast(1).takeLast(6).joinToString("\n") {
                        val role = if (it.isUser) "User" else "Assistant"
                        "$role: ${sanitizeForPrompt(it.text)}"
                    }
                    """
                        $PROMPT_INJECTION_GUARD

                        You are a news expert assistant continuing a conversation.
                        Respond in $preferredLanguage. Keep answers concise, factual, and helpful.
                        If the question relates to the loaded headlines below, ground your answer in them;
                        if it goes beyond the feed, note when you are drawing on general knowledge.

                        Loaded feed headlines:
                        [[DATA]]
                        $headlines
                        [[/DATA]]

                        Conversation history:
                        [[DATA]]
                        $history
                        [[/DATA]]

                        User: ${sanitizeForPrompt(trimmedUserMessage)}
                    """.trimIndent()
                }

                val reply = aiRepository.recordUncachedRequest(AiFeature.CHAT) {
                    withTimeout(60_000) { generativeModel.generateContent(prompt) }.text
                        ?: throw IllegalStateException("Empty chat response")
                }

                if (currentTurn == chatGeneration) {
                    val updated = _chatMessages.value.toMutableList()
                    updated.add(ChatMessage(reply, isUser = false))
                    _chatMessages.value = updated
                }
            } catch (e: TimeoutCancellationException) {
                if (currentTurn == chatGeneration) {
                    val updated = _chatMessages.value.toMutableList()
                    updated.add(
                        ChatMessage(
                            "The response took too long. Please try asking again.",
                            isUser = false
                        )
                    )
                    _chatMessages.value = updated
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiRequestException) {
                if (currentTurn == chatGeneration) {
                    val message = when (e.error) {
                        is AiError.QuotaExceeded ->
                            "Gemini's usage limit has been reached. It will reset later — please try again then."
                        is AiError.Network ->
                            "Couldn't reach Gemini. Check your connection and try again."
                        is AiError.Timeout ->
                            "The request timed out. Please try asking again."
                        is AiError.Unknown ->
                            "I ran into an issue answering that. Please try again."
                    }
                    val updated = _chatMessages.value.toMutableList()
                    updated.add(ChatMessage(message, isUser = false))
                    _chatMessages.value = updated
                }
            } catch (e: Exception) {
                if (currentTurn == chatGeneration) {
                    val updated = _chatMessages.value.toMutableList()
                    updated.add(
                        ChatMessage(
                            "I ran into an issue answering that. Please try again.",
                            isUser = false
                        )
                    )
                    _chatMessages.value = updated
                }
            } finally {
                if (currentTurn == chatGeneration) {
                    _isChatting.value = false
                }
            }
        }
    }

    fun clearChat() {
        chatJob?.cancel()
        chatGeneration++
        _chatMessages.value = emptyList()
        _isChatting.value = false
    }
}
