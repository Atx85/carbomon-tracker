# Built-in food data

The app includes 23 generic staples in `StapleFoods.kt`. They are available offline in diary and recipe searches. Each has a stable source ID; personal products are stored separately and included in backups. Generic foods are labelled as such in search results and are not claimed to represent Lidl or another retailer.

## UK CoFID 2021

Twenty entries are taken from Public Health England's [Composition of Foods Integrated Dataset 2021](https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid), tables 1.3 (Proximates) and 1.4 (Inorganics), joined by food code. The original food code and name are recorded beside each entry. Display names have been shortened, with raw/cooked state retained. Chicken light meat is searchable as chicken breast. Mince fat percentages reflect the measured values in the dataset rather than supermarket pack claims.

Contains public sector information licensed under the [Open Government Licence v3.0](https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/).

Values are per 100 g: energy in kcal; protein, carbohydrate, fat, AOAC fibre and total sugars in grams; sodium, potassium, calcium and iron in milligrams. Trace values (`Tr`) are represented as zero. The existing nutrition model also uses zero for unavailable values: AOAC fibre is unavailable for CoFID 18-508, 18-606, 18-607 and 11-716. Those zeroes do not establish absence of fibre. NSP fibre has not been substituted for AOAC fibre.

## Generic USDA mince variants

The raw 15% fat beef entry uses [USDA Standard Reference Release 27, NDB 23567](https://www.ars.usda.gov/SP2UserFiles/Place/12354500/Data/SR27/reports/sr27fg13.pdf#page=1075) (also identified by FDC SR Legacy ID 171796). It is labelled USDA SR and generic, separately from CoFID and online USDA search results.

Raw pork mince at 4% and 16% fat uses [USDA Standard Reference Release 28](https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg10.pdf): NDB 10973 (PDF page 982) and NDB 10972 (PDF page 973), respectively. These remain labelled generic USDA SR foods, with their source fat percentages retained.

## Personal products, including Lidl UK

Use Manual entry and **Save to my foods** to save the package's per-100-g nutrition without logging a meal. Include `Lidl UK` in the brand field and the fat percentage and raw/cooked state in the product name. Save each pork or beef fat variant as a separate product using its own label; generic values are not used to invent retailer-specific variants. A manual ingredient confirmed in a recipe is saved to the same personal collection.

Personal matches come first, followed by generic staples and cached foods. Matching combines name and brand and accepts words in any order, `minced`/`ground`/`mince`, and `Lidle`/`Lidl`. Percentage numbers match exactly, so searching for 5% does not match 15%. When configured, USDA results are appended; local results remain available if the API fails or its key is absent. An exact brand query only matches products bearing that brand, not generic substitutions.
