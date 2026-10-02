# Built-in food data

The app includes 28 generic staples in `StapleFoods.kt`. They are available offline in diary and recipe searches. Each has a stable source ID; personal products are stored separately and included in backups. Generic foods are labelled as such in search results and are not claimed to represent Lidl or another retailer.

## UK CoFID 2021

Twenty-four entries are taken from Public Health England's [Composition of Foods Integrated Dataset 2021](https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid), tables 1.3 (Proximates) and 1.4 (Inorganics), joined by food code. The original food code and name are recorded beside each entry. Display names have been shortened, with raw/cooked state retained. Chicken light meat is searchable as chicken breast. Mince fat percentages reflect the measured values in the dataset rather than supermarket pack claims.

Contains public sector information licensed under the [Open Government Licence v3.0](https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/).

Values are per 100 g: energy in kcal; protein, carbohydrate, fat, AOAC fibre and total sugars in grams; sodium, potassium, calcium, iron and cholesterol in milligrams. Trace values (`Tr`) are represented as zero. The existing nutrition model also uses zero for unavailable values: AOAC fibre is unavailable for CoFID 18-508, 18-606, 18-607, 11-716 and 13-318. Those zeroes do not establish absence of fibre. NSP fibre has not been substituted for AOAC fibre.

## Generic USDA mince variants

The raw 15% fat beef entry uses [USDA Standard Reference Release 27, NDB 23567](https://www.ars.usda.gov/SP2UserFiles/Place/12354500/Data/SR27/reports/sr27fg13.pdf#page=1075) (also identified by FDC SR Legacy ID 171796). It is labelled USDA SR and generic, separately from CoFID and online USDA search results.

Raw pork mince at 4% and 16% fat uses [USDA Standard Reference Release 28](https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg10.pdf): NDB 10973 (PDF page 982) and NDB 10972 (PDF page 973), respectively. These remain labelled generic USDA SR foods, with their source fat percentages retained.

## Bananas, fresh peppers and ground paprika

Bananas use CoFID 14-318 (flesh only); weigh them peeled. Raw red, yellow and green bell peppers use CoFID 13-524, 13-526 and 13-318 respectively; weigh the edible portion without stalks or seeds. The fresh pepper names include `paprika` so both meanings are searchable, with colour and raw state preserved.

Ground paprika uses [USDA SR28, NDB 02028, Spices, paprika](https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg02.pdf#page=104). CoFID's paprika entry has no energy or carbohydrate value, so the complete USDA entry is used instead. USDA carbohydrate by difference (53.99 g/100 g) includes dietary fibre (34.9 g/100 g); it is retained as published, not converted to a UK label carbohydrate value. The app therefore preserves different carbohydrate conventions across its sources.

These additions use generic nutrition. Lidl UK package values were not verified, and none is labelled as a Lidl product. Use the package label to save an exact retailer product.

## Personal products, including Lidl UK

Use Manual entry and **Save to my foods** to save the package's per-100-g nutrition without logging a meal. Include `Lidl UK` in the brand field and the fat percentage and raw/cooked state in the product name. Save each pork or beef fat variant as a separate product using its own label; generic values are not used to invent retailer-specific variants. A manual ingredient confirmed in a recipe is saved to the same personal collection.

Personal matches come first, followed by generic staples and cached foods. Matching combines name and brand and accepts words in any order, `minced`/`ground`/`mince`, and `Lidle`/`Lidl`. Percentage numbers match exactly, so searching for 5% does not match 15%. When configured, USDA results are appended; local results remain available if the API fails or its key is absent. An exact brand query only matches products bearing that brand, not generic substitutions.

## Cholesterol data and units

The nullable `cholesterolMgPer100g` field stores dietary cholesterol in milligrams per 100 g. CoFID entries use the `Cholesterol (mg)` column in table 1.3; trace values follow the existing zero convention. USDA SR28 pork values are 59 mg for NDB 10973 and 68 mg for NDB 10972 (PDF pages 983 and 974); USDA SR27 beef NDB 23567 is 68 mg (PDF page 1076). USDA SR28 paprika NDB 02028 is 0 mg (PDF page 105). All 28 staples have a sourced value.

USDA API search/detail responses use nutrient number 601 / nutrient ID 1253 (Cholesterol), with unit conversion to mg. Label-only USDA values and FatSecret serving values are converted to per 100 g only when a positive serving weight in grams is supplied. Volume or unknown serving weights remain unknown. [FatSecret documents cholesterol in mg per serving](https://platform.fatsecret.com/docs/v4/food.get). Open Food Facts `cholesterol_100g` is normalized in grams, so the app multiplies it by 1,000; the original label's `cholesterol_unit` does not change that normalization ([field definitions](https://github.com/openfoodfacts/openfoodfacts-server/blob/main/html/data-fields.txt)).

An absent, null, negative or non-finite cholesterol value is unknown, not zero. The same nullable value is used in foods, diary entries, recipe ingredients and JSON persistence. Existing backup schema 1 remains compatible because this is an optional field. Old records are not assigned an invented value. A recipe has a complete per-100-g cholesterol value only when every nonzero ingredient portion has data; diary totals show the known sum plus an incomplete-data notice when needed.
