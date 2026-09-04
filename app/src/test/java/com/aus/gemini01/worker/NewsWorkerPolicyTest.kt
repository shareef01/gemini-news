package com.aus.gemini01.worker

import com.aus.gemini01.data.MissingNewsApiKeyException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class NewsWorkerPolicyTest {

    @Test
    fun `only persist notified url when notification was shown`() {
        assertTrue(shouldPersistNotifiedUrl(true))
        assertFalse(shouldPersistNotifiedUrl(false))
    }

    @Test
    fun `missing api key is permanent failure`() {
        assertTrue(isPermanentNewsWorkerFailure(MissingNewsApiKeyException()))
    }

    @Test
    fun `http_4xx_is_permanent_failure`() {
        val error = HttpException(Response.error<Unit>(401, "".toResponseBody(null)))
        assertTrue(isPermanentNewsWorkerFailure(error))
    }

    @Test
    fun `http_429_is_permanent_failure`() {
        val error = HttpException(Response.error<Unit>(429, "".toResponseBody(null)))
        assertTrue(isPermanentNewsWorkerFailure(error))
    }

    @Test
    fun `http_5xx_may_retry`() {
        val error = HttpException(Response.error<Unit>(503, "".toResponseBody(null)))
        assertFalse(isPermanentNewsWorkerFailure(error))
    }

    @Test
    fun `generic_io_may_retry`() {
        assertFalse(isPermanentNewsWorkerFailure(java.io.IOException("offline")))
    }
}
