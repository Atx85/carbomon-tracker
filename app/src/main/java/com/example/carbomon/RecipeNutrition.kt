package com.example.carbomon

// Ingredient nutrition is a saved snapshot, independent of mutable search caches.
internal fun recipeToFood(recipe: Recipe): FoodItem {
    val totalGramsInRecipe = recipe.ingredients.sumOf { it.gramsUsed }
    if (totalGramsInRecipe == 0.0) {
        return FoodItem(
            id = recipe.id,
            source = FoodSource.RECIPE,
            description = recipe.name,
            brand = "",
            caloriesPer100g = 0.0,
            proteinPer100g = 0.0,
            carbsPer100g = 0.0,
            fatPer100g = 0.0
        )
    }

    var totalCalories = 0.0
    var totalProtein = 0.0
    var totalCarbs = 0.0
    var totalFat = 0.0
    var totalFiber = 0.0
    var totalSugar = 0.0
    var totalSodiumMg = 0.0
    var totalPotassiumMg = 0.0
    var totalCalciumMg = 0.0
    var totalIronMg = 0.0
    val cholesterolPortions = mutableListOf<Pair<Double?, Double>>()

    for (ingredient in recipe.ingredients) {
        val multiplier = ingredient.gramsUsed / 100.0
        totalCalories += ingredient.caloriesPer100g * multiplier
        totalProtein += ingredient.proteinPer100g * multiplier
        totalCarbs += ingredient.carbsPer100g * multiplier
        totalFat += ingredient.fatPer100g * multiplier
        totalFiber += ingredient.fiberPer100g * multiplier
        totalSugar += ingredient.sugarPer100g * multiplier
        totalSodiumMg += ingredient.sodiumMgPer100g * multiplier
        totalPotassiumMg += ingredient.potassiumMgPer100g * multiplier
        totalCalciumMg += ingredient.calciumMgPer100g * multiplier
        totalIronMg += ingredient.ironMgPer100g * multiplier
        cholesterolPortions += ingredient.cholesterolMgPer100g to ingredient.gramsUsed
    }

    // Convert totals to per 100g
    val divisor = (recipe.finishedWeightGrams ?: totalGramsInRecipe) / 100.0
    return FoodItem(
        id = recipe.id,
        source = FoodSource.RECIPE,
        description = recipe.name,
        brand = "",
        caloriesPer100g = totalCalories / divisor,
        proteinPer100g = totalProtein / divisor,
        carbsPer100g = totalCarbs / divisor,
        fatPer100g = totalFat / divisor,
        fiberPer100g = totalFiber / divisor,
        sugarPer100g = totalSugar / divisor,
        sodiumMgPer100g = totalSodiumMg / divisor,
        potassiumMgPer100g = totalPotassiumMg / divisor,
        calciumMgPer100g = totalCalciumMg / divisor,
        ironMgPer100g = totalIronMg / divisor,
        cholesterolMgPer100g = recipeCholesterolPer100g(cholesterolPortions, recipe.finishedWeightGrams)
    )
}
