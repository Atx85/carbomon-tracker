package com.example.carbomon

import org.junit.Assert.*
import org.junit.Test

class FoodSearchTest {
    private val pork = FoodItem(
        id = "my-pork", source = FoodSource.MANUAL,
        description = "Pork mince, 15% fat, raw", brand = "Lidl UK Birchwood",
        caloriesPer100g = 200.0, proteinPer100g = 18.0,
        carbsPer100g = 0.0, fatPer100g = 15.0
    ) // Synthetic test fixture, not product nutrition data.

    @Test
    fun savedBrandAndNameMatchAcrossWordsInAnyOrder() {
        assertEquals(listOf(pork), searchLocalFoods("Lidle minced pork 15%", listOf(pork)))
        assertEquals(listOf(pork), searchLocalFoods("pork lidl meat mince 15 percent", listOf(pork)))
    }

    @Test
    fun fatPercentagesDoNotCrossMatch() {
        val lean = pork.copy(id = "lean", description = "Pork mince, 5% fat, raw", fatPer100g = 5.0)
        assertEquals(listOf(lean), searchLocalFoods("pork 5%", listOf(pork, lean)))
        assertEquals(listOf(pork), searchLocalFoods("pork 15%", listOf(pork, lean)))
    }

    @Test
    fun savedProductsComeBeforeGenericAndApiResultsWithoutDuplicates() {
        val generic = pork.copy(id = "generic", source = FoodSource.GENERIC, brand = "")
        val remote = pork.copy(id = "remote", source = FoodSource.USDA)
        assertEquals(listOf(pork, generic, remote), searchLocalFoods("pork", listOf(remote, generic, pork, pork)))
    }

    @Test
    fun staplesAreAvailableWithoutAnyCachedOrSavedFoods() {
        for (query in listOf("lard", "chicken breast", "beef mince 15%", "pork mince 4%", "pork mince 16%", "pasta dry", "oats")) {
            assertTrue("No staple matches $query", searchLocalFoods(query, stapleFoods).isNotEmpty())
        }
        val beef = searchLocalFoods("beef mince 15%", stapleFoods).single()
        assertEquals(15.0, beef.fatPer100g, 0.0)
        assertEquals(215.0, beef.caloriesPer100g, 0.0)
    }

    @Test
    fun genericFoodsAreNeverPresentedAsLidlProducts() {
        assertTrue(searchLocalFoods("Lidl pork", stapleFoods).isEmpty())
        assertTrue(stapleFoods.all { it.source == FoodSource.GENERIC })
    }

    @Test
    fun rawAndCookedPastaStayDistinct() {
        val raw = searchLocalFoods("pasta raw", stapleFoods)
        val cooked = searchLocalFoods("pasta boiled", stapleFoods)
        assertTrue(raw.isNotEmpty() && cooked.isNotEmpty())
        assertTrue(raw.none { food -> cooked.any { it.id == food.id } })
        assertTrue(raw.all { it.caloriesPer100g > 300.0 })
        assertTrue(cooked.all { it.caloriesPer100g < 200.0 })
    }

    @Test
    fun emptyAndUnrelatedQueriesDoNotReturnOldResults() {
        assertTrue(searchLocalFoods(" ", stapleFoods).isEmpty())
        assertTrue(searchLocalFoods("xyz-nonexistent", stapleFoods).isEmpty())
        assertEquals(2, searchLocalFoods("pasta", stapleFoods, limit = 2).size)
    }
}
