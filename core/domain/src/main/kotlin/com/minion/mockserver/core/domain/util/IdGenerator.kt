package com.minion.mockserver.core.domain.util

import java.util.UUID

/**
 * Where new identifiers come from.
 *
 * Injected so that a test asserting on a saved route's id does not have to accept whatever a
 * random UUID happened to be — and so that an importer producing a hundred routes can be checked
 * for id collisions deterministically.
 */
fun interface IdGenerator {

    /** A new unique identifier. */
    fun newId(): String

    companion object {

        /** Random UUIDs. Bound by Hilt in `:core:data`. */
        val Uuid: IdGenerator = IdGenerator { UUID.randomUUID().toString() }
    }
}
