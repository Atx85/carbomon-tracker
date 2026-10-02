package com.example.carbomon

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.UUID

internal val catalogNutrients = listOf(
    "caloriesPer100g", "proteinPer100g", "carbsPer100g", "fatPer100g",
    "fiberPer100g", "sugarPer100g", "sodiumMgPer100g", "potassiumMgPer100g",
    "calciumMgPer100g", "ironMgPer100g"
)

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
internal fun recordKey(record: JSONObject): String = record.getString("kind") + ":" + record.getString("id")

// JSON key order and 1 vs 1.0 must not create phantom edits after a round trip.
internal fun canonicalJson(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
        JSONObject.quote(it) + ":" + canonicalJson(value.get(it))
    }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalJson(value.get(it)) }
    is Number -> value.toDouble().toString()
    is Boolean -> value.toString()
    else -> JSONObject.quote(value.toString())
}

private fun nutrientData(source: JSONObject): JSONObject = JSONObject().apply {
    catalogNutrients.forEach { key -> put(key, source.optDouble(key, 0.0)) }
    putCholesterol(source.storedCholesterol())
}

internal data class CatalogPlan(val changes: JSONArray)
internal enum class ConflictChoice { LOCAL, SERVER }
internal class CatalogConflict(val records: JSONArray) : Exception("These entries changed on another device. Choose which version to keep.")
internal data class CatalogSyncResult(val foods: Int, val recipes: Int)

internal fun planCatalogSync(
    foods: JSONArray,
    recipes: JSONArray,
    baseline: JSONArray,
    deviceId: String,
    conflicts: JSONArray = JSONArray(),
    choice: ConflictChoice? = null
): CatalogPlan {
    val previous = baseline.objects().associateBy(::recordKey)
    fun sharedId(kind: String, id: String): String = if (id.startsWith("catalog-") || previous.containsKey("$kind:$id")) id
        else "catalog-" + UUID.nameUUIDFromBytes("$deviceId/$kind/$id".toByteArray(Charsets.UTF_8))
    val foodIds = foods.objects().filter { it.optString("source") == "MANUAL" }
        .associate { it.getString("id") to sharedId("food", it.getString("id")) }
    val local = linkedMapOf<String, JSONObject>()
    fun add(kind: String, id: String, data: JSONObject) {
        val shared = sharedId(kind, id)
        local["$kind:$shared"] = JSONObject().put("kind", kind).put("id", shared).put("data", data).put("deleted", false)
    }
    foods.objects().filter { it.optString("source") == "MANUAL" }.forEach { food ->
        add("food", food.getString("id"), nutrientData(food)
            .put("description", food.getString("description")).put("brand", food.optString("brand", ""))
            .put("nutritionSource", food.optString("nutritionSource", "")))
    }
    recipes.objects().forEach { recipe ->
        val ingredients = JSONArray()
        recipe.getJSONArray("ingredients").objects().forEach { ingredient ->
            val source = ingredient.optString("foodSource", "MANUAL")
            val id = ingredient.optString("foodId", "")
            ingredients.put(nutrientData(ingredient)
                .put("foodId", if (source == "MANUAL") foodIds[id] ?: id else id)
                .put("foodSource", source).put("foodDescription", ingredient.getString("foodDescription"))
                .put("gramsUsed", ingredient.getDouble("gramsUsed")))
        }
        add("recipe", recipe.getString("id"), JSONObject()
            .put("name", recipe.getString("name")).put("notes", recipe.optString("notes", ""))
            .put("createdAtEpochMs", recipe.optLong("createdAtEpochMs", 0))
            .put("finishedWeightGrams", recipe.opt("finishedWeightGrams") ?: JSONObject.NULL)
            .put("ingredients", ingredients))
    }
    val decisions = conflicts.objects().associateBy(::recordKey)
    val changes = JSONArray()
    (local.keys + previous.keys).forEach { key ->
        val current = local[key]
        val old = previous[key]
        val deleted = current == null
        val changed = if (deleted) old != null && !old.optBoolean("deleted")
            else old == null || old.optBoolean("deleted") || canonicalJson(current!!.get("data")) != canonicalJson(old.get("data"))
        if (!changed) return@forEach
        val conflict = decisions[key]
        if (conflict != null && choice == ConflictChoice.SERVER) return@forEach
        val base = if (conflict != null && choice == ConflictChoice.LOCAL) conflict.getLong("revision") else old?.getLong("revision") ?: 0
        if (deleted && base == 0L) return@forEach
        val identity = current ?: old!!
        changes.put(JSONObject().put("kind", identity.getString("kind")).put("id", identity.getString("id"))
            .put("baseRevision", base).put("deleted", deleted)
            .put("data", current?.get("data") ?: JSONObject.NULL))
    }
    return CatalogPlan(changes)
}

internal data class CatalogStorage(val foods: JSONArray, val recipes: JSONArray)

internal fun catalogToStorage(catalog: JSONObject): CatalogStorage {
    require(catalog.getInt("schemaVersion") == 1) { "Unsupported catalogue version" }
    require(catalog.getString("serverId").isNotBlank()) { "Missing server identity" }
    val foods = JSONArray()
    val recipes = JSONArray()
    val seen = mutableSetOf<String>()
    catalog.getJSONArray("records").objects().forEach { record ->
        val key = recordKey(record)
        require(seen.add(key) && record.getString("id").isNotBlank() && record.getLong("revision") > 0) { "Invalid catalogue entry" }
        val kind = record.getString("kind")
        require(kind == "food" || kind == "recipe") { "Unknown catalogue entry type" }
        if (record.getBoolean("deleted")) return@forEach
        val data = JSONObject(record.getJSONObject("data").toString()).put("id", record.getString("id"))
        fun checkNutrients(obj: JSONObject) {
            catalogNutrients.forEach { field ->
                val value = obj.getDouble(field)
                require(value.isFinite() && value >= 0) { "Invalid nutrient: $field" }
            }
            if (!obj.isNull("cholesterolMgPer100g")) require(obj.storedCholesterol() != null) { "Invalid cholesterol" }
        }
        if (kind == "food") {
            require(data.getString("description").isNotBlank())
            checkNutrients(data)
            foods.put(data.put("source", "MANUAL"))
        } else {
            require(data.getString("name").isNotBlank())
            val ingredients = data.getJSONArray("ingredients")
            require(ingredients.length() > 0)
            ingredients.objects().forEach {
                checkNutrients(it)
                require(it.getString("foodDescription").isNotBlank())
                val grams = it.getDouble("gramsUsed")
                require(grams.isFinite() && grams > 0)
            }
            if (!data.isNull("finishedWeightGrams")) {
                val weight = data.getDouble("finishedWeightGrams")
                require(weight.isFinite() && weight > 0)
            }
            recipes.put(data)
        }
    }
    return CatalogStorage(foods, recipes)
}

internal fun normalizeCatalogUrl(raw: String): URL {
    val url = URL(raw.trim().trimEnd('/'))
    require(url.protocol == "http" || url.protocol == "https") { "Use an http:// or https:// server address" }
    require(url.userInfo == null && url.query == null && url.ref == null && (url.path.isEmpty() || url.path == "/")) {
        "Use only the server address and port, without a path"
    }
    require(url.host.isNotBlank()) { "Enter the server address" }
    return url
}

internal class CatalogSyncClient(private val prefs: SharedPreferences) {
    fun sync(address: String, key: String, conflicts: JSONArray, choice: ConflictChoice?): CatalogSyncResult {
        val baseUrl = normalizeCatalogUrl(address)
        require(key.trim().isNotEmpty()) { "Enter the server access key" }
        val addresses = InetAddress.getAllByName(baseUrl.host)
        require(addresses.isNotEmpty() && addresses.all { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress ||
            (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc) }) { "Use a server on your local network" }
        val deviceId = prefs.getString("catalog_device_id", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("catalog_device_id", it).commit()) { "Unable to save device identity" }
        }
        val foodRaw = prefs.getString("manual_foods_json", null)
        val recipeRaw = prefs.getString("recipes_json", null)
        val baseline = JSONArray(prefs.getString("catalog_baseline_json", "[]"))
        val plan = planCatalogSync(JSONArray(foodRaw ?: "[]"), JSONArray(recipeRaw ?: "[]"), baseline, deviceId, conflicts, choice)
        val serverId = prefs.getString("catalog_server_id", "") ?: ""
        val payload = JSONObject().put("schemaVersion", 1).put("serverId", serverId).put("changes", plan.changes)
        val conn = (URL(baseUrl.toString().trimEnd('/') + "/api/v1/sync").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 10000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${key.trim()}")
        }
        val catalog = try {
            val body = payload.toString().toByteArray(Charsets.UTF_8)
            require(body.size <= 16 * 1024 * 1024) { "Catalogue upload is too large (16 MB limit)" }
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    require(buffer.size() + count <= 32 * 1024 * 1024) { "Server response is too large" }
                    buffer.write(chunk, 0, count)
                }
                buffer.toString("UTF-8")
            } ?: throw IllegalStateException("Server returned HTTP $code")
            val result = JSONObject(raw)
            if (code == 409 && result.has("conflicts")) throw CatalogConflict(result.getJSONArray("conflicts"))
            check(code == 200) { result.optString("error", "Server returned HTTP $code").take(500) }
            check(serverId.isEmpty() || result.getString("serverId") == serverId) { "Server identity changed" }
            result
        } finally {
            conn.disconnect()
        }
        val storage = catalogToStorage(catalog)
        // The repository holds its catalogue mutex throughout sync. Keep this guard as
        // protection against future writers bypassing that lock.
        check(foodRaw == prefs.getString("manual_foods_json", null) && recipeRaw == prefs.getString("recipes_json", null)) {
            "Foods or recipes changed during sync. Please sync again."
        }
        val knownIds = baseline.objects().map(::recordKey).toSet()
        val foodIdMap = JSONArray(foodRaw ?: "[]").objects().associate { item ->
            val id = item.getString("id")
            id to if (id.startsWith("catalog-") || "food:$id" in knownIds) id else "catalog-" + UUID.nameUUIDFromBytes("$deviceId/food/$id".toByteArray(Charsets.UTF_8))
        }
        val recipeIdMap = JSONArray(recipeRaw ?: "[]").objects().associate { item ->
            val id = item.getString("id")
            id to if (id.startsWith("catalog-") || "recipe:$id" in knownIds) id else "catalog-" + UUID.nameUUIDFromBytes("$deviceId/recipe/$id".toByteArray(Charsets.UTF_8))
        }
        val currentFoods = storage.foods.objects().associateBy { it.getString("id") }
        val currentRecipeIds = storage.recipes.objects().map { it.getString("id") }.toSet()
        fun refreshHistory(raw: String?): String {
            val items = JSONArray()
            JSONArray(raw ?: "[]").objects().forEach { old ->
                val source = old.optString("source")
                val oldId = old.optString("id")
                when (source) {
                    "MANUAL" -> {
                        val id = foodIdMap[oldId] ?: oldId
                        val fresh = currentFoods[id]
                        if (fresh != null) items.put(JSONObject(fresh.toString()).put("lastUsedGrams", old.opt("lastUsedGrams")))
                    }
                    "RECIPE" -> {
                        val id = recipeIdMap[oldId] ?: oldId
                        // Recipe nutrition is resolved from current ingredients on use.
                        if (id in currentRecipeIds) items.put(JSONObject(old.toString()).put("id", id))
                    }
                    else -> items.put(old)
                }
            }
            return items.toString()
        }
        val recent = refreshHistory(prefs.getString("recent_foods_json", null))
        val cached = refreshHistory(prefs.getString("foods_cache_json", null))
        check(prefs.edit()
            .putString("manual_foods_json", storage.foods.toString())
            .putString("recipes_json", storage.recipes.toString())
            .putString("recent_foods_json", recent)
            .putString("foods_cache_json", cached)
            .putString("catalog_baseline_json", catalog.getJSONArray("records").toString())
            .putString("catalog_server_id", catalog.getString("serverId"))
            .putLong("catalog_last_sync", System.currentTimeMillis())
            .commit()) { "Unable to save downloaded catalogue. Please sync again." }
        return CatalogSyncResult(storage.foods.length(), storage.recipes.length())
    }
}
