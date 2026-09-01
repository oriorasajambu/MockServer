package com.rio.mockserver.core.domain.model

import kotlinx.serialization.Serializable

/**
 * One rule: when a request looks like *this*, answer with *that* (SPEC §3).
 *
 * A route is meaningless without its collection — [pathPattern] is relative to the collection's
 * base path, and the collection's `order` is part of this route's tie-break. Anything that needs
 * both is given a [CompiledRoute] rather than a bare `MockRoute`.
 */
@Serializable
data class MockRoute(
    val id: String,
    val collectionId: String,

    /** What the user calls it. Shown in the traffic log against every request it matched. */
    val name: String,

    /** Which methods reach this route. */
    val method: HttpMethodFilter = HttpMethodFilter.ANY,

    /** Relative to the collection's base path: `/users/{id}`, or `/files/` ending in a `**` tail (SPEC §4.2). */
    val pathPattern: String = "/",

    /** Extra conditions, AND-ed. Each one raises specificity. */
    val matchers: List<RequestMatcher> = emptyList(),

    val response: MockResponse = MockResponse(),

    /** `null` inherits the global profile. A non-null override replaces it wholesale (SPEC §6.1). */
    val chaosOverride: ChaosProfile? = null,

    /** A disabled route contributes no candidates but stays in the list, greyed. */
    val isEnabled: Boolean = true,

    /**
     * Epoch millis. Part of the tie-break chain — the *older* route wins, so adding a duplicate
     * never silently steals traffic from the rule already in use (SPEC §4.3).
     */
    val createdAt: Long = 0L,

    /**
     * Set by an importer when a spec defined the operation but no example body (SPEC §9.1).
     *
     * The route is created anyway, with an empty body and this flag, because a missing mock you
     * can see beats a generated `{"name":"string"}` that looks real and tests nothing.
     */
    val needsBody: Boolean = false,
) {

    /** Whether chaos is configured specifically for this route, for the list-row icon (SPEC §6.1). */
    val hasChaosOverride: Boolean get() = chaosOverride != null
}
