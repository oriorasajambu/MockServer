package com.rio.mockserver.core.importexport.exporter

import com.rio.mockserver.core.common.config.AppConstants
import com.rio.mockserver.core.domain.model.ChaosProfile
import com.rio.mockserver.core.domain.model.MockCollection
import com.rio.mockserver.core.domain.model.MockRoute
import com.rio.mockserver.core.domain.model.PathNormaliser
import com.rio.mockserver.core.domain.model.RequestMatcher
import com.rio.mockserver.core.domain.model.TrafficEntry
import com.rio.mockserver.core.domain.repository.ExportGateway
import com.rio.mockserver.core.domain.util.JsonTree
import com.rio.mockserver.core.domain.util.TimeProvider
import com.rio.mockserver.core.importexport.dto.HarCacheDto
import com.rio.mockserver.core.importexport.dto.HarContentDto
import com.rio.mockserver.core.importexport.dto.HarCreatorDto
import com.rio.mockserver.core.importexport.dto.HarEntryDto
import com.rio.mockserver.core.importexport.dto.HarFileDto
import com.rio.mockserver.core.importexport.dto.HarLogDto
import com.rio.mockserver.core.importexport.dto.HarNameValueDto
import com.rio.mockserver.core.importexport.dto.HarPostDataDto
import com.rio.mockserver.core.importexport.dto.HarRequestDto
import com.rio.mockserver.core.importexport.dto.HarResponseDto
import com.rio.mockserver.core.importexport.dto.HarTimingsDto
import com.rio.mockserver.core.importexport.dto.NativeExportDto
import com.rio.mockserver.core.importexport.dto.PostmanBodyDto
import com.rio.mockserver.core.importexport.dto.PostmanCollectionDto
import com.rio.mockserver.core.importexport.dto.PostmanHeaderDto
import com.rio.mockserver.core.importexport.dto.PostmanInfoDto
import com.rio.mockserver.core.importexport.dto.PostmanItemDto
import com.rio.mockserver.core.importexport.dto.PostmanRequestDto
import com.rio.mockserver.core.importexport.dto.PostmanResponseDto
import com.rio.mockserver.core.importexport.dto.PostmanUrlDto
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes every export format (SPEC §7.4, §9.2).
 *
 * All four outputs are formats something else already reads — this app's own JSON, Postman for a
 * team, HAR for Charles or devtools, cURL for a terminal. Nothing here produces data that can only
 * ever come back to this app.
 */
@Singleton
class ExportGatewayImpl @Inject constructor(
    private val timeProvider: TimeProvider,
) : ExportGateway {

    override fun toNativeJson(
        collections: List<MockCollection>,
        routes: List<MockRoute>,
        chaosProfiles: List<ChaosProfile>,
    ): String = JsonTree.Pretty.encodeToString(
        NativeExportDto.serializer(),
        NativeExportDto(
            schemaVersion = AppConstants.ImportExport.EXPORT_SCHEMA_VERSION,
            exportedAt = timeProvider.nowMillis(),
            collections = collections,
            routes = routes,
            chaosProfiles = chaosProfiles,
        ),
    )

    override fun toPostmanCollection(collection: MockCollection, routes: List<MockRoute>): String {
        val dto = PostmanCollectionDto(
            info = PostmanInfoDto(
                name = collection.name,
                schema = AppConstants.ImportExport.POSTMAN_SCHEMA,
                description = "Exported from MockServer. Base path ${collection.basePath}.",
            ),
            item = routes.map { route -> route.toPostmanItem(collection) },
        )
        return JsonTree.Pretty.encodeToString(PostmanCollectionDto.serializer(), dto)
    }

    /**
     * Names exactly what a Postman export will drop.
     *
     * Generated from the routes rather than being a static sentence, so it lists the actual
     * matchers and overrides at stake. A static warning goes stale and gets dismissed unread.
     */
    override fun postmanLossWarnings(routes: List<MockRoute>): List<String> {
        val warnings = mutableListOf<String>()

        val matcherCount = routes.sumOf { it.matchers.size }
        if (matcherCount > 0) {
            val kinds = routes.flatMap { it.matchers }.map { it.describeKind() }.distinct().sorted()
            warnings += "$matcherCount matcher(s) (${kinds.joinToString(", ")}) have no Postman " +
                "equivalent and will not be exported"
        }

        val chaosRoutes = routes.filter { it.hasChaosOverride }
        if (chaosRoutes.isNotEmpty()) {
            warnings += "${chaosRoutes.size} route(s) have chaos overrides, which Postman cannot represent"
        }

        val delayed = routes.count { it.response.delayMillisOverride != null }
        if (delayed > 0) {
            warnings += "$delayed route(s) have a fixed delay, which will not be exported"
        }

        val disabled = routes.count { !it.isEnabled }
        if (disabled > 0) {
            warnings += "$disabled disabled route(s) will be exported as ordinary requests"
        }

        return warnings
    }

    override fun toHar(entries: List<TrafficEntry>, baseUrl: String): String {
        val har = HarFileDto(
            log = HarLogDto(
                version = AppConstants.ImportExport.HAR_VERSION,
                creator = HarCreatorDto(),
                entries = entries.map { it.toHarEntry(baseUrl) },
            ),
        )
        return JsonTree.Pretty.encodeToString(HarFileDto.serializer(), har)
    }

    /**
     * A cURL reproduction of one entry (SPEC §7.4).
     *
     * Against the address currently on screen, not the one the request originally arrived on — the
     * point of copying it is to replay it from a laptop, and the phone's IP may well have changed
     * since the request was captured.
     */
    override fun toCurl(entry: TrafficEntry, baseUrl: String): String {
        val parts = mutableListOf("curl", "-X", entry.method)

        entry.requestHeaders
            .filterKeys { it.lowercase() !in CURL_SKIPPED_HEADERS }
            .forEach { (name, values) ->
                values.forEach { value -> parts += listOf("-H", "$name: $value".shellQuote()) }
            }

        entry.requestBody?.takeIf { it.isNotBlank() }?.let { body ->
            parts += listOf("--data-raw", body.shellQuote())
        }

        parts += entry.fullUrl(baseUrl).shellQuote()
        return parts.joinToString(separator = " ")
    }

    override fun toTranscript(entry: TrafficEntry, baseUrl: String): String = buildString {
        appendLine("${entry.method} ${entry.fullUrl(baseUrl)}")
        entry.requestHeaders.forEach { (name, values) ->
            values.forEach { value -> appendLine("$name: $value") }
        }
        entry.requestBody?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine(JsonTree.prettify(it))
        }

        appendLine()
        appendLine("--- ${entry.statusCode} in ${entry.durationMillis} ms ---")
        entry.matchedRouteName?.let { appendLine("Matched: $it") } ?: appendLine("Unmatched")
        entry.chaosApplied?.let { appendLine("Chaos: $it") }
        appendLine()

        entry.responseHeaders.forEach { (name, value) -> appendLine("$name: $value") }
        entry.responseBody?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            append(JsonTree.prettify(it))
        }
    }

    // ------------------------------------------------------------------ mapping

    private fun MockRoute.toPostmanItem(collection: MockCollection): PostmanItemDto {
        val fullPath = PathNormaliser.join(collection.basePath, pathPattern)
        val segments = PathNormaliser.segments(fullPath)
        return PostmanItemDto(
            name = name,
            request = PostmanRequestDto(
                method = if (method.isExplicit) method.name else "GET",
                header = response.headers.map { (key, value) -> PostmanHeaderDto(key, value) },
                url = PostmanUrlDto(
                    raw = "{{baseUrl}}$fullPath",
                    host = listOf("{{baseUrl}}"),
                    path = segments,
                ),
            ),
            response = listOf(
                PostmanResponseDto(
                    name = "Mock response",
                    code = response.statusCode,
                    status = "",
                    header = response.headers.map { (key, value) -> PostmanHeaderDto(key, value) },
                    body = response.body,
                ),
            ),
        )
    }

    private fun TrafficEntry.toHarEntry(baseUrl: String): HarEntryDto = HarEntryDto(
        startedDateTime = ISO_INSTANT.format(Instant.ofEpochMilli(receivedAt).atOffset(ZoneOffset.UTC)),
        time = durationMillis.toDouble(),
        request = HarRequestDto(
            method = method,
            url = fullUrl(baseUrl),
            headers = requestHeaders.flatMap { (name, values) ->
                values.map { HarNameValueDto(name, it) }
            },
            queryString = query.flatMap { (name, values) -> values.map { HarNameValueDto(name, it) } },
            postData = requestBody?.let {
                HarPostDataDto(mimeType = contentTypeOfRequest(), text = it)
            },
            bodySize = requestByteCount,
        ),
        response = HarResponseDto(
            status = statusCode,
            statusText = "",
            headers = responseHeaders.map { (name, value) -> HarNameValueDto(name, value) },
            content = HarContentDto(
                size = responseBody?.toByteArray(Charsets.UTF_8)?.size ?: 0,
                mimeType = responseHeaders.entries
                    .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                    ?.value
                    ?: AppConstants.Request.DEFAULT_CONTENT_TYPE,
                text = responseBody,
            ),
            bodySize = responseBody?.toByteArray(Charsets.UTF_8)?.size ?: 0,
        ),
        cache = HarCacheDto(),
        // All -1 except the total: this app measures one duration and inventing a breakdown it
        // did not measure would be a fiction a viewer would render as fact.
        timings = HarTimingsDto(receive = durationMillis.toDouble()),
        serverIPAddress = clientIp.takeIf { it.isNotBlank() },
    )

    private fun TrafficEntry.contentTypeOfRequest(): String = requestHeaders.entries
        .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
        ?.value
        ?.firstOrNull()
        ?: AppConstants.Request.DEFAULT_CONTENT_TYPE

    private fun TrafficEntry.fullUrl(baseUrl: String): String {
        val queryString = query.entries
            .flatMap { (name, values) -> values.map { "${name.urlEncode()}=${it.urlEncode()}" } }
            .joinToString(separator = "&")
        val base = baseUrl.trimEnd('/')
        return if (queryString.isEmpty()) "$base$path" else "$base$path?$queryString"
    }

    private fun RequestMatcher.describeKind(): String = when (this) {
        is RequestMatcher.Header -> "header"
        is RequestMatcher.Query -> "query"
        is RequestMatcher.BodySubset -> "body subset"
    }

    /**
     * Single-quotes a value for a POSIX shell.
     *
     * The `'\''` dance is the only way to get a literal single quote through: close the quote,
     * emit an escaped one, reopen. Without it a body containing an apostrophe produces a command
     * that either fails to parse or, worse, runs with the wrong argument boundaries.
     */
    private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

    private fun String.urlEncode(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

    private companion object {
        val ISO_INSTANT: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        /** Headers cURL sets itself; repeating them produces a command that behaves differently. */
        val CURL_SKIPPED_HEADERS = setOf("host", "content-length", "connection")
    }
}
