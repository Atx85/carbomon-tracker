package com.example.carbomon

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CholesterolTest {
    @Test
    fun staplesIncludeSourcedCholesterolForAnimalAndPlantFoods() {
        assertTrue(stapleFoods.all { it.cholesterolMgPer100g != null })
        val egg = stapleFoods.single { it.id == "cofid-12-937" }
        val banana = stapleFoods.single { it.id == "cofid-14-318" }
        assertEquals(175.0, cholesterolForGrams(egg.cholesterolMgPer100g, 50.0)!!, 0.0)
        assertEquals(0.0, banana.cholesterolMgPer100g!!, 0.0)
    }

    @Test
    fun storageRoundTripDistinguishesUnknownFromMeasuredZeroAndSupportsOldBackups() {
        assertNull(JSONObject("{}").storedCholesterol())
        for (value in listOf(null, 0.0, 213.2)) {
            val restored = JSONObject(JSONObject().putCholesterol(value).toString()).storedCholesterol()
            assertEquals(value, restored)
        }
        assertNull(JSONObject("""{"cholesterolMgPer100g":-1}""").storedCholesterol())
    }

    @Test
    fun manualInputAllowsDecimalCommaAndZeroButRejectsInvalidValues() {
        assertEquals(12.5, parseCholesterolMg("12,5")!!, 0.0001)
        assertEquals(0.0, parseCholesterolMg("0")!!, 0.0)
        for (input in listOf(null, "", "unknown", "-1", "NaN", "Infinity", "5 mg", Double.NaN)) {
            assertNull("Unexpected accepted input: $input", parseCholesterolMg(input))
        }
    }

    @Test
    fun portionsAndTotalsKeepMissingDataVisible() {
        assertEquals(175.0, cholesterolForGrams(350.0, 50.0)!!, 0.0001)
        val total = cholesterolTotal(listOf(350.0 to 50.0, 0.0 to 100.0, null to 20.0))
        assertEquals(175.0, total.knownMg, 0.0001)
        assertEquals(1, total.missingCount)
        assertEquals(2, total.knownCount)
        assertEquals(CholesterolTotal(0.0, 0, 0), cholesterolTotal(emptyList()))
        assertEquals(CholesterolTotal(0.0, 1, 0), cholesterolTotal(listOf(null to 100.0)))
    }

    @Test
    fun recipesNormalizeToWeightAndDoNotPretendPartialTotalsAreComplete() {
        assertEquals(87.5, recipeCholesterolPer100g(listOf(350.0 to 50.0, 0.0 to 150.0))!!, 0.0001)
        assertNull(recipeCholesterolPer100g(listOf(350.0 to 50.0, null to 150.0)))
        assertNull(recipeCholesterolPer100g(emptyList()))
        assertNull(recipeCholesterolPer100g(listOf(0.0 to 0.0)))
    }

    @Test
    fun openFoodFactsConvertsNormalizedGramsRegardlessOfLabelUnit() {
        assertEquals(125.0, openFoodFactsCholesterol(JSONObject("""{"cholesterol_100g":0.125,"cholesterol_unit":"mg","cholesterol_value":125}"""))!!, 0.0001)
        assertEquals(0.0, openFoodFactsCholesterol(JSONObject("""{"cholesterol_100g":0}"""))!!, 0.0)
        assertNull(openFoodFactsCholesterol(JSONObject("""{"cholesterol_serving":0.1}""")))
    }

    @Test
    fun fatSecretUsesServingWeightAndRejectsVolumeOrMissingWeights() {
        assertEquals(80.0, fatSecretCholesterol(JSONObject("""{"cholesterol":"20","metric_serving_amount":"25","metric_serving_unit":"g"}"""))!!, 0.0001)
        for (json in listOf("{}", """{"cholesterol":20,"metric_serving_amount":25,"metric_serving_unit":"ml"}""", """{"metric_serving_amount":100,"metric_serving_unit":"g"}""")) {
            assertNull(fatSecretCholesterol(JSONObject(json)))
        }
    }

    @Test
    fun usdaSupportsSearchDetailAndLabelShapesWithCorrectUnits() {
        val samples = listOf(
            """{"foodNutrients":[{"nutrientNumber":"601","value":60,"unitName":"MG"}]}""",
            """{"foodNutrients":[{"nutrient":{"id":1253,"number":"601","unitName":"mg"},"amount":60}]}""",
            """{"foodNutrients":[{"nutrientId":1253,"value":0.06,"unitName":"g"}]}""",
            """{"labelNutrients":{"cholesterol":{"value":15}},"servingSize":25,"servingSizeUnit":"g"}"""
        )
        samples.forEach { assertEquals(60.0, usdaCholesterol(JSONObject(it))!!, 0.0001) }
        assertEquals(0.0, usdaCholesterol(JSONObject("""{"foodNutrients":[{"nutrientNumber":"601","value":0,"unitName":"MG"}]}"""))!!, 0.0)
        assertNull(usdaCholesterol(JSONObject("{}")))
        assertNull(usdaCholesterol(JSONObject("""{"labelNutrients":{"cholesterol":{"value":15}},"servingSize":25,"servingSizeUnit":"ml"}""")))
    }

    @Test
    fun diaryCopiesAndRecentFoodConversionPreserveCholesterol() {
        val entry = ConsumedFoodEntry(
            id = "egg", date = "2026-10-02", foodId = "cofid-12-937", source = FoodSource.GENERIC,
            description = "Egg", caloriesPer100g = 131.0, proteinPer100g = 12.6,
            carbsPer100g = 0.0, fatPer100g = 9.0, gramsConsumed = 50.0, cholesterolMgPer100g = 350.0
        )
        assertEquals(350.0, entry.asFoodItem().cholesterolMgPer100g!!, 0.0)
        assertEquals(262.5, cholesterolForGrams(entry.copy(gramsConsumed = 75.0).cholesterolMgPer100g, 75.0)!!, 0.0)
    }
}
