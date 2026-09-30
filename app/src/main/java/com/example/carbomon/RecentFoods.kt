package com.example.carbomon

internal fun formatGrams(grams: Double): String =
    java.math.BigDecimal.valueOf(grams).stripTrailingZeros().toPlainString()

internal fun recentFoodsWithAmounts(
    recentFoods: List<FoodItem>,
    entries: List<ConsumedFoodEntry>
): List<FoodItem> {
    // Entries are appended in usage order, even when logging a different diary date.
    val latestEntries = entries.asReversed().distinctBy { it.source to it.foodId }
    val latestAmounts = latestEntries.associate { (it.source to it.foodId) to it.gramsConsumed }
    val foods = recentFoods.ifEmpty { latestEntries.map { it.asFoodItem() } }
    return foods.take(100).map { food ->
        val grams = food.lastUsedGrams?.takeIf { it.isFinite() && it > 0.0 }
            ?: latestAmounts[food.source to food.id]?.takeIf { it.isFinite() && it > 0.0 }
        food.copy(lastUsedGrams = grams)
    }
}
