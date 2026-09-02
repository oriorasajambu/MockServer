package com.minion.mockserver.core.domain.model

/**
 * A complete instruction for the transport: exactly what to put on the socket.
 *
 * The domain decides everything — status, headers, the rendered body, how much of it to write,
 * what `Content-Length` should claim, and whether to kill the connection afterwards — and the
 * writer in `:core:server` does no thinking at all. That is what makes chaos testable without a
 * socket: a fake writer records the plan, and the assertions are about the plan.
 *
 * The three numbers are deliberately separate:
 * - [declaredContentLength] is what the header says.
 * - [bytesToWrite] is what actually goes out.
 * - They differ only under chaos, which is the entire mechanism of SPEC §6.2 modes 3 and 4.
 */
data class ResponsePlan(
    val statusCode: Int,

    /**
     * Every header except `Content-Length`, which the writer emits from [declaredContentLength].
     *
     * CORS headers are already merged in, after the user's own, so they win on conflict
     * (SPEC §5.2).
     */
    val headers: Map<String, String>,

    /** The fully rendered body, placeholders resolved. */
    val body: String,

    /** What `Content-Length` declares, in bytes of UTF-8. */
    val declaredContentLength: Int,

    /**
     * How many bytes of [body] to write.
     *
     * Zero for `HEAD` (SPEC §5.3) and for a reset that fires before any body. Less than
     * [declaredContentLength] for a truncated or reset response — cutting mid-character is
     * intentional there, since a client choking on a partial UTF-8 sequence is a realistic
     * failure to be able to reproduce.
     */
    val bytesToWrite: Int,

    /**
     * Whether to cancel the response channel after writing, so the socket closes without a clean
     * end and the client sees a reset or a premature EOF (SPEC §6.2 mode 3).
     */
    val abortAfterWrite: Boolean = false,
) {

    companion object {

        /** A plan that writes [body] in full under an honest `Content-Length`. */
        fun complete(
            statusCode: Int,
            headers: Map<String, String>,
            body: String,
        ): ResponsePlan {
            val length = body.toByteArray(Charsets.UTF_8).size
            return ResponsePlan(
                statusCode = statusCode,
                headers = headers,
                body = body,
                declaredContentLength = length,
                bytesToWrite = length,
            )
        }
    }
}
