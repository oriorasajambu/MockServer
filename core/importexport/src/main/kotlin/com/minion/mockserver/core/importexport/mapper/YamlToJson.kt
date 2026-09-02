package com.minion.mockserver.core.importexport.mapper

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts a YAML document into kotlinx.serialization's JSON tree.
 *
 * This is what lets one OpenAPI traversal serve both JSON and YAML specs. Writing the traversal
 * twice — once over `JsonElement` and once over `YamlNode` — is how the two drift, and the YAML
 * path is the one nobody tests.
 */
object YamlToJson {

    /** Parses [text] as YAML and returns it as a JSON tree, or `null` if it is not YAML. */
    fun parseOrNull(text: String): JsonElement? = try {
        convert(Yaml.default.parseToYamlNode(text))
    } catch (_: Exception) {
        null
    }

    private fun convert(node: YamlNode): JsonElement = when (node) {
        is YamlNull -> JsonNull
        is YamlScalar -> node.toJsonPrimitive()
        is YamlList -> JsonArray(node.items.map(::convert))
        is YamlMap -> JsonObject(node.entries.entries.associate { (key, value) -> key.content to convert(value) })
        // A tagged node (`!!str foo`, `!Ref x`) carries the tag for a schema this app does not
        // read, so the tag is dropped and the value kept.
        is YamlTaggedNode -> convert(node.innerNode)
    }

    /**
     * Types a YAML scalar the way JSON would have.
     *
     * YAML scalars are untyped text, so `true` and `"true"` are indistinguishable after parsing.
     * The traversal reads almost everything as `.content`, where the distinction does not matter;
     * where it does — an `example` value that becomes a mock response body — a number that stays
     * a number produces a body that looks like the spec author wrote it.
     */
    private fun YamlScalar.toJsonPrimitive(): JsonPrimitive = when {
        content == "true" || content == "false" -> JsonPrimitive(content.toBoolean())
        NUMBER.matches(content) -> JsonPrimitive(content.toBigDecimalOrNull())
        else -> JsonPrimitive(content)
    }

    private fun String.toBigDecimalOrNull(): Long? = toLongOrNull()

    private val NUMBER = Regex("""-?\d+""")
}
