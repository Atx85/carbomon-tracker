package com.example.carbomon

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

// A validated preview has stable, app-generated identities. AI output cannot pick
// existing IDs and overwrite saved entries, and retrying the same preview is safe.
class CatalogImportDraft internal constructor(val id: String, val kind: String, val catalogJson: String)

internal fun catalogAiPrompt(kind: String, schema: String, example: String): String {
    require(kind == "food" || kind == "recipe")
    return """
        Help me create a $kind for CarboMon. Ask me about the food, package label or recipe first.
        Return one JSON object or a JSON list matching the schema below, with no Markdown or explanation around the final JSON.
        All nutrition is PER 100 GRAMS: energy in kcal, macros/fibre/sugar in g, minerals and cholesterol in mg.
        For recipes, gramsUsed is the amount of each ingredient used. Ingredient nutrition is still per 100 g, not per portion.
        Include finishedWeightGrams only if I supply a measured finished weight; otherwise omit it or use null.
        Use my labels or reliable sources; identify any agreed estimates in nutritionSource (food) or notes (recipe).
        Do not invent missing required nutrition or treat unknown values as measured zeros. Ask me for missing core values.
        Unknown cholesterol must be null or omitted. Optional missing nutrients use catalogue defaults (zero); include them when known.
        Do not include record IDs, revisions or a catalogue wrapper. Food source links belong in nutritionSource; recipe instructions belong in notes.
        The example is illustrative only. Replace its values with the values established in our chat.

        JSON SCHEMA:
        $schema

        EXAMPLE:
        $example

        My food / recipe and source information:
    """.trimIndent()
}

// Implements the validation keywords used by the bundled server schemas. Both
// sides ship the same schema files, rather than maintaining two JSON shapes.
internal fun validateCatalogJson(value: Any?, schema: JSONObject, path: String = "item") {
    fun reject(message: String): Nothing = throw IllegalArgumentException("$path: $message")
    val types = when (val type = schema.opt("type")) {
        is String -> listOf(type)
        is JSONArray -> (0 until type.length()).map { type.getString(it) }
        else -> emptyList()
    }
    fun matches(type: String): Boolean = when (type) {
        "null" -> value == null || value == JSONObject.NULL
        "object" -> value is JSONObject
        "array" -> value is JSONArray
        "string" -> value is String
        "number" -> value is Number && value.toDouble().isFinite()
        "integer" -> value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0
        else -> false
    }
    if (types.isNotEmpty() && types.none(::matches)) reject("expected ${types.joinToString(" or ")}")
    schema.optJSONArray("enum")?.let { allowed ->
        if ((0 until allowed.length()).none { canonicalJson(allowed.get(it)) == canonicalJson(value) }) reject("unsupported value")
    }
    when (value) {
        is JSONObject -> {
            schema.optJSONArray("required")?.let { required ->
                for (i in 0 until required.length()) if (!value.has(required.getString(i))) reject("missing ${required.getString(i)}")
            }
            val properties = schema.optJSONObject("properties") ?: JSONObject()
            value.keys().asSequence().forEach { key ->
                if (schema.opt("additionalProperties") == false && !properties.has(key)) reject("unknown field $key")
                properties.optJSONObject(key)?.let { validateCatalogJson(value.get(key), it, "$path.$key") }
            }
        }
        is JSONArray -> {
            if (schema.has("minItems") && value.length() < schema.getInt("minItems")) reject("not enough items")
            if (schema.has("maxItems") && value.length() > schema.getInt("maxItems")) reject("too many items")
            schema.optJSONObject("items")?.let { itemSchema ->
                for (i in 0 until value.length()) validateCatalogJson(value.get(i), itemSchema, "$path[${i + 1}]")
            }
        }
        is String -> {
            val length = value.codePointCount(0, value.length)
            if (schema.has("minLength") && length < schema.getInt("minLength")) reject("text is too short")
            if (schema.has("maxLength") && length > schema.getInt("maxLength")) reject("text is too long")
            if (schema.has("pattern") && !Regex(schema.getString("pattern")).containsMatchIn(value)) reject("text must not be blank")
        }
        is Number -> {
            val number = value.toDouble()
            if (!number.isFinite()) reject("number must be finite")
            if (schema.has("minimum") && number < schema.getDouble("minimum")) reject("minimum is ${schema.get("minimum")}")
            if (schema.has("maximum") && number > schema.getDouble("maximum")) reject("maximum is ${schema.get("maximum")}")
            if (schema.has("exclusiveMinimum") && number <= schema.getDouble("exclusiveMinimum")) reject("must be greater than ${schema.get("exclusiveMinimum")}")
        }
    }
}

internal fun prepareCatalogImport(raw: String, kind: String, schema: JSONObject): CatalogImportDraft {
    require(kind == "food" || kind == "recipe") { "Choose food or recipe" }
    require(raw.length <= 512_000) { "JSON is too large (512,000 character limit)" }
    var text = raw.trim().removePrefix("\uFEFF")
    if (text.startsWith("```")) {
        val lines = text.lines()
        require(lines.first().lowercase() in setOf("```", "```json") && lines.last() == "```") { "Paste only the JSON, without surrounding chat" }
        text = lines.drop(1).dropLast(1).joinToString("\n")
    }
    // Bound nesting before invoking the recursive JSON parser on pasted input.
    var depth = 0
    var quote: Char? = null
    var escaped = false
    text.forEach { char ->
        if (quote != null) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == quote) quote = null
        } else when (char) {
            '\"', '\'' -> quote = char
            '{', '[' -> { depth++; require(depth <= 16) { "JSON nesting is too deep" } }
            '}', ']' -> depth--
        }
    }
    val tokener = JSONTokener(text)
    val parsed = tokener.nextValue()
    require(tokener.nextClean() == '\u0000') { "Unexpected text after JSON" }
    val entries = when (parsed) {
        is JSONObject -> listOf(parsed)
        is JSONArray -> {
            require(parsed.length() in 1..100) { "Import between 1 and 100 items at a time" }
            parsed.objects()
        }
        else -> error("Paste a JSON object or a list of objects")
    }
    entries.forEachIndexed { index, item -> validateCatalogJson(item, schema, "item ${index + 1}") }
    fun nutrients(item: JSONObject) {
        catalogNutrients.forEach { if (!item.has(it)) item.put(it, 0.0) }
        item.putCholesterol(item.storedCholesterol())
    }
    val records = JSONArray()
    entries.forEach { original ->
        val item = JSONObject(original.toString())
        if (kind == "food") {
            nutrients(item)
            if (!item.has("brand")) item.put("brand", "")
            if (!item.has("nutritionSource")) item.put("nutritionSource", "")
        } else {
            if (!item.has("notes")) item.put("notes", "")
            if (!item.has("createdAtEpochMs")) item.put("createdAtEpochMs", System.currentTimeMillis())
            if (!item.has("finishedWeightGrams")) item.put("finishedWeightGrams", JSONObject.NULL)
            item.getJSONArray("ingredients").objects().forEach { ingredient ->
                nutrients(ingredient)
                if (!ingredient.has("foodId")) ingredient.put("foodId", "")
                if (!ingredient.has("foodSource")) ingredient.put("foodSource", "MANUAL")
            }
        }
        records.put(JSONObject().put("kind", kind).put("id", "catalog-${UUID.randomUUID()}")
            .put("revision", 1).put("deleted", false).put("data", item))
    }
    val catalog = JSONObject().put("schemaVersion", 1).put("serverId", "offline-import").put("records", records)
    catalogToStorage(catalog)
    return CatalogImportDraft(UUID.randomUUID().toString(), kind, catalog.toString())
}

internal fun appendCatalogImport(foods: JSONArray, recipes: JSONArray, draft: CatalogImportDraft): CatalogStorage {
    val incoming = catalogToStorage(JSONObject(draft.catalogJson))
    fun append(existing: JSONArray, additions: JSONArray): JSONArray {
        val byId = existing.objects().associateBy { it.getString("id") }
        val result = JSONArray(existing.toString())
        additions.objects().forEach { item ->
            val old = byId[item.getString("id")]
            require(old == null || canonicalJson(old) == canonicalJson(item)) { "An imported item was changed. Review the JSON again." }
            if (old == null) result.put(item)
        }
        return result
    }
    return CatalogStorage(append(foods, incoming.foods), append(recipes, incoming.recipes))
}
