package com.example.carbomon

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ScannedFoodsTest {
    private fun response(): JSONObject = JSONObject().put("status", 1).put("product", JSONObject()
        .put("product_name", "Test cereal").put("brands", "Test brand")
        .put("nutriments", JSONObject().put("energy-kcal_100g", 350).put("proteins_100g", 10)
            .put("carbohydrates_100g", 60).put("fat_100g", 8).put("sodium_100g", 0.12)
            .put("calcium_100g", 0.08).put("iron_100g", 0.003).put("cholesterol_100g", 0.02)))

    @Test fun equivalentBarcodesHaveTheSameIdentityAcrossPhones() {
        assertEquals(scannedFoodId("123456789012"), scannedFoodId("0123456789012"))
        assertEquals(scannedFoodId("0123456789012"), scannedFoodId("00123456789012"))
        assertNotEquals(scannedFoodId("00123456789012"), scannedFoodId("10123456789012"))
        assertNull(scannedFoodId("https://example.test/12345678"))
        assertNull(scannedFoodId("123456789"))
        assertNotNull(scannedFoodId("12345678"))
    }

    @Test fun successfulLookupBecomesASharedOfflineFoodWithCorrectUnits() {
        val saved = parseScannedFood("0123456789012", response())
        assertEquals(FoodSource.MANUAL, saved.source)
        assertEquals("catalog-barcode-00123456789012", saved.id)
        assertEquals(120.0, saved.sodiumMgPer100g, 0.001)
        assertEquals(80.0, saved.calciumMgPer100g, 0.001)
        assertEquals(3.0, saved.ironMgPer100g, 0.001)
        assertEquals(20.0, saved.cholesterolMgPer100g!!, 0.001)
        assertTrue(saved.nutritionSource.contains("Open Food Facts"))
        assertSame(saved, findSavedScannedFood(listOf(saved), "123456789012"))
        assertNull(findSavedScannedFood(listOf(saved), "99999999"))
    }

    @Test fun missingCoreNutritionIsNotSavedAsZeros() {
        val missing = response().also { it.getJSONObject("product").getJSONObject("nutriments").remove("proteins_100g") }
        try { parseScannedFood("0123456789012", missing); fail("Missing protein accepted") } catch (_: IllegalStateException) { }
        val invalid = response().also { it.getJSONObject("product").getJSONObject("nutriments").put("fat_100g", -1) }
        try { parseScannedFood("0123456789012", invalid); fail("Negative fat accepted") } catch (_: IllegalStateException) { }
        try { parseScannedFood("0123456789012", JSONObject().put("status", 0)); fail("Unknown product saved") } catch (_: IllegalStateException) { }
    }

    @Test fun kilojoulesSaltAndUnknownCholesterolAreHandled() {
        val json = response()
        json.getJSONObject("product").getJSONObject("nutriments").apply {
            remove("energy-kcal_100g"); put("energy_100g", 418.4)
            remove("sodium_100g"); put("salt_100g", 1)
            remove("cholesterol_100g")
        }
        val food = parseScannedFood("0123456789012", json)
        assertEquals(100.0, food.caloriesPer100g, 0.001)
        assertEquals(400.0, food.sodiumMgPer100g, 0.001)
        assertNull(food.cholesterolMgPer100g)
    }

    @Test fun scannedProductKeepsBarcodeIdentityThroughSyncRoundTrip() {
        val id = scannedFoodId("0123456789012")!!
        val json = JSONObject().put("id", id).put("source", "MANUAL").put("description", "Test cereal")
            .put("nutritionSource", "Open Food Facts").put("caloriesPer100g", 350).put("proteinPer100g", 10)
            .put("carbsPer100g", 60).put("fatPer100g", 8)
        val first = planCatalogSync(JSONArray().put(json), JSONArray(), JSONArray(), "phone-a")
        val second = planCatalogSync(JSONArray().put(json), JSONArray(), JSONArray(), "phone-b")
        assertEquals(canonicalJson(first.changes), canonicalJson(second.changes))
        val record = JSONObject(first.changes.getJSONObject(0).toString()).put("revision", 1)
        record.remove("baseRevision")
        val records = JSONArray().put(record)
        val storage = catalogToStorage(JSONObject().put("schemaVersion", 1).put("serverId", "server").put("records", records))
        assertEquals(id, storage.foods.getJSONObject(0).getString("id"))
        assertEquals(0, planCatalogSync(storage.foods, storage.recipes, records, "phone-b").changes.length())
    }
}
