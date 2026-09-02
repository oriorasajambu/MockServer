package com.minion.mockserver.core.common.config

/**
 * Every tuning number in the app, in one place, grouped by the subsystem that reads it.
 *
 * These are constants of the *design*, not of the build: they are the numbers SPEC-1 fixes, and
 * changing one changes documented behaviour. Values a user or a build can vary — the port, the
 * log capacity, the chaos seed — are settings, and live in `ServerSettings` instead. A magic
 * number in a use case is a bug this file exists to prevent.
 */
object AppConstants {

    /** Binding, ports and the socket (SPEC §8.4). */
    object Server {

        /** The port offered on first launch. */
        const val DEFAULT_PORT = 8080

        /**
         * Ports below this need root on Linux, and therefore on Android. Rejected in the editor
         * with an explanation rather than at bind time with a permission error.
         */
        const val MIN_PORT = 1024

        /** The top of the TCP port range. */
        const val MAX_PORT = 65_535

        /**
         * How many consecutive ports to try after the requested one is refused.
         *
         * `port + 1 … port + 10`, then an actionable error. Eleven attempts is enough to step
         * over a cluster of dev servers and short enough that a doomed start fails visibly fast.
         */
        const val PORT_FALLBACK_ATTEMPTS = 10

        /** Both LAN clients and on-device `localhost` callers are served by one socket. */
        const val DEFAULT_BIND_ADDRESS = "0.0.0.0"

        /** The loopback address shown as its own row on the server screen (SPEC §8.5). */
        const val LOOPBACK_HOST = "localhost"

        /** Default name of the API-key header (SPEC §8.6). */
        const val DEFAULT_API_KEY_HEADER = "X-Mock-Key"

        /** Length of a generated API key, in characters. */
        const val API_KEY_LENGTH = 16
    }

    /** Route matching and specificity scoring (SPEC §4.3). */
    object Matching {

        /** A path segment that must match exactly. */
        const val SCORE_LITERAL_SEGMENT = 10

        /** A `{name}` capture: matches anything, so it is worth less than a literal. */
        const val SCORE_PARAM_SEGMENT = 4

        /** The `**` tail, counted once however many segments it swallows. */
        const val SCORE_TAIL = 1

        /** A method filter other than `ANY`. */
        const val SCORE_EXPLICIT_METHOD = 5

        /** Each header matcher on the route. */
        const val SCORE_HEADER_MATCHER = 3

        /** Each query matcher on the route. */
        const val SCORE_QUERY_MATCHER = 3

        /**
         * Each body-subset matcher.
         *
         * Worth more than a header or query matcher because it is the most specific thing a route
         * can assert, and a route that pins the body should beat one that only pins a header.
         */
        const val SCORE_BODY_MATCHER = 6

        /** The tail wildcard token. */
        const val TAIL_WILDCARD = "**"
    }

    /** The chaos engine (SPEC §6). */
    object Chaos {

        /** Hard ceiling on an injected delay. Above this a request is indistinguishable from a hang. */
        const val MAX_DELAY_MILLIS = 60_000L

        /** Above this the editor warns; it does not block. */
        const val DELAY_WARNING_MILLIS = 30_000L

        /** Sent with an injected `429` so a client's backoff has something to read. */
        const val DEFAULT_RETRY_AFTER_SECONDS = 5

        /** The statuses an error-injection pool draws from when the user has not chosen. */
        val DEFAULT_ERROR_STATUSES = listOf(500, 503, 429)

        /** Body returned with an injected error, unless the profile overrides it. */
        const val DEFAULT_ERROR_BODY = """{"error":"Injected by chaos engine"}"""
    }

    /** The in-memory traffic log (SPEC §7.1). */
    object Traffic {

        /** Entries retained before the oldest is dropped. */
        const val DEFAULT_LOG_CAPACITY = 500

        /** The smallest capacity the settings screen allows. */
        const val MIN_LOG_CAPACITY = 50

        /**
         * The largest capacity the settings screen allows.
         *
         * Bounded because the log is the app's dominant memory cost and the phone has to keep
         * serving after it fills (SPEC §12: steady state under 120 MB).
         */
        const val MAX_LOG_CAPACITY = 2_000

        /** Per-direction body cap in the log. Beyond this the entry is flagged truncated. */
        const val MAX_LOGGED_BODY_BYTES = 256 * 1024

        /**
         * Depth of the hand-off channel between the request path and the log writer.
         *
         * On overflow the *oldest* queued entry is dropped, never the newest, and never the
         * request: logging must not be able to slow the server down (SPEC §7.1).
         */
        const val LOG_CHANNEL_CAPACITY = 256

        /** Debounce on the traffic search field. */
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }

    /** Reading the incoming request (SPEC §5.4). */
    object Request {

        /**
         * Largest request body read into memory. Anything larger is answered `413`.
         *
         * This ceiling protects a phone from a client that decides to upload a video to a mock
         * endpoint.
         */
        const val MAX_BODY_BYTES = 5L * 1024 * 1024

        /** Content-type prefixes whose bodies are never stored as text. */
        val BINARY_CONTENT_TYPE_PREFIXES = listOf(
            "image/",
            "audio/",
            "video/",
            "multipart/",
            "application/octet-stream",
            "application/pdf",
            "application/zip",
        )

        /** Response `Content-Type` when a route does not set one. */
        const val DEFAULT_CONTENT_TYPE = "application/json; charset=utf-8"

        /** Headers Ktor owns; a user-supplied value is ignored with an editor warning (SPEC §5.2). */
        val RESERVED_RESPONSE_HEADERS = listOf("content-length", "date", "server", "connection", "transfer-encoding")
    }

    /** Import and export (SPEC §9). */
    object ImportExport {

        /** Bumped whenever the native export format changes shape, so an import can migrate. */
        const val EXPORT_SCHEMA_VERSION = 1

        /** Largest import file accepted, to keep a malformed 200 MB file from taking the app down. */
        const val MAX_IMPORT_BYTES = 20L * 1024 * 1024

        /** The Postman collection schema this app writes and reads. */
        const val POSTMAN_SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"

        /** The HAR version this app writes. */
        const val HAR_VERSION = "1.2"
    }
}
