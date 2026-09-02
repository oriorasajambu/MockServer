package com.minion.mockserver.core.data.util

import com.minion.mockserver.core.common.dispatcher.IoDispatcher
import com.minion.mockserver.core.common.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires one request at the app's own server over loopback.
 *
 * This is what "Test this route" does (SPEC §10.2). It goes through the real socket rather than
 * calling the pipeline directly, because the point of the button is to prove the rule works end
 * to end — including the adapter, the writer and the traffic log, which are exactly the parts a
 * unit test cannot see.
 *
 * `HttpURLConnection` rather than a client library: this issues one request, on a button press,
 * to `127.0.0.1`. A Ktor or OkHttp client would be a dependency and a connection pool for that.
 */
@Singleton
class LoopbackRequester @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Sends [method] to `http://127.0.0.1:[port][path]` and discards the response.
     *
     * The response is discarded because the caller reads the *traffic entry* the request produced,
     * which carries far more than the response does — what matched, what score, what chaos did.
     *
     * Returns `true` if the request reached the server at all. A refused connection is a real
     * answer ("the server is not running"), not an error worth propagating.
     */
    suspend fun send(
        port: Int,
        path: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): Boolean = withContext(ioDispatcher) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URI(SCHEME, null, HOST, port, path, null, null).toURL()
                .openConnection() as HttpURLConnection).apply {
                requestMethod = method.uppercase()
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            // Reading the status is what actually completes the exchange; without it the request
            // may never be flushed.
            connection.responseCode
            true
        } catch (e: Exception) {
            Logger.w(TAG, "loopback request failed: ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TAG = "LoopbackRequester"
        const val SCHEME = "http"

        /** The literal, not `localhost`: no DNS lookup, and no chance of an IPv6 detour. */
        const val HOST = "127.0.0.1"
        const val TIMEOUT_MILLIS = 5_000
    }
}
