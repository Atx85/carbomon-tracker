package com.example.carbomon

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CatalogJsonImportTest {
    private fun resource(folder: String, kind: String): String = File(System.getProperty("carbomon.catalogRoot"), "$folder/$kind.json").readText()
    private fun schema(kind: String) = JSONObject(resource("schemas", kind))
    private fun prepare(raw: String, kind: String = "food") = prepareCatalogImport(raw, kind, schema(kind))
    private fun food() = JSONObject(resource("examples", "food"))
    private fun recipe() = JSONObject(resource("examples", "recipe"))
    private fun rejected(raw: String, kind: String = "food") {
        try { prepare(raw, kind); fail("Invalid import was accepted") } catch (_: Exception) { }
    }

    @Test fun foodAndRecipeUseServerExamplesAndWorkOffline() {
        val draft = prepare(food().toString())
        val storage = appendCatalogImport(JSONArray(), JSONArray(), draft)
        assertEquals(1, storage.foods.length())
        val saved = storage.foods.getJSONObject(0)
        assertEquals("MANUAL", saved.getString("source"))
        assertTrue(saved.getString("id").startsWith("catalog-"))
        assertTrue(saved.isNull("cholesterolMgPer100g"))
        assertEquals(370.0, saved.getDouble("caloriesPer100g"), 0.0)
        val dish = prepare(recipe().toString(), "recipe")
        val combined = appendCatalogImport(storage.foods, storage.recipes, dish)
        assertEquals(1, combined.foods.length())
        assertEquals(1, combined.recipes.length())
        assertEquals(250.0, combined.recipes.getJSONObject(0).getDouble("finishedWeightGrams"), 0.0)
        assertEquals("", combined.recipes.getJSONObject(0).getJSONArray("ingredients").getJSONObject(0).getString("foodId"))
        val plan = planCatalogSync(combined.foods, combined.recipes, JSONArray(), "offline-phone")
        assertEquals(2, plan.changes.length())
        plan.changes.objects().forEach { record -> validateCatalogJson(record.getJSONObject("data"), schema(record.getString("kind"))) }
    }

    @Test fun listAndMarkdownFenceAreAcceptedWithoutChatText() {
        val draft = prepare("```json\n" + JSONArray().put(food()).put(food().put("description", "Second food")) + "\n```")
        assertEquals(2, appendCatalogImport(JSONArray(), JSONArray(), draft).foods.length())
        rejected(food().toString() + " extra chat")
        rejected("Here is the JSON: " + food())
        rejected("[]")
        rejected("[".repeat(1000) + "0" + "]".repeat(1000))
        rejected(JSONArray().apply { repeat(101) { put(food()) } }.toString())
        rejected(" ".repeat(512_001))
    }

    @Test fun requiredNumbersBoundsUnknownFieldsAndIncompleteListsAreRejected() {
        rejected(food().apply { remove("proteinPer100g") }.toString())
        rejected(food().put("proteinPer100g", "13").toString())
        rejected(food().put("proteinPer100g", -1).toString())
        rejected(food().put("fatPer100g", 101).toString())
        rejected(food().put("caloriesPer100g", 1001).toString())
        rejected(food().put("description", "   ").toString())
        rejected(food().put("cholesterolMgPer100g", -1).toString())
        rejected(food().put("id", "catalog-existing").toString())
        rejected(food().put("source", "MANUAL").toString())
        rejected(JSONArray().put(food()).put(food().apply { remove("fatPer100g") }).toString())
    }

    @Test fun recipeIngredientValidationAndFinishedWeightFollowServerRules() {
        rejected(recipe().put("ingredients", JSONArray()).toString(), "recipe")
        rejected(recipe().apply { getJSONArray("ingredients").getJSONObject(0).put("gramsUsed", 0) }.toString(), "recipe")
        rejected(recipe().apply { getJSONArray("ingredients").getJSONObject(0).put("foodSource", "INVALID") }.toString(), "recipe")
        rejected(recipe().put("finishedWeightGrams", 0).toString(), "recipe")
        rejected(recipe().put("createdAtEpochMs", 1.5).toString(), "recipe")
        val draft = prepare(recipe().apply { remove("finishedWeightGrams") }.toString(), "recipe")
        assertTrue(appendCatalogImport(JSONArray(), JSONArray(), draft).recipes.getJSONObject(0).isNull("finishedWeightGrams"))
    }

    @Test fun savingAPreviewIsIdempotentAndCannotOverwriteExistingItems() {
        val draft = prepare(food().toString())
        val saved = appendCatalogImport(JSONArray(), JSONArray(), draft)
        val retried = appendCatalogImport(saved.foods, saved.recipes, draft)
        assertEquals(1, retried.foods.length())
        val changed = JSONArray(saved.foods.toString())
        changed.getJSONObject(0).put("description", "My later edit")
        try { appendCatalogImport(changed, saved.recipes, draft); fail("Overwrote an existing food") } catch (_: IllegalArgumentException) { }
        assertEquals("My later edit", changed.getJSONObject(0).getString("description"))
        val fresh = prepare(food().toString())
        assertNotEquals(draft.catalogJson, fresh.catalogJson)
    }

    @Test fun promptContainsActualSchemaExamplesAndUnitInstructions() {
        val schemaText = resource("schemas", "recipe")
        val exampleText = resource("examples", "recipe")
        val prompt = catalogAiPrompt("recipe", schemaText, exampleText)
        assertTrue(prompt.contains(schemaText))
        assertTrue(prompt.contains(exampleText))
        assertTrue(prompt.contains("PER 100 GRAMS"))
        assertTrue(prompt.contains("Unknown cholesterol must be null"))
        assertTrue(prompt.contains("measured finished weight"))
    }
}
