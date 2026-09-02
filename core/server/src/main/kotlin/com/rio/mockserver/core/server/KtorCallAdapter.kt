package com.rio.mockserver.core.server

import com.rio.mockserver.core.common.config.AppConstants
import com.rio.mockserver.core.domain.model.MockRequest
import com.rio.mockserver.core.domain.model.PathNormaliser
import com.rio.mockserver.core.domain.util.TimeProvider
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates a Ktor call into the domain's [MockRequest] (SPEC §2.1).
 *
 * The one place Ktor's request types are read. Everything downstream operates on plain data,
 * which is what lets the whole matching pipeline be tested without a socket.
 *
 * Reading the body is this adapter's most consequential decision, and it makes three (SPEC §5.4):
 * a body over the ceiling is not read at all, a binary body is measured but not stored as text,
 * and a compressed body is inflated before it is offered for matching.
 */
@Singleton
class KtorCallAdapter @Inject constructor(
    private val timeProvider: TimeProvider,
) {

    /** Builds a [MockRequest] from [call], reading at most [maxBodyBytes] of body. */
    suspend fun toMockRequest(call: ApplicationCall, maxBodyBytes: Long): MockRequest {
        val contentType = call.request.contentType()
        val declaredLength = call.request.headers[CONTENT_LENGTH]?.toLongOrNull()

        val body = readBody(call, contentType, declaredLength, maxBodyBytes)

        return MockRequest(
            method = call.request.httpMethod.value.uppercase(),
            // Normalised here so that every consumer — matcher, log, placeholder — sees the same
            // string, and a rule written against `/users` cannot fail for `/users/`.
            path = PathNormaliser.normalise(call.request.uri),
            query = call.request.queryParameters.entries().associate { it.key to it.value },
            headers = call.request.headers.entries().associate { it.key to it.value },
            body = body.text,
            contentType = contentType.takeIf { it != ContentType.Any }?.toString(),
            bodyByteCount = body.byteCount,
            bodyState = body.state,
            clientIp = call.request.local.remoteAddress,
            receivedAt = timeProvider.nowMillis(),
        )
    }

    private data class BodyRead(
        val text: String?,
        val byteCount: Int,
        val state: MockRequest.BodyState,
    )

    private suspend fun readBody(
        call: ApplicationCall,
        contentType: ContentType,
        declaredLength: Long?,
        maxBodyBytes: Long,
    ): BodyRead {
        // Refuse on the declared length before reading a byte. Reading 200 MB to discover it is
        // too big is the failure this ceiling exists to prevent.
        if (declaredLength != null && declaredLength > maxBodyBytes) {
            return BodyRead(null, declaredLength.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), MockRequest.BodyState.OVERSIZED)
        }
        if (declaredLength == 0L) return BodyRead(null, 0, MockRequest.BodyState.ABSENT)

        val raw = call.receiveChannel().readUpTo(maxBodyBytes)
            ?: return BodyRead(null, maxBodyBytes.toInt(), MockRequest.BodyState.OVERSIZED)

        if (raw.isEmpty()) return BodyRead(null, 0, MockRequest.BodyState.ABSENT)

        val decoded = decode(raw, call.request.headers[CONTENT_ENCODING])

        // Binary bodies are measured, never stored as text: the log shows `<binary, N bytes>` and
        // every body matcher fails, which is what SPEC §5.4 asks for.
        if (contentType.isBinary()) {
            return BodyRead(null, decoded.size, MockRequest.BodyState.BINARY)
        }

        return BodyRead(String(decoded, Charsets.UTF_8), decoded.size, MockRequest.BodyState.PRESENT)
    }

    /**
     * Reads up to [limit] bytes, returning `null` when the channel has more than that.
     *
     * One byte past the limit is enough to know: it is read and discarded rather than the whole
     * remainder being drained to measure it.
     */
    private suspend fun ByteReadChannel.readUpTo(limit: Long): ByteArray? {
        val out = ByteArrayOutputStream(DEFAULT_BUFFER_SIZE)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L

        while (true) {
            val read = readAvailable(buffer, 0, buffer.size)
            if (read <= 0) break
            total += read
            if (total > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * Inflates a `gzip` or `deflate` body so matching sees what the client meant to send.
     *
     * A body that claims an encoding it does not have is returned as-is rather than failing the
     * request: the mock server's job is to answer, and a malformed body still deserves to be
     * matched by the path-only routes.
     */
    private fun decode(raw: ByteArray, encoding: String?): ByteArray = when {
        encoding.equals(GZIP, ignoreCase = true) -> runCatching {
            GZIPInputStream(raw.inputStream()).use { it.readBytes() }
        }.getOrDefault(raw)

        encoding.equals(DEFLATE, ignoreCase = true) -> runCatching {
            InflaterInputStream(raw.inputStream()).use { it.readBytes() }
        }.getOrDefault(raw)

        else -> raw
    }

    private fun ContentType.isBinary(): Boolean {
        val value = "$contentType/$contentSubtype"
        return AppConstants.Request.BINARY_CONTENT_TYPE_PREFIXES.any { prefix ->
            value.startsWith(prefix.removeSuffix("/"), ignoreCase = true)
        }
    }

    private companion object {
        const val CONTENT_LENGTH = "Content-Length"
        const val CONTENT_ENCODING = "Content-Encoding"
        const val GZIP = "gzip"
        const val DEFLATE = "deflate"
        const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
