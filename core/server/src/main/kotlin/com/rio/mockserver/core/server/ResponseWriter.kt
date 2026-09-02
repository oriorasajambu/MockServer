package com.rio.mockserver.core.server

import com.rio.mockserver.core.domain.model.ResponsePlan
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.writeFully
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes a [ResponsePlan] on the socket, and does no thinking of its own (SPEC §2.1).
 *
 * Every decision — status, headers, how many bytes, whether to abort — was made in the domain.
 * This class is the mechanism, which is why the chaos failure modes can be asserted in a unit
 * test against a plan rather than only observed through a client.
 *
 * `Content-Length` is written by hand from [ResponsePlan.declaredContentLength] rather than
 * letting Ktor compute it. That is the entire trick behind the malformed-body mode (SPEC §6.2):
 * the header declares the full length while fewer bytes actually go out, so a strict client
 * reports an incomplete body and a lenient one reports a parse error.
 */
@Singleton
class ResponseWriter @Inject constructor() {

    /** Writes [plan] to [call]. */
    suspend fun write(call: ApplicationCall, plan: ResponsePlan) {
        plan.headers.forEach { (name, value) -> call.response.header(name, value) }

        val bytes = plan.body.toByteArray(Charsets.UTF_8)
        val toWrite = plan.bytesToWrite.coerceIn(0, bytes.size)

        call.response.header(CONTENT_LENGTH, plan.declaredContentLength.toString())

        call.respondBytesWriter(
            contentType = plan.contentType(),
            status = HttpStatusCode.fromValue(plan.statusCode),
            // Suppressed for the same reason the header is written by hand: Ktor would otherwise
            // set its own Content-Length from the bytes actually written, undoing the mismatch.
            contentLength = null,
        ) {
            if (toWrite > 0) {
                writeFully(bytes, 0, toWrite)
                flush()
            }

            if (plan.abortAfterWrite) {
                // Cancelling the channel closes the socket without a clean end, so the client
                // sees a reset or a premature EOF rather than a short but tidy response
                // (SPEC §6.2 mode 3).
                throw ConnectionResetSignal
            }
        }
    }

    /**
     * Answers a throwing handler with a 500 rather than dropping the socket.
     *
     * The counterpart to the outbound `safeApiCall` an app with a network layer would have: this
     * app's boundary is inbound, and the failure to avoid is a client left hanging on a socket
     * that silently went away. [ConnectionResetSignal] is re-thrown, because there it is the
     * intended outcome rather than a fault.
     */
    suspend fun respondCatching(call: ApplicationCall, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: ConnectionResetSignal) {
            throw e
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            runCatching {
                write(
                    call,
                    ResponsePlan.complete(
                        statusCode = HttpStatusCode.InternalServerError.value,
                        headers = mapOf(CONTENT_TYPE to ContentType.Application.Json.toString()),
                        body = INTERNAL_ERROR_BODY,
                    ),
                )
            }
        }
    }

    private fun ResponsePlan.contentType(): ContentType {
        val declared = headers.entries
            .firstOrNull { it.key.equals(CONTENT_TYPE, ignoreCase = true) }
            ?.value
            ?: return ContentType.Application.Json
        return runCatching { ContentType.parse(declared) }.getOrDefault(ContentType.Application.Json)
    }

    private companion object {
        const val CONTENT_LENGTH = "Content-Length"
        const val CONTENT_TYPE = "Content-Type"
        const val INTERNAL_ERROR_BODY = """{"error":"Mock server failed to build a response"}"""
    }
}

/**
 * Thrown to kill a connection deliberately (SPEC §6.2 mode 3).
 *
 * An object rather than a class: it carries no information, it is thrown on the request path, and
 * capturing a stack trace for something that is working as designed would be pure cost. It is
 * caught and re-thrown by [ResponseWriter.respondCatching] so the 500 path never swallows it.
 */
object ConnectionResetSignal : RuntimeException("Connection reset injected by the chaos engine") {
    private fun readResolve(): Any = ConnectionResetSignal

    override fun fillInStackTrace(): Throwable = this
}
