package com.example.carbomon

import java.util.Locale

private fun searchWords(text: String): List<String> =
    Regex("[\\p{L}]+|[0-9]+(?:[.,][0-9]+)?")
        .findAll(text.lowercase(Locale.ROOT))
        .map { match ->
            when (val word = match.value.replace(',', '.')) {
                "minced", "ground" -> "mince"
                "lidle" -> "lidl"
                "breasts" -> "breast"
                "uncooked" -> "raw"
                "dry" -> "dried"
                "wholemeal" -> "wholewheat"
                else -> word
            }
        }.filterNot { it in setOf("meat", "percent", "percentage", "of") }.toList()

internal fun searchLocalFoods(query: String, foods: List<FoodItem>, limit: Int = 25): List<FoodItem> {
    val words = searchWords(query)
    if (words.isEmpty()) return emptyList()
    return foods.distinctBy { it.source to it.id }
        .filter { food ->
            val foodWords = searchWords("${food.description} ${food.brand}")
            words.all { word ->
                foodWords.any { candidate ->
                    if (word.first().isDigit()) candidate == word else candidate.startsWith(word)
                }
            }
        }
        .sortedWith(
            compareBy<FoodItem> {
                when (it.source) {
                    FoodSource.MANUAL -> 0
                    FoodSource.GENERIC -> 1
                    else -> 2
                }
            }.thenBy { !it.description.startsWith(query.trim(), ignoreCase = true) }
                .thenBy { it.description.length }
        )
        .take(limit)
}
