package com.example.carbomon

import org.json.JSONObject

internal fun scannedFoodId(barcode: String): String? {
    val digits = barcode.trim().replace(" ", "").replace("-", "")
    if (!digits.matches(Regex("[0-9]{8}|[0-9]{12,14}"))) return null
    // Zero-padding makes UPC-A and its leading-zero EAN/GTIN representation agree.
    return "catalog-barcode-" + digits.padStart(14, '0')
}

internal fun isScannedFood(food: FoodItem): Boolean = food.id.startsWith("catalog-barcode-")

internal fun findSavedScannedFood(foods: List<FoodItem>, barcode: String): FoodItem? {
    val id = scannedFoodId(barcode) ?: return null
    return foods.firstOrNull { it.id == id }
}

internal fun parseScannedFood(barcode: String, root: JSONObject): FoodItem {
    val id = scannedFoodId(barcode) ?: error("Enter a valid product barcode")
    check(root.optInt("status") == 1) { "Barcode not found" }
    val product = root.optJSONObject("product") ?: error("Missing product details")
    val description = product.optString("product_name", "").ifBlank { product.optString("generic_name", "") }
    check(description.isNotBlank() && description.length <= 500) { "Product has no usable name" }
    val nutrients = product.optJSONObject("nutriments") ?: error("Product has no nutrition data")
    fun number(key: String): Double? {
        val raw = nutrients.opt(key)
        if (raw == null || raw == JSONObject.NULL || raw.toString().isBlank()) return null
        val value = raw.toString().replace(',', '.').toDoubleOrNull()
        check(value != null && value.isFinite() && value >= 0) { "Invalid nutrition value: $key" }
        return value
    }
    fun bounded(value: Double?, max: Double, name: String, required: Boolean = false): Double {
        check(!required || value != null) { "Product is missing per-100-g $name" }
        val result = value ?: 0.0
        check(result <= max) { "Invalid per-100-g $name" }
        return result
    }
    val calories = number("energy-kcal_100g") ?: (number("energy-kj_100g") ?: number("energy_100g"))?.div(4.184)
    val sodium = number("sodium_100g")?.times(1000.0) ?: number("salt_100g")?.times(400.0)
    val cholesterol = number("cholesterol_100g")?.times(1000.0)
    if (cholesterol != null) bounded(cholesterol, 100000.0, "cholesterol")
    return FoodItem(
        id = id, source = FoodSource.MANUAL, description = description,
        brand = product.optString("brands", "").take(500),
        caloriesPer100g = bounded(calories, 1000.0, "energy", true),
        proteinPer100g = bounded(number("proteins_100g"), 100.0, "protein", true),
        carbsPer100g = bounded(number("carbohydrates_100g"), 100.0, "carbohydrate", true),
        fatPer100g = bounded(number("fat_100g"), 100.0, "fat", true),
        fiberPer100g = bounded(number("fiber_100g"), 100.0, "fibre"),
        sugarPer100g = bounded(number("sugars_100g"), 100.0, "sugar"),
        sodiumMgPer100g = bounded(sodium, 100000.0, "sodium"),
        potassiumMgPer100g = bounded(number("potassium_100g")?.times(1000.0), 100000.0, "potassium"),
        calciumMgPer100g = bounded(number("calcium_100g")?.times(1000.0), 100000.0, "calcium"),
        ironMgPer100g = bounded(number("iron_100g")?.times(1000.0), 100000.0, "iron"),
        cholesterolMgPer100g = cholesterol,
        nutritionSource = "Open Food Facts — https://world.openfoodfacts.org/product/${id.removePrefix("catalog-barcode-")} (ODbL)"
    )
}
