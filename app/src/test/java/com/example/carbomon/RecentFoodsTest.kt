package com.example.carbomon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentFoodsTest {
    private val food = FoodItem(
        id = "oats", source = FoodSource.USDA, description = "Oats", brand = "",
        caloriesPer100g = 389.0, proteinPer100g = 16.9,
        carbsPer100g = 66.3, fatPer100g = 6.9
    )

    private fun entry(grams: Double, source: FoodSource = food.source) = ConsumedFoodEntry(
        id = "entry-$grams", date = "2026-09-30", foodId = food.id, source = source,
        description = food.description, caloriesPer100g = food.caloriesPer100g,
        proteinPer100g = food.proteinPer100g, carbsPer100g = food.carbsPer100g,
        fatPer100g = food.fatPer100g, gramsConsumed = grams
    )

    @Test
    fun legacyRecentFoodUsesLatestLoggedAmountEvenForBackdatedEntries() {
        val entries = listOf(entry(100.0), entry(37.5).copy(date = "2026-09-01"))
        val result = recentFoodsWithAmounts(listOf(food), entries).single()
        assertEquals(food.copy(lastUsedGrams = 37.5), result)
        assertEquals("37.5", formatGrams(result.lastUsedGrams!!))
    }

    @Test
    fun savedRecipeAmountTakesPrecedenceOverDiaryAndSurvivesMissingHistory() {
        val recent = food.copy(lastUsedGrams = 62.25)
        assertEquals(recent, recentFoodsWithAmounts(listOf(recent), listOf(entry(100.0))).single())
        assertEquals(recent, recentFoodsWithAmounts(listOf(recent), emptyList()).single())
    }

    @Test
    fun sameIdFromDifferentSourceDoesNotSupplyAnAmount() {
        val result = recentFoodsWithAmounts(listOf(food), listOf(entry(80.0, FoodSource.OPENFOODFACTS)))
        assertNull(result.single().lastUsedGrams)
    }

    @Test
    fun emptyRecentListFallsBackToLatestUniqueDiaryFoodsWithAmountsAndNutrients() {
        val result = recentFoodsWithAmounts(emptyList(), listOf(entry(100.0), entry(12.75)))
        assertEquals(listOf(food.copy(lastUsedGrams = 12.75)), result)
    }

    @Test
    fun invalidAmountsAreIgnoredAndValidDiaryAmountCanRecoverThem() {
        for (grams in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val recent = food.copy(lastUsedGrams = grams)
            assertNull(recentFoodsWithAmounts(listOf(recent), listOf(entry(grams))).single().lastUsedGrams)
            assertEquals(
                food.copy(lastUsedGrams = 42.5),
                recentFoodsWithAmounts(listOf(recent), listOf(entry(42.5))).single()
            )
        }
    }

    @Test
    fun recentOrderAndLimitArePreserved() {
        val foods = (1..105).map { food.copy(id = "$it", lastUsedGrams = it.toDouble()) }
        assertEquals(foods.take(100), recentFoodsWithAmounts(foods, emptyList()))
    }

    @Test
    fun gramsFormattingPreservesFractionsWithoutAddingDecimalToWholeAmounts() {
        assertEquals("100", formatGrams(100.0))
        assertEquals("0.125", formatGrams(0.125))
    }
}
