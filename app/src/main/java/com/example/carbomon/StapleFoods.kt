package com.example.carbomon

// Generic per-100g foods, not retailer-specific products. Source codes and units
// are preserved from CoFID 2021 and USDA SR; see docs/food-data-sources.md.
internal val stapleFoods: List<FoodItem> = listOf(
    // CoFID 14-318: Bananas, flesh only
    FoodItem(
        id = "cofid-14-318", source = FoodSource.GENERIC,
        description = "Bananas, raw, peeled", brand = "CoFID 2021",
        caloriesPer100g = 81.0,
        proteinPer100g = 1.2,
        carbsPer100g = 20.3,
        fatPer100g = 0.1,
        fiberPer100g = 1.4,
        sugarPer100g = 18.1,
        sodiumMgPer100g = 0.0,
        potassiumMgPer100g = 330.0,
        calciumMgPer100g = 6.0,
        ironMgPer100g = 0.27,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 13-524: Pepper, capsicum, red, raw
    FoodItem(
        id = "cofid-13-524", source = FoodSource.GENERIC,
        description = "Red bell peppers (fresh paprika), raw", brand = "CoFID 2021",
        caloriesPer100g = 21.0,
        proteinPer100g = 0.8,
        carbsPer100g = 4.3,
        fatPer100g = 0.2,
        fiberPer100g = 2.2,
        sugarPer100g = 4.2,
        sodiumMgPer100g = 1.0,
        potassiumMgPer100g = 216.0,
        calciumMgPer100g = 7.0,
        ironMgPer100g = 0.39,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 13-526: Pepper, capsicum, yellow, raw
    FoodItem(
        id = "cofid-13-526", source = FoodSource.GENERIC,
        description = "Yellow bell peppers (fresh paprika), raw", brand = "CoFID 2021",
        caloriesPer100g = 23.0,
        proteinPer100g = 0.8,
        carbsPer100g = 4.6,
        fatPer100g = 0.2,
        fiberPer100g = 2.2,
        sugarPer100g = 4.4,
        sodiumMgPer100g = 1.0,
        potassiumMgPer100g = 189.0,
        calciumMgPer100g = 7.0,
        ironMgPer100g = 0.48,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 13-318: Peppers, capsicum, green, raw
    FoodItem(
        id = "cofid-13-318", source = FoodSource.GENERIC,
        description = "Green bell peppers (fresh paprika), raw", brand = "CoFID 2021",
        caloriesPer100g = 15.0,
        proteinPer100g = 0.8,
        carbsPer100g = 2.6,
        fatPer100g = 0.3,
        fiberPer100g = 0.0,
        sugarPer100g = 2.4,
        sodiumMgPer100g = 4.0,
        potassiumMgPer100g = 120.0,
        calciumMgPer100g = 8.0,
        ironMgPer100g = 0.4,
        cholesterolMgPer100g = 0.0
    ),
    // USDA SR28, NDB 02028: Spices, paprika. Total carbohydrate includes fibre.
    FoodItem(
        id = "usda-sr28-02028", source = FoodSource.GENERIC,
        description = "Paprika, ground spice powder", brand = "USDA SR",
        caloriesPer100g = 282.0, proteinPer100g = 14.14,
        carbsPer100g = 53.99, fatPer100g = 12.89,
        fiberPer100g = 34.9, sugarPer100g = 10.34,
        sodiumMgPer100g = 68.0, potassiumMgPer100g = 2280.0,
        calciumMgPer100g = 229.0, ironMgPer100g = 21.14,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 17-010: Lard
    FoodItem(
        id = "cofid-17-010", source = FoodSource.GENERIC,
        description = "Lard", brand = "CoFID 2021",
        caloriesPer100g = 891.0,
        proteinPer100g = 0.0,
        carbsPer100g = 0.0,
        fatPer100g = 99.0,
        fiberPer100g = 0.0,
        sugarPer100g = 0.0,
        sodiumMgPer100g = 2.0,
        potassiumMgPer100g = 1.0,
        calciumMgPer100g = 1.0,
        ironMgPer100g = 0.1,
        cholesterolMgPer100g = 93.0
    ),
    // CoFID 18-290: Chicken, light meat, raw
    FoodItem(
        id = "cofid-18-290", source = FoodSource.GENERIC,
        description = "Chicken breast (light meat), raw", brand = "CoFID 2021",
        caloriesPer100g = 106.0,
        proteinPer100g = 24.0,
        carbsPer100g = 0.0,
        fatPer100g = 1.1,
        fiberPer100g = 0.0,
        sugarPer100g = 0.0,
        sodiumMgPer100g = 60.0,
        potassiumMgPer100g = 370.0,
        calciumMgPer100g = 5.0,
        ironMgPer100g = 0.5,
        cholesterolMgPer100g = 70.0
    ),
    // CoFID 18-323: Chicken, breast, grilled without skin, meat only
    FoodItem(
        id = "cofid-18-323", source = FoodSource.GENERIC,
        description = "Chicken breast, skinless, grilled", brand = "CoFID 2021",
        caloriesPer100g = 148.0,
        proteinPer100g = 32.0,
        carbsPer100g = 0.0,
        fatPer100g = 2.2,
        fiberPer100g = 0.0,
        sugarPer100g = 0.0,
        sodiumMgPer100g = 55.0,
        potassiumMgPer100g = 460.0,
        calciumMgPer100g = 6.0,
        ironMgPer100g = 0.4,
        cholesterolMgPer100g = 94.0
    ),
    // CoFID 18-469: Beef, mince, raw
    FoodItem(
        id = "cofid-18-469", source = FoodSource.GENERIC,
        description = "Beef mince, 16.2% fat, raw", brand = "CoFID 2021",
        caloriesPer100g = 225.0,
        proteinPer100g = 19.7,
        carbsPer100g = 0.0,
        fatPer100g = 16.2,
        fiberPer100g = 0.0,
        sugarPer100g = 0.0,
        sodiumMgPer100g = 80.0,
        potassiumMgPer100g = 260.0,
        calciumMgPer100g = 9.0,
        ironMgPer100g = 1.4,
        cholesterolMgPer100g = 60.0
    ),
    // CoFID 18-508: Beef, mince, raw, extra lean
    FoodItem(
        id = "cofid-18-508", source = FoodSource.GENERIC,
        description = "Beef mince, extra lean, 4.2% fat, raw", brand = "CoFID 2021",
        caloriesPer100g = 130.0,
        proteinPer100g = 21.9,
        carbsPer100g = 0.0,
        fatPer100g = 4.2,
        fiberPer100g = 0.0,
        sugarPer100g = 0.0,
        sodiumMgPer100g = 90.0,
        potassiumMgPer100g = 290.0,
        calciumMgPer100g = 10.0,
        ironMgPer100g = 1.5,
        cholesterolMgPer100g = 37.0
    ),
    // CoFID 18-606: Pork, mince, raw
    FoodItem(
        id = "cofid-18-606", source = FoodSource.GENERIC,
        description = "Pork mince, 9.7% fat, raw", brand = "CoFID 2021",
        caloriesPer100g = 164.0,
        proteinPer100g = 19.2,
        carbsPer100g = 0.0,
        fatPer100g = 9.7,
        fiberPer100g = 0.0,
        sugarPer100g = 0.0,
        sodiumMgPer100g = 66.0,
        potassiumMgPer100g = 390.0,
        calciumMgPer100g = 7.0,
        ironMgPer100g = 0.9,
        cholesterolMgPer100g = 66.0
    ),
    // CoFID 18-607: Pork, mince, stewed
    FoodItem(
        id = "cofid-18-607", source = FoodSource.GENERIC,
        description = "Pork mince, stewed", brand = "CoFID 2021",
        caloriesPer100g = 191.0,
        proteinPer100g = 24.4,
        carbsPer100g = 0.0,
        fatPer100g = 10.4,
        fiberPer100g = 0.0,
        sugarPer100g = 0.0,
        sodiumMgPer100g = 61.0,
        potassiumMgPer100g = 320.0,
        calciumMgPer100g = 13.0,
        ironMgPer100g = 1.4,
        cholesterolMgPer100g = 81.0
    ),
    // CoFID 11-716: Pasta, white, dried, raw
    FoodItem(
        id = "cofid-11-716", source = FoodSource.GENERIC,
        description = "Pasta, white, dried, raw", brand = "CoFID 2021",
        caloriesPer100g = 343.0,
        proteinPer100g = 11.3,
        carbsPer100g = 75.6,
        fatPer100g = 1.6,
        fiberPer100g = 0.0,
        sugarPer100g = 2.1,
        sodiumMgPer100g = 2.0,
        potassiumMgPer100g = 232.0,
        calciumMgPer100g = 24.0,
        ironMgPer100g = 1.59,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 11-1129: Pasta, white, dried, boiled in unsalted water
    FoodItem(
        id = "cofid-11-1129", source = FoodSource.GENERIC,
        description = "Pasta, white, boiled in unsalted water", brand = "CoFID 2021",
        caloriesPer100g = 169.0,
        proteinPer100g = 5.5,
        carbsPer100g = 37.2,
        fatPer100g = 0.8,
        fiberPer100g = 2.5,
        sugarPer100g = 1.1,
        sodiumMgPer100g = 1.0,
        potassiumMgPer100g = 114.0,
        calciumMgPer100g = 12.0,
        ironMgPer100g = 0.78,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 11-718: Pasta, wholewheat, spaghetti, dried, raw
    FoodItem(
        id = "cofid-11-718", source = FoodSource.GENERIC,
        description = "Pasta, wholewheat spaghetti, dried, raw", brand = "CoFID 2021",
        caloriesPer100g = 329.0,
        proteinPer100g = 12.6,
        carbsPer100g = 68.3,
        fatPer100g = 2.5,
        fiberPer100g = 11.7,
        sugarPer100g = 3.9,
        sodiumMgPer100g = 3.0,
        potassiumMgPer100g = 426.0,
        calciumMgPer100g = 39.0,
        ironMgPer100g = 3.25,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 11-723: Pasta, wholewheat, spaghetti, dried, boiled in unsalted water
    FoodItem(
        id = "cofid-11-723", source = FoodSource.GENERIC,
        description = "Pasta, wholewheat spaghetti, boiled in unsalted water", brand = "CoFID 2021",
        caloriesPer100g = 134.0,
        proteinPer100g = 5.2,
        carbsPer100g = 27.5,
        fatPer100g = 1.1,
        fiberPer100g = 4.2,
        sugarPer100g = 0.0,
        sodiumMgPer100g = 5.0,
        potassiumMgPer100g = 82.0,
        calciumMgPer100g = 31.0,
        ironMgPer100g = 1.48,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 11-722: Pasta, white, spaghetti, dried, boiled in unsalted water
    FoodItem(
        id = "cofid-11-722", source = FoodSource.GENERIC,
        description = "Pasta, white spaghetti, boiled in unsalted water", brand = "CoFID 2021",
        caloriesPer100g = 141.0,
        proteinPer100g = 4.4,
        carbsPer100g = 31.5,
        fatPer100g = 0.6,
        fiberPer100g = 1.7,
        sugarPer100g = 1.0,
        sodiumMgPer100g = 3.0,
        potassiumMgPer100g = 41.0,
        calciumMgPer100g = 27.0,
        ironMgPer100g = 0.63,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 11-720: Pasta, white,  twists, fusilli, dried, boiled in unsalted water
    FoodItem(
        id = "cofid-11-720", source = FoodSource.GENERIC,
        description = "Pasta, white fusilli, boiled in unsalted water", brand = "CoFID 2021",
        caloriesPer100g = 146.0,
        proteinPer100g = 4.8,
        carbsPer100g = 32.9,
        fatPer100g = 0.4,
        fiberPer100g = 2.6,
        sugarPer100g = 0.6,
        sodiumMgPer100g = 5.0,
        potassiumMgPer100g = 40.0,
        calciumMgPer100g = 25.0,
        ironMgPer100g = 0.71,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 11-857: Rice, white, basmati, raw
    FoodItem(
        id = "cofid-11-857", source = FoodSource.GENERIC,
        description = "Rice, white basmati, raw", brand = "CoFID 2021",
        caloriesPer100g = 351.0,
        proteinPer100g = 8.1,
        carbsPer100g = 83.7,
        fatPer100g = 0.5,
        fiberPer100g = 1.1,
        sugarPer100g = 0.1,
        sodiumMgPer100g = 1.0,
        potassiumMgPer100g = 77.0,
        calciumMgPer100g = 10.0,
        ironMgPer100g = 1.73,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 11-788: Porridge oats, unfortified
    FoodItem(
        id = "cofid-11-788", source = FoodSource.GENERIC,
        description = "Porridge oats, unfortified", brand = "CoFID 2021",
        caloriesPer100g = 381.0,
        proteinPer100g = 10.9,
        carbsPer100g = 70.7,
        fatPer100g = 8.1,
        fiberPer100g = 7.8,
        sugarPer100g = 0.3,
        sodiumMgPer100g = 1.0,
        potassiumMgPer100g = 372.0,
        calciumMgPer100g = 50.0,
        ironMgPer100g = 3.64,
        cholesterolMgPer100g = 0.3
    ),
    // CoFID 12-937: Eggs, chicken, whole, raw
    FoodItem(
        id = "cofid-12-937", source = FoodSource.GENERIC,
        description = "Eggs, chicken, whole, raw", brand = "CoFID 2021",
        caloriesPer100g = 131.0,
        proteinPer100g = 12.6,
        carbsPer100g = 0.0,
        fatPer100g = 9.0,
        fiberPer100g = 0.0,
        sugarPer100g = 0.0,
        sodiumMgPer100g = 154.0,
        potassiumMgPer100g = 145.0,
        calciumMgPer100g = 46.0,
        ironMgPer100g = 1.72,
        cholesterolMgPer100g = 350.0
    ),
    // CoFID 17-038: Oil, olive
    FoodItem(
        id = "cofid-17-038", source = FoodSource.GENERIC,
        description = "Olive oil", brand = "CoFID 2021",
        caloriesPer100g = 899.0,
        proteinPer100g = 0.0,
        carbsPer100g = 0.0,
        fatPer100g = 99.9,
        fiberPer100g = 0.0,
        sugarPer100g = 0.0,
        sodiumMgPer100g = 0.0,
        potassiumMgPer100g = 0.0,
        calciumMgPer100g = 0.0,
        ironMgPer100g = 0.4,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 17-685: Butter, salted
    FoodItem(
        id = "cofid-17-685", source = FoodSource.GENERIC,
        description = "Butter, salted", brand = "CoFID 2021",
        caloriesPer100g = 744.0,
        proteinPer100g = 0.6,
        carbsPer100g = 0.6,
        fatPer100g = 82.2,
        fiberPer100g = 0.0,
        sugarPer100g = 0.6,
        sodiumMgPer100g = 730.0,
        potassiumMgPer100g = 27.0,
        calciumMgPer100g = 18.0,
        ironMgPer100g = 0.0,
        cholesterolMgPer100g = 213.2
    ),
    // CoFID 13-489: Potatoes, old, raw, flesh only
    FoodItem(
        id = "cofid-13-489", source = FoodSource.GENERIC,
        description = "Potatoes, flesh only, raw", brand = "CoFID 2021",
        caloriesPer100g = 82.0,
        proteinPer100g = 1.9,
        carbsPer100g = 19.6,
        fatPer100g = 0.1,
        fiberPer100g = 2.0,
        sugarPer100g = 0.9,
        sodiumMgPer100g = 2.0,
        potassiumMgPer100g = 443.0,
        calciumMgPer100g = 7.0,
        ironMgPer100g = 0.32,
        cholesterolMgPer100g = 0.0
    ),
    // CoFID 12-313: Milk, semi-skimmed, pasteurised, average
    FoodItem(
        id = "cofid-12-313", source = FoodSource.GENERIC,
        description = "Milk, semi-skimmed, pasteurised", brand = "CoFID 2021",
        caloriesPer100g = 46.0,
        proteinPer100g = 3.5,
        carbsPer100g = 4.7,
        fatPer100g = 1.7,
        fiberPer100g = 0.0,
        sugarPer100g = 4.7,
        sodiumMgPer100g = 43.0,
        potassiumMgPer100g = 156.0,
        calciumMgPer100g = 120.0,
        ironMgPer100g = 0.02,
        cholesterolMgPer100g = 5.9
    ),
    // USDA SR28, NDB 10973; FDC SR Legacy 169190.
    FoodItem(
        id = "usda-169190", source = FoodSource.GENERIC,
        description = "Pork mince, 4% fat, raw", brand = "USDA SR",
        caloriesPer100g = 121.0, proteinPer100g = 21.10,
        carbsPer100g = 0.21, fatPer100g = 4.0,
        fiberPer100g = 0.0, sugarPer100g = 0.0,
        sodiumMgPer100g = 67.0, potassiumMgPer100g = 310.0,
        calciumMgPer100g = 15.0, ironMgPer100g = 0.86,
        cholesterolMgPer100g = 59.0
    ),
    // USDA SR28, NDB 10972; FDC SR Legacy 168372.
    FoodItem(
        id = "usda-168372", source = FoodSource.GENERIC,
        description = "Pork mince, 16% fat, raw", brand = "USDA SR",
        caloriesPer100g = 218.0, proteinPer100g = 17.99,
        carbsPer100g = 0.44, fatPer100g = 16.0,
        fiberPer100g = 0.0, sugarPer100g = 0.0,
        sodiumMgPer100g = 68.0, potassiumMgPer100g = 244.0,
        calciumMgPer100g = 15.0, ironMgPer100g = 0.88,
        cholesterolMgPer100g = 68.0
    ),
    // USDA SR27, NDB 23567; FDC SR Legacy 171796.
    FoodItem(
        id = "usda-171796", source = FoodSource.GENERIC,
        description = "Beef mince, 15% fat, raw", brand = "USDA SR",
        caloriesPer100g = 215.0, proteinPer100g = 18.59,
        carbsPer100g = 0.0, fatPer100g = 15.0,
        fiberPer100g = 0.0, sugarPer100g = 0.0,
        sodiumMgPer100g = 66.0, potassiumMgPer100g = 295.0,
        calciumMgPer100g = 15.0, ironMgPer100g = 2.09,
        cholesterolMgPer100g = 68.0
    )
)
