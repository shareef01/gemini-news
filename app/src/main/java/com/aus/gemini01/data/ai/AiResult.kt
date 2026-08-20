package com.aus.gemini01.data.ai

/**
 * What an AI surface (dialog, reader pane) renders. Failures carry the
 * classified error so the UI can explain what happened and what still works.
 */
sealed interface AiResult {
    data class Success(val text: String) : AiResult
    data class Failure(val error: AiError) : AiResult
}
