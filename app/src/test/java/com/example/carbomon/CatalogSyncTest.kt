package com.example.carbomon

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.lang.reflect.Proxy

class CatalogSyncTest {
    private fun food(id: String = "manual-old") = JSONObject()
        .put("id", id).put("source", "MANUAL").put("description", "Test oats").put("brand", "Test brand")
        .put("caloriesPer100g", 370).put("proteinPer100g", 13).put("carbsPer100g", 60).put("fatPer100g", 7)
        .put("lastUsedGrams", 45)
    private fun recipe() = JSONObject().put("id", "recipe-old").put("name", "Test porridge").put("createdAtEpochMs", 1000)
        .put("finishedWeightGrams", 250).put("ingredients", JSONArray().put(JSONObject()
            .put("foodId", "manual-old").put("foodSource", "MANUAL").put("foodDescription", "Test oats")
            .put("gramsUsed", 50).put("caloriesPer100g", 370).put("proteinPer100g", 13).put("carbsPer100g", 60).put("fatPer100g", 7)))
    private fun accepted(changes: JSONArray) = JSONArray().apply {
        changes.objects().forEachIndexed { i, change -> put(JSONObject(change.toString()).removeBase().put("revision", i + 1)) }
    }
    private fun JSONObject.removeBase() = apply { remove("baseRevision") }
    private fun catalog(records: JSONArray) = JSONObject().put("schemaVersion", 1).put("serverId", "test-server").put("revision", 2).put("records", records)

    @Test fun firstSyncContributesOnlyManualFoodsAndRecipesWithStableIds() {
        val foods = JSONArray().put(food()).put(food("usda").put("source", "USDA"))
        val recipes = JSONArray().put(recipe())
        val plan = planCatalogSync(foods, recipes, JSONArray(), "device-A")
        assertEquals(2, plan.changes.length())
        assertEquals(canonicalJson(plan.changes), canonicalJson(planCatalogSync(foods, recipes, JSONArray(), "device-A").changes))
        val sharedFoodId = plan.changes.getJSONObject(0).getString("id")
        assertEquals(sharedFoodId, plan.changes.getJSONObject(1).getJSONObject("data").getJSONArray("ingredients").getJSONObject(0).getString("foodId"))
        assertFalse(plan.changes.getJSONObject(0).getJSONObject("data").has("lastUsedGrams"))
        assertNotEquals(sharedFoodId, planCatalogSync(foods, recipes, JSONArray(), "device-B").changes.getJSONObject(0).getString("id"))
    }

    @Test fun downloadedCatalogueRoundTripsWithoutPhantomEdits() {
        val first = planCatalogSync(JSONArray().put(food()), JSONArray().put(recipe()), JSONArray(), "device-A")
        val records = accepted(first.changes)
        val storage = catalogToStorage(catalog(records))
        assertEquals(0, planCatalogSync(storage.foods, storage.recipes, records, "device-A").changes.length())
        assertEquals(0, planCatalogSync(storage.foods, storage.recipes, records, "device-B").changes.length())
        assertTrue(storage.foods.getJSONObject(0).isNull("cholesterolMgPer100g"))
        assertEquals(250.0, storage.recipes.getJSONObject(0).getDouble("finishedWeightGrams"), 0.0)
    }

    @Test fun unchangedLocalCopyDoesNotOverwriteRemoteAndDeleteIsExplicit() {
        val records = accepted(planCatalogSync(JSONArray().put(food()), JSONArray().put(recipe()), JSONArray(), "a").changes)
        val storage = catalogToStorage(catalog(records))
        val plan = planCatalogSync(storage.foods, JSONArray(), records, "a")
        assertEquals(1, plan.changes.length())
        assertTrue(plan.changes.getJSONObject(0).getBoolean("deleted"))
        assertEquals(2, plan.changes.getJSONObject(0).getInt("baseRevision"))
        records.getJSONObject(1).put("deleted", true).put("data", JSONObject.NULL)
        assertEquals(0, planCatalogSync(storage.foods, JSONArray(), records, "a").changes.length())
    }

    @Test fun conflictResolutionUsesExactServerRevisionOrDiscardsOnlyConflictingChanges() {
        val records = accepted(planCatalogSync(JSONArray().put(food()), JSONArray().put(recipe()), JSONArray(), "a").changes)
        val storage = catalogToStorage(catalog(records))
        storage.foods.getJSONObject(0).put("description", "Local edit")
        storage.recipes.getJSONObject(0).put("name", "Local recipe edit")
        val conflicts = JSONArray().put(JSONObject(records.getJSONObject(0).toString()).put("revision", 10))
        val local = planCatalogSync(storage.foods, storage.recipes, records, "a", conflicts, ConflictChoice.LOCAL)
        assertEquals(10, local.changes.getJSONObject(0).getInt("baseRevision"))
        val remote = planCatalogSync(storage.foods, storage.recipes, records, "a", conflicts, ConflictChoice.SERVER)
        assertEquals(1, remote.changes.length())
        assertEquals("recipe", remote.changes.getJSONObject(0).getString("kind"))
    }

    @Test fun malformedDownloadCannotReplaceOfflineStorage() {
        val records = accepted(planCatalogSync(JSONArray().put(food()), JSONArray(), JSONArray(), "a").changes)
        records.getJSONObject(0).getJSONObject("data").put("proteinPer100g", -1)
        try { catalogToStorage(catalog(records)); fail("Accepted invalid nutrient") } catch (_: IllegalArgumentException) { }
    }

    @Test fun finishedWeightAdjustsCholesterolAndUnknownStaysUnknown() {
        assertEquals(50.0, recipeCholesterolPer100g(listOf(100.0 to 100.0), 200.0)!!, 0.0)
        assertNull(recipeCholesterolPer100g(listOf(null to 100.0), 200.0))
        assertNull(recipeCholesterolPer100g(listOf(100.0 to 100.0), 0.0))
    }

    @Test fun finishedRecipeWeightAppliesToAllNutrients() {
        val ingredient = RecipeIngredient("oats", FoodSource.MANUAL, "Oats", 50.0,
            caloriesPer100g = 370.0, proteinPer100g = 13.0, carbsPer100g = 60.0, fatPer100g = 7.0,
            sodiumMgPer100g = 5.0, cholesterolMgPer100g = 0.0)
        val recipe = Recipe("test", "Porridge", listOf(ingredient), 0, finishedWeightGrams = 250.0)
        val food = recipeToFood(recipe)
        assertEquals(74.0, food.caloriesPer100g, 0.0001)
        assertEquals(2.6, food.proteinPer100g, 0.0001)
        assertEquals(12.0, food.carbsPer100g, 0.0001)
        assertEquals(1.4, food.fatPer100g, 0.0001)
        assertEquals(1.0, food.sodiumMgPer100g, 0.0001)
        assertEquals(0.0, food.cholesterolMgPer100g!!, 0.0)
        assertEquals(370.0, recipeToFood(recipe.copy(finishedWeightGrams = null)).caloriesPer100g, 0.0)
    }

    @Test fun realServerTwoDeviceSyncConflictDeleteAndFailedConnection() {
        val address = System.getenv("CARBOMON_TEST_SERVER") ?: ""
        assumeTrue("Set CARBOMON_TEST_SERVER to run the real HTTP integration test", address.isNotEmpty())
        val key = System.getenv("CARBOMON_TEST_KEY") ?: error("Missing test key")
        val a = MemoryPrefs(); val b = MemoryPrefs()
        a.values["manual_foods_json"] = JSONArray().put(food()).toString()
        a.values["recipes_json"] = JSONArray().put(recipe()).toString()
        a.values["entries_json"] = "private diary stays here"
        val clientA = CatalogSyncClient(a.prefs); val clientB = CatalogSyncClient(b.prefs)
        assertEquals(1, clientA.sync(address, key, JSONArray(), null).foods)
        assertEquals(1, clientB.sync(address, key, JSONArray(), null).recipes)
        assertEquals("private diary stays here", a.values["entries_json"])
        assertNull(b.values["entries_json"])
        val aRecipes = JSONArray(a.values["recipes_json"].toString())
        val bRecipes = JSONArray(b.values["recipes_json"].toString())
        aRecipes.getJSONObject(0).put("name", "Phone A edit"); a.values["recipes_json"] = aRecipes.toString()
        bRecipes.getJSONObject(0).put("name", "Phone B edit"); b.values["recipes_json"] = bRecipes.toString()
        clientA.sync(address, key, JSONArray(), null)
        val beforeConflict = b.values["recipes_json"]
        val conflict = try { clientB.sync(address, key, JSONArray(), null); error("Expected conflict") } catch (e: CatalogConflict) { e }
        assertEquals(beforeConflict, b.values["recipes_json"])
        clientB.sync(address, key, conflict.records, ConflictChoice.LOCAL)
        clientA.sync(address, key, JSONArray(), null)
        assertEquals("Phone B edit", JSONArray(a.values["recipes_json"].toString()).getJSONObject(0).getString("name"))
        a.values["recipes_json"] = "[]"
        clientA.sync(address, key, JSONArray(), null)
        assertEquals(0, clientB.sync(address, key, JSONArray(), null).recipes)
        val beforeFailure = HashMap(b.values)
        try { clientB.sync(address, "", JSONArray(), null); fail("Expected key prompt") } catch (_: CatalogKeyRequired) { }
        try { clientB.sync(address, "wrong-key", JSONArray(), null); fail("Expected auth error") } catch (_: IllegalStateException) { }
        assertEquals(beforeFailure, b.values)
        assertEquals(0, clientB.sync(address, key, JSONArray(), null).recipes)

        // Both phones scan the same product independently before either syncs.
        val scanned = food(scannedFoodId("0123456789012")!!).put("nutritionSource", "Open Food Facts")
        for (phone in listOf(a, b)) {
            phone.values["manual_foods_json"] = JSONArray(phone.values["manual_foods_json"].toString()).put(scanned).toString()
        }
        assertEquals(2, clientA.sync(address, key, JSONArray(), null).foods)
        assertEquals(2, clientB.sync(address, key, JSONArray(), null).foods)
        assertEquals(2, clientB.sync(address, key, JSONArray(), null).foods)
    }

    @Test fun realServerSyncWithoutAnAccessKey() {
        val address = System.getenv("CARBOMON_TEST_OPEN_SERVER") ?: ""
        assumeTrue("Set CARBOMON_TEST_OPEN_SERVER to a fresh key-free server", address.isNotEmpty())
        val a = MemoryPrefs(); val b = MemoryPrefs()
        a.values["manual_foods_json"] = JSONArray().put(food()).toString()
        val clientA = CatalogSyncClient(a.prefs); val clientB = CatalogSyncClient(b.prefs)
        assertEquals(1, clientA.sync(address, "", JSONArray(), null).foods)
        assertEquals(1, clientB.sync(address, "  ", JSONArray(), null).foods)
        assertEquals(1, clientB.sync(address, "old-saved-key", JSONArray(), null).foods)
        assertEquals(1, clientA.sync(address, "", JSONArray(), null).foods)
    }

    // No Android runtime is needed to exercise the real client: only this interface
    // is substituted; HTTP, JSON, planning, and commits run as in the application.
    private class MemoryPrefs {
        val values = mutableMapOf<String, Any?>()
        val prefs: SharedPreferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString", "getLong", "getInt", "getBoolean", "getFloat" -> values[args[0]] ?: args[1]
                "contains" -> values.containsKey(args[0])
                "getAll" -> HashMap(values)
                "edit" -> editor()
                else -> null
            }
        } as SharedPreferences
        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
                when (method.name) {
                    "commit", "apply" -> { values.putAll(pending); true }
                    "remove" -> { values.remove(args[0]); proxy }
                    "clear" -> { values.clear(); proxy }
                    else -> { pending[args[0] as String] = args[1]; proxy }
                }
            } as SharedPreferences.Editor
        }
    }
}
