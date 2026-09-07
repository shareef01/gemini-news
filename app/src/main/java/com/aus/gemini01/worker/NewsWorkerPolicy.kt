package com.aus.gemini01.worker

import com.aus.gemini01.data.MissingNewsApiKeyException
import com.aus.gemini01.data.NewsProviderException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/** Persist last-notified URL only when the OS actually accepted the notification. */
internal fun shouldPersistNotifiedUrl(notificationShown: Boolean): Boolean = notificationShown

/**
 * Permanent NewsWorker failures must not [androidx.work.ListenableWorker.Result.retry]
 * forever (battery / backoff spam). Transient network/5xx may retry.
 * Cancellation must never be classified here — callers rethrow it.
 */
internal fun isPermanentNewsWorkerFailure(error: Throwable): Boolean = when (error) {
    is CancellationException -> false
    is MissingNewsApiKeyException -> true
    is NewsProviderException -> true
    is HttpException -> error.code() in 400..499
    else -> false
}

/**
 * Maps a caught throwable into a WorkManager [androidx.work.ListenableWorker.Result]
 * after CancellationException has already been rethrown. Pure seam for unit tests.
 */
internal fun newsWorkerResultForFailure(
    error: Throwable,
    runAttemptCount: Int,
    maxAttemptsBeforeFailure: Int = 3
): androidx.work.ListenableWorker.Result {
    require(error !is CancellationException) {
        "CancellationException must be rethrown, not classified"
    }
    return if (isPermanentNewsWorkerFailure(error) || runAttemptCount > maxAttemptsBeforeFailure) {
        androidx.work.ListenableWorker.Result.failure()
    } else {
        androidx.work.ListenableWorker.Result.retry()
    }
}
