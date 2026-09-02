package com.minion.mockserver.core.server

import com.minion.mockserver.core.domain.model.ResponsePlan
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.http.content.OutgoingContent
import io.ktor.server.response.respond
import io.ktor.utils.io.ByteWriteChannel
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
 * `Content-Length` comes from [ResponsePlan.declaredContentLength], not from the bytes actually
 * written. That is the entire trick behind the malformed-body mode (SPEC §6.2): the header
 * declares the full length while fewer bytes go out, so a strict client reports an incomplete
 * body and a lenient one reports a parse error.
 *
 * It is declared to Ktor through [OutgoingContent.contentLength] rather than set as a raw header,
 * and the difference is not cosmetic. A body of unknown length makes Ktor fall back to
 * `Transfer-Encoding: chunked`, and chunked framing does two things this app cannot afford:
 * it puts a `Content-Length` and a `Transfer-Encoding` on the same response, which RFC 7230
 * §3.3.3 forbids outright, and it terminates the body with a zero-length chunk — so a truncated
 * body arrives as a *complete* short one and the malformed-body mode quietly stops failing
 * anything. Declaring the length keeps identity framing, where writing fewer bytes than promised
 * is exactly the premature EOF the spec is asking for.
 */
@Singleton
class ResponseWriter @Inject constructor() {

    /** Writes [plan] to [call]. */
    suspend fun write(call: ApplicationCall, plan: ResponsePlan) {
        plan.headers
            // Content-Length is the writer's to set, and setting it here as well would emit it
            // twice — once from the plan's headers and once from the content below.
            .filterKeys { !it.equals(CONTENT_LENGTH, ignoreCase = true) }
            .forEach { (name, value) -> call.response.header(name, value) }

        val bytes = plan.body.toByteArray(Charsets.UTF_8)
        val toWrite = plan.bytesToWrite.coerceIn(0, bytes.size)

        call.respond(
            PlannedBody(
                contentType = plan.contentType(),
                status = HttpStatusCode.fromValue(plan.statusCode),
                declaredLength = plan.declaredContentLength.toLong(),
            ) { channel ->
                if (toWrite > 0) {
                    channel.writeFully(bytes, 0, toWrite)
                    channel.flush()
                }

                if (plan.abortAfterWrite) {
                    // Cancelling the channel closes the socket without a clean end, so the client
                    // sees a reset or a premature EOF rather than a short but tidy response
                    // (SPEC §6.2 mode 3).
                    throw ConnectionResetSignal
                }
            },
        )
    }

    /**
     * A response body whose declared length is the plan's, not the number of bytes written.
     *
     * The one reason this is a hand-rolled [OutgoingContent] rather than `respondBytesWriter`:
     * that helper takes a nullable length and passing `null` — which is what suppressing Ktor's
     * own computed length requires — is precisely what selects chunked framing.
     */
    private class PlannedBody(
        override val contentType: ContentType,
        override val status: HttpStatusCode,
        private val declaredLength: Long,
        private val body: suspend (ByteWriteChannel) -> Unit,
    ) : OutgoingContent.WriteChannelContent() {

        override val contentLength: Long get() = declaredLength

        override suspend fun writeTo(channel: ByteWriteChannel) = body(channel)
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
