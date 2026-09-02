package com.minion.mockserver.core.importexport.dto

import kotlinx.serialization.Serializable

/**
 * HAR 1.2, reduced to what a mock can be built from and what a viewer needs (SPEC §9).
 *
 * Import reads entries as request/response pairs; export writes them so the file opens in Charles,
 * Fiddler or a browser's devtools importer. The fields those viewers require — `startedDateTime`,
 * `time`, the `timings` block, `headersSize`, `bodySize` — are written even where this app has
 * nothing interesting to say, because a viewer that hits a missing required field usually shows
 * an empty window rather than an error.
 */
@Serializable
data class HarFileDto(val log: HarLogDto)

@Serializable
data class HarLogDto(
    val version: String = "1.2",
    val creator: HarCreatorDto = HarCreatorDto(),
    val entries: List<HarEntryDto> = emptyList(),
)

@Serializable
data class HarCreatorDto(
    val name: String = "MockServer",
    val version: String = "1",
)

@Serializable
data class HarEntryDto(
    val startedDateTime: String = "",
    val time: Double = 0.0,
    val request: HarRequestDto,
    val response: HarResponseDto,
    val cache: HarCacheDto = HarCacheDto(),
    val timings: HarTimingsDto = HarTimingsDto(),
    val serverIPAddress: String? = null,
)

@Serializable
data class HarRequestDto(
    val method: String = "GET",
    val url: String = "",
    val httpVersion: String = "HTTP/1.1",
    val headers: List<HarNameValueDto> = emptyList(),
    val queryString: List<HarNameValueDto> = emptyList(),
    val cookies: List<HarNameValueDto> = emptyList(),
    val postData: HarPostDataDto? = null,
    val headersSize: Int = -1,
    val bodySize: Int = -1,
)

@Serializable
data class HarResponseDto(
    val status: Int = 200,
    val statusText: String = "",
    val httpVersion: String = "HTTP/1.1",
    val headers: List<HarNameValueDto> = emptyList(),
    val cookies: List<HarNameValueDto> = emptyList(),
    val content: HarContentDto = HarContentDto(),
    val redirectURL: String = "",
    val headersSize: Int = -1,
    val bodySize: Int = -1,
)

@Serializable
data class HarNameValueDto(val name: String = "", val value: String = "")

@Serializable
data class HarPostDataDto(
    val mimeType: String = "",
    val text: String = "",
)

@Serializable
data class HarContentDto(
    val size: Int = 0,
    val mimeType: String = "application/json",
    val text: String? = null,
)

@Serializable
data class HarCacheDto(val comment: String = "")

/** Every field is required by the HAR schema; `-1` is the spec's "not applicable". */
@Serializable
data class HarTimingsDto(
    val blocked: Double = -1.0,
    val dns: Double = -1.0,
    val connect: Double = -1.0,
    val send: Double = 0.0,
    val wait: Double = 0.0,
    val receive: Double = 0.0,
    val ssl: Double = -1.0,
)
