package com.rio.mockserver.core.domain.usecase.request

import com.rio.mockserver.core.domain.util.JsonTree
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether a matcher fragment is a deep subset of a request body (SPEC §4.4).
 *
 * Subset rather than equality is what makes body matching usable on a phone: you paste the two
 * fields that identify the request and ignore the other forty. The rules, in full:
 *
 * - **Objects** — every key in the matcher must exist in the request with a matching value. Extra
 *   keys in the request are ignored. This recurses.
 * - **Arrays** — every matcher element must find *some* matching element in the request array.
 *   Order is not significant, and the request may contain extra elements.
 * - **Scalars** — strict equality, type-sensitive: `"1"` does not match `1`, and `1` does not
 *   match `1.0`.
 * - **`null`** — a `null` in the matcher means "this key is present and its value is null", not
 *   "this key is absent". Absence assertions on bodies wait for v2.
 * - **Invalid request JSON** eliminates the candidate. It is never an error: a client posting
 *   malformed JSON should still be matched by the path-only routes, and should see the mock
 *   server's answer rather than a crash.
 */
@Singleton
class BodySubsetMatcher @Inject constructor() {

    /**
     * Matcher fragments parsed once and reused.
     *
     * Keyed on the fragment text, which is stable for the life of a route, so the map is bounded
     * by the number of body matchers the user has authored. Without it every request re-parses
     * every candidate's fragment — parsing work that depends only on data that has not changed.
     */
    private val parsedMatchers = ConcurrentHashMap<String, JsonElement>()

    /** Whether [requestBody] satisfies [matcherJson]. `false` for any unparseable input. */
    fun matches(matcherJson: String, requestBody: String?): Boolean {
        val expected = parsedMatchers.getOrPut(matcherJson) {
            JsonTree.parseOrNull(matcherJson) ?: return false
        }
        val actual = JsonTree.parseOrNull(requestBody) ?: return false
        return isSubset(expected, actual)
    }

    /** The recursive rule. Visible for tests, which drive it directly against parsed trees. */
    fun isSubset(expected: JsonElement, actual: JsonElement): Boolean = when (expected) {
        is JsonObject -> actual is JsonObject && expected.all { (key, value) ->
            // `containsKey` first, so a matcher asking for an explicit null distinguishes
            // "present and null" from "absent" — `actual[key]` alone returns null for both.
            actual.containsKey(key) && isSubset(value, actual.getValue(key))
        }

        is JsonArray -> actual is JsonArray && expected.all { element ->
            actual.any { candidate -> isSubset(element, candidate) }
        }

        // JsonPrimitive equality compares `isString` as well as `content`, which is exactly the
        // type-sensitivity SPEC §4.4 asks for: JsonPrimitive("1") != JsonPrimitive(1).
        is JsonPrimitive -> expected == actual
    }
}
