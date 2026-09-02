package com.minion.mockserver.core.domain.usecase.request

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC §4.4 — deep subset assertion against the parsed request body. */
class BodySubsetMatcherTest {

    private val matcher = BodySubsetMatcher()

    @Test
    fun `extra keys in the request are ignored`() {
        assertMatches(
            """{"type":"express"}""",
            """{"type":"express","weight":3,"note":"handle with care"}""",
        )
    }

    @Test
    fun `a missing key fails`() {
        assertDoesNotMatch("""{"type":"express"}""", """{"weight":3}""")
    }

    @Test
    fun `nested objects recurse`() {
        assertMatches(
            """{"customer":{"tier":"gold"}}""",
            """{"customer":{"id":7,"tier":"gold","since":2019},"total":12}""",
        )
        assertDoesNotMatch(
            """{"customer":{"tier":"gold"}}""",
            """{"customer":{"id":7,"tier":"silver"}}""",
        )
    }

    @Test
    fun `arrays match by containment, not by position`() {
        assertMatches("""{"tags":["b"]}""", """{"tags":["a","b","c"]}""")
        assertMatches("""{"tags":["c","a"]}""", """{"tags":["a","b","c"]}""")
        assertDoesNotMatch("""{"tags":["z"]}""", """{"tags":["a","b","c"]}""")
    }

    @Test
    fun `an object inside an array matches any element containing it`() {
        assertMatches(
            """{"items":[{"sku":"X1"}]}""",
            """{"items":[{"sku":"A0","qty":1},{"sku":"X1","qty":2}]}""",
        )
    }

    @Test
    fun `scalar comparison is type-sensitive`() {
        assertDoesNotMatch("""{"id":"1"}""", """{"id":1}""")
        assertDoesNotMatch("""{"id":1}""", """{"id":"1"}""")
        assertDoesNotMatch("""{"ok":true}""", """{"ok":"true"}""")
        assertMatches("""{"id":1}""", """{"id":1}""")
    }

    @Test
    fun `numeric formatting is compared as written`() {
        // `1` and `1.0` are different tokens, and treating them as equal would make a matcher
        // written against an integer id silently accept a float.
        assertDoesNotMatch("""{"n":1}""", """{"n":1.0}""")
    }

    @Test
    fun `null in the matcher means present with a null value, not absent`() {
        assertMatches("""{"deletedAt":null}""", """{"deletedAt":null}""")
        assertDoesNotMatch("""{"deletedAt":null}""", """{"id":1}""")
        assertDoesNotMatch("""{"deletedAt":null}""", """{"deletedAt":"2024-01-01"}""")
    }

    @Test
    fun `an invalid request body fails without erroring`() {
        // The candidate is eliminated; the request still gets matched by path-only routes and
        // still gets an answer.
        assertDoesNotMatch("""{"a":1}""", "not json at all")
        assertDoesNotMatch("""{"a":1}""", null)
        assertDoesNotMatch("""{"a":1}""", "")
    }

    @Test
    fun `an unparseable matcher fails rather than matching everything`() {
        assertFalse(matcher.matches("{ this is not json", """{"a":1}"""))
    }

    @Test
    fun `an empty object matcher matches any object`() {
        assertMatches("{}", """{"anything":true}""")
        assertDoesNotMatch("{}", """["an","array"]""")
    }

    @Test
    fun `type mismatches between container kinds fail`() {
        assertDoesNotMatch("""{"a":1}""", """[{"a":1}]""")
        assertDoesNotMatch("""["a"]""", """{"0":"a"}""")
    }

    private fun assertMatches(matcherJson: String, body: String?) {
        assertTrue("$matcherJson should match $body", matcher.matches(matcherJson, body))
    }

    private fun assertDoesNotMatch(matcherJson: String, body: String?) {
        assertFalse("$matcherJson should not match $body", matcher.matches(matcherJson, body))
    }
}
