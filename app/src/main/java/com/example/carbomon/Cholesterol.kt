package com.example.carbomon

import org.json.JSONObject

internal fun parseCholesterolMg(value: Any?): Double? = when (value) {
    is Number -> value.toDouble()
    is String -> value.trim().replace(',', '.').toDoubleOrNull()
    else -> null
}?.takeIf { it.isFinite() && it >= 0.0 }

internal fun JSONObject.storedCholesterol(): Double? = parseCholesterolMg(opt("cholesterolMgPer100g"))

internal fun JSONObject.putCholesterol(value: Double?): JSONObject =
    put("cholesterolMgPer100g", parseCholesterolMg(value) ?: JSONObject.NULL)

internal fun cholesterolForGrams(per100g: Double?, grams: Double): Double? =
    parseCholesterolMg(per100g)?.let { it * grams / 100.0 }
        ?.takeIf { grams.isFinite() && grams >= 0.0 && it.isFinite() }

internal data class CholesterolTotal(val knownMg: Double, val missingCount: Int, val knownCount: Int)

internal fun cholesterolTotal(portions: List<Pair<Double?, Double>>): CholesterolTotal {
    val amounts = portions.filter { it.second > 0.0 }.map { cholesterolForGrams(it.first, it.second) }
    return CholesterolTotal(amounts.filterNotNull().sum(), amounts.count { it == null }, amounts.count { it != null })
}

internal fun recipeCholesterolPer100g(portions: List<Pair<Double?, Double>>, finishedWeightGrams: Double? = null): Double? {
    val grams = finishedWeightGrams ?: portions.sumOf { it.second }
    if (!grams.isFinite() || grams <= 0.0 || portions.any { it.second < 0.0 }) return null
    val total = cholesterolTotal(portions)
    return if (total.missingCount == 0) total.knownMg * 100.0 / grams else null
}

// OFF normalizes *_100g nutrients to grams, regardless of the original label unit.
internal fun openFoodFactsCholesterol(nutriments: JSONObject): Double? =
    parseCholesterolMg(nutriments.opt("cholesterol_100g"))?.times(1000.0)

internal fun fatSecretCholesterol(serving: JSONObject): Double? {
    val grams = parseCholesterolMg(serving.opt("metric_serving_amount")) ?: return null
    if (grams <= 0.0 || !serving.optString("metric_serving_unit").equals("g", true)) return null
    return parseCholesterolMg(serving.opt("cholesterol"))?.times(100.0 / grams)
}

internal fun usdaCholesterol(food: JSONObject): Double? {
    val nutrients = food.optJSONArray("foodNutrients")
    if (nutrients != null) {
        for (i in 0 until nutrients.length()) {
            val item = nutrients.optJSONObject(i) ?: continue
            val nested = item.optJSONObject("nutrient")
            val number = item.optString("nutrientNumber", nested?.optString("number") ?: "")
            val id = item.optInt("nutrientId", nested?.optInt("id", -1) ?: -1)
            val name = item.optString("nutrientName", nested?.optString("name") ?: "")
            if (number != "601" && id != 1253 && !name.equals("Cholesterol", true)) continue
            val value = parseCholesterolMg(item.opt("value")) ?: parseCholesterolMg(item.opt("amount")) ?: continue
            val unit = item.optString("unitName", nested?.optString("unitName") ?: "mg")
            return when (unit.lowercase(java.util.Locale.ROOT)) {
                "mg" -> value
                "g" -> value * 1000.0
                "µg", "ug", "mcg" -> value / 1000.0
                else -> null
            }
        }
    }
    val label = food.optJSONObject("labelNutrients")?.optJSONObject("cholesterol") ?: return null
    val grams = parseCholesterolMg(food.opt("servingSize")) ?: return null
    if (grams <= 0.0 || !food.optString("servingSizeUnit").equals("g", true)) return null
    return parseCholesterolMg(label.opt("value"))?.times(100.0 / grams)
}
