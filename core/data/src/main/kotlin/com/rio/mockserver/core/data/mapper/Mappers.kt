package com.rio.mockserver.core.data.mapper

import com.rio.mockserver.core.data.local.entity.ChaosProfileEntity
import com.rio.mockserver.core.data.local.entity.CollectionEntity
import com.rio.mockserver.core.data.local.entity.RouteEntity
import com.rio.mockserver.core.domain.model.ChaosProfile
import com.rio.mockserver.core.domain.model.ConnectionResetSpec
import com.rio.mockserver.core.domain.model.CorsConfig
import com.rio.mockserver.core.domain.model.DelaySpec
import com.rio.mockserver.core.domain.model.ErrorInjection
import com.rio.mockserver.core.domain.model.HttpMethodFilter
import com.rio.mockserver.core.domain.model.MalformedBodySpec
import com.rio.mockserver.core.domain.model.MockCollection
import com.rio.mockserver.core.domain.model.MockResponse
import com.rio.mockserver.core.domain.model.MockRoute
import com.rio.mockserver.core.domain.model.RequestMatcher
import com.rio.mockserver.core.domain.util.JsonTree
import kotlinx.serialization.builtins.ListSerializer

/**
 * Entity ↔ domain, with the JSON columns encoded and decoded here rather than by Room.
 *
 * Every decode has a fallback. A row whose `matchersJson` will not parse — a hand-edited
 * database, an interrupted migration, a future schema read by an older build — degrades to a
 * route with no matchers rather than taking down the read that loaded it. A mock server that
 * refuses to start because one row is malformed is far worse than one that serves a slightly
 * wrong rule and shows it in the list.
 */
internal object Mappers {

    // ---------------------------------------------------------------- collections

    fun CollectionEntity.toDomain(): MockCollection = MockCollection(
        id = id,
        name = name,
        basePath = basePath,
        isEnabled = isEnabled,
        order = sortOrder,
        cors = decode(corsJson, CorsConfig.serializer(), CorsConfig.DISABLED),
        createdAt = createdAt,
    )

    fun MockCollection.toEntity(): CollectionEntity = CollectionEntity(
        id = id,
        name = name,
        basePath = basePath,
        isEnabled = isEnabled,
        sortOrder = order,
        corsJson = encode(cors, CorsConfig.serializer()),
        createdAt = createdAt,
    )

    // ---------------------------------------------------------------- routes

    fun RouteEntity.toDomain(): MockRoute = MockRoute(
        id = id,
        collectionId = collectionId,
        name = name,
        // An unrecognised method string means a row from a future version; ANY is the widest
        // reading, which keeps the route serving something rather than silently vanishing.
        method = HttpMethodFilter.entries.firstOrNull { it.name == method } ?: HttpMethodFilter.ANY,
        pathPattern = pathPattern,
        matchers = decode(matchersJson, MATCHER_LIST, emptyList()),
        response = decode(responseJson, MockResponse.serializer(), MockResponse()),
        chaosOverride = chaosOverrideJson?.let {
            decodeOrNull(it, ChaosProfile.serializer())
        },
        isEnabled = isEnabled,
        createdAt = createdAt,
        needsBody = needsBody,
    )

    fun MockRoute.toEntity(): RouteEntity = RouteEntity(
        id = id,
        collectionId = collectionId,
        name = name,
        method = method.name,
        pathPattern = pathPattern,
        matchersJson = encode(matchers, MATCHER_LIST),
        responseJson = encode(response, MockResponse.serializer()),
        chaosOverrideJson = chaosOverride?.let { encode(it, ChaosProfile.serializer()) },
        isEnabled = isEnabled,
        createdAt = createdAt,
        needsBody = needsBody,
    )

    // ---------------------------------------------------------------- chaos

    fun ChaosProfileEntity.toDomain(): ChaosProfile = ChaosProfile(
        id = id,
        name = name,
        delay = decode(delayJson, DelaySpec.serializer(), DelaySpec.None),
        errorInjection = decode(errorInjectionJson, ErrorInjection.serializer(), ErrorInjection.OFF),
        connectionReset = decode(connectionResetJson, ConnectionResetSpec.serializer(), ConnectionResetSpec.OFF),
        malformedBody = decode(malformedBodyJson, MalformedBodySpec.serializer(), MalformedBodySpec.OFF),
    )

    fun ChaosProfile.toEntity(): ChaosProfileEntity = ChaosProfileEntity(
        id = id,
        name = name,
        delayJson = encode(delay, DelaySpec.serializer()),
        errorInjectionJson = encode(errorInjection, ErrorInjection.serializer()),
        connectionResetJson = encode(connectionReset, ConnectionResetSpec.serializer()),
        malformedBodyJson = encode(malformedBody, MalformedBodySpec.serializer()),
    )

    // ---------------------------------------------------------------- plumbing

    private val MATCHER_LIST = ListSerializer(RequestMatcher.serializer())

    private fun <T> encode(
        value: T,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): String = JsonTree.Permissive.encodeToString(serializer, value)

    private fun <T> decode(
        json: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        fallback: T,
    ): T = try {
        JsonTree.Permissive.decodeFromString(serializer, json)
    } catch (_: Exception) {
        fallback
    }

    /**
     * [decode] for a nullable column: a malformed value degrades to "no override" rather than to
     * a fabricated default, because inventing a chaos profile nobody wrote would inject failures
     * into a route that never asked for them.
     */
    private fun <T : Any> decodeOrNull(
        json: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T? = try {
        JsonTree.Permissive.decodeFromString(serializer, json)
    } catch (_: Exception) {
        null
    }
}
