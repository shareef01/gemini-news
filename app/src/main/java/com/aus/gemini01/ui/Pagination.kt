package com.aus.gemini01.ui

/**
 * Page number to request next. Callers must only commit this as [currentPage]
 * after a successful append — otherwise a failed load skips a NewsAPI page.
 */
internal fun nextPageToLoad(currentPage: Int): Int = currentPage + 1
