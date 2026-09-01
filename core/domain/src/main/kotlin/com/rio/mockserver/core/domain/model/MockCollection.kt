package com.rio.mockserver.core.domain.model

import kotlinx.serialization.Serializable

/**
 * A named group of routes sharing a base path (SPEC §3).
 *
 * Several collections can be enabled at once — SPEC §17.1 settles that deliberately. Because each
 * carries a base-path prefix, two enabled collections usually do not overlap at all; where they
 * do, the scoring chain in [MatchScore] resolves it, with [order] as the tie-break a user can
 * actually control.
 */
@Serializable
data class MockCollection(
    /** UUID. Stable across export and re-import, so a conflict can be recognised. */
    val id: String,

    /** What the user calls it. Never part of matching. */
    val name: String,

    /**
     * The prefix every route in this collection hangs off, e.g. `/api/v1`.
     *
     * Normalised on the way in: leading slash, no trailing slash, no duplicate slashes. An empty
     * base path (`/`) is legal and means the routes are mounted at the root.
     */
    val basePath: String,

    /** A disabled collection contributes no candidates, whatever its routes say. */
    val isEnabled: Boolean = true,

    /** Tie-break priority when two routes score identically. Lower wins (SPEC §4.3). */
    val order: Int = 0,

    /** Browser access policy for this collection's paths. */
    val cors: CorsConfig = CorsConfig.DISABLED,

    /** Epoch millis. Used only for display ordering. */
    val createdAt: Long = 0L,
)
