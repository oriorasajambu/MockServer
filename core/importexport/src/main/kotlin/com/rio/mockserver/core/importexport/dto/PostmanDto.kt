package com.rio.mockserver.core.importexport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Postman collection v2.1, reduced to the fields this app reads and writes (SPEC §9.1, §9.2).
 *
 * A real collection carries auth blocks, pre-request scripts, test scripts, variables and events;
 * none of them have a meaning here, and `ignoreUnknownKeys` lets them pass through the parser
 * untouched rather than making a perfectly good collection unimportable.
 */
@Serializable
data class PostmanCollectionDto(
    val info: PostmanInfoDto,
    val item: List<PostmanItemDto> = emptyList(),
)

@Serializable
data class PostmanInfoDto(
    val name: String = "Imported collection",
    val schema: String = "",
    @SerialName("_postman_id") val postmanId: String? = null,
    val description: String? = null,
)

/**
 * One request, or a folder of them.
 *
 * Postman models both with the same type: an item with `request` is a request, an item with
 * `item` is a folder, and folders nest arbitrarily. The importer flattens them, because a
 * collection here is one flat list of routes under a base path.
 */
@Serializable
data class PostmanItemDto(
    val name: String = "",
    val request: PostmanRequestDto? = null,
    val response: List<PostmanResponseDto> = emptyList(),
    val item: List<PostmanItemDto> = emptyList(),
)

@Serializable
data class PostmanRequestDto(
    val method: String = "GET",
    val header: List<PostmanHeaderDto> = emptyList(),
    @Serializable(with = PostmanUrlSerializer::class)
    val url: PostmanUrlDto? = null,
    val body: PostmanBodyDto? = null,
)

@Serializable
data class PostmanHeaderDto(
    val key: String = "",
    val value: String = "",
    val disabled: Boolean = false,
)

/**
 * A Postman URL, which is either a string or an object.
 *
 * Modelled as the object form with every field optional; [PostmanUrlSerializer] coerces the
 * string form before deserialisation, so the importer only ever sees one shape.
 */
@Serializable
data class PostmanUrlDto(
    val raw: String = "",
    val host: List<String> = emptyList(),
    val path: List<String> = emptyList(),
    val query: List<PostmanQueryDto> = emptyList(),
)

@Serializable
data class PostmanQueryDto(
    val key: String = "",
    val value: String? = null,
    val disabled: Boolean = false,
)

@Serializable
data class PostmanBodyDto(
    val mode: String = "raw",
    val raw: String = "",
)

/** A saved example response — the only place a Postman collection carries a body to mock with. */
@Serializable
data class PostmanResponseDto(
    val name: String = "",
    val status: String = "",
    val code: Int = 200,
    val header: List<PostmanHeaderDto> = emptyList(),
    val body: String? = null,
)
