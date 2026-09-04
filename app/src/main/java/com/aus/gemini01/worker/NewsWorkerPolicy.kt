package com.aus.gemini01.worker

import com.aus.gemini01.data.MissingNewsApiKeyException
import retrofit2.HttpException

/** Persist last-notified URL only when the OS actually accepted the notification. */
internal fun shouldPersistNotifiedUrl(notificationShown: Boolean): Boolean = notificationShown

/**
 * Permanent NewsWorker failures must not [androidx.work.ListenableWorker.Result.retry]
 * forever (battery / backoff spam). Transient network/5xx may retry.
 */
internal fun isPermanentNewsWorkerFailure(error: Throwable): Boolean = when (error) {
    is MissingNewsApiKeyException -> true
    is HttpException -> error.code() in 400..499
    else -> false
}
