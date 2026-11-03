package com.example.carbomon

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun CatalogJsonImportSection(state: NutritionUiState, onAction: (NutritionAction) -> Unit) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf("food") }
    // Large pasted documents must not enter Android's limited saved-state bundle.
    var text by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<CatalogImportDraft?>(null) }
    var error by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val busy = state.isImporting || state.isSyncing
    LaunchedEffect(state.jsonImportSavedId) {
        if (state.jsonImportSavedId != null && state.jsonImportSavedId == draft?.id) {
            text = ""
            draft = null
            error = ""
        }
    }
    fun resource(folder: String): String = context.assets.open("$folder/$kind.json").bufferedReader().use { it.readText() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { expanded = !expanded }, enabled = !busy) { Text(stringResource(R.string.json_import_title)) }
            if (expanded) {
                Text(stringResource(R.string.json_import_help), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    kind = if (kind == "food") "recipe" else "food"
                    draft = null
                    copied = false
                    error = ""
                }, enabled = !busy) {
                    Text(stringResource(if (kind == "food") R.string.json_import_kind_food else R.string.json_import_kind_recipe))
                }
                OutlinedButton(onClick = {
                    runCatching {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("CarboMon AI prompt", catalogAiPrompt(kind, resource("schemas"), resource("examples"))))
                    }.onSuccess { copied = true; error = "" }.onFailure { error = it.message.orEmpty().take(500) }
                }, enabled = !busy) { Text(stringResource(R.string.json_import_copy_prompt)) }
                if (copied) Text(stringResource(R.string.json_import_copied), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        if (value.length <= 512_000) { text = value; draft = null; error = "" }
                        else error = context.getString(R.string.json_import_too_large)
                    },
                    label = { Text(stringResource(R.string.json_import_paste_label)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 10, enabled = !busy
                )
                OutlinedButton(onClick = {
                    runCatching {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        require(pasted.length <= 512_000) { context.getString(R.string.json_import_too_large) }
                        text = pasted
                        draft = null
                        error = ""
                    }.onFailure { error = it.message.orEmpty().take(500) }
                }, enabled = !busy) { Text(stringResource(R.string.json_import_paste)) }
                OutlinedButton(onClick = {
                    runCatching { prepareCatalogImport(text, kind, JSONObject(resource("schemas"))) }
                        .onSuccess { draft = it; error = "" }
                        .onFailure { draft = null; error = it.message.orEmpty().take(500) }
                }, enabled = !busy && text.isNotBlank()) { Text(stringResource(R.string.json_import_review)) }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (state.jsonImportError.isNotBlank()) Text(state.jsonImportError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                draft?.let { reviewed ->
                    val records = remember(reviewed.id) { JSONObject(reviewed.catalogJson).getJSONArray("records").objects() }
                    Text(stringResource(R.string.json_import_preview_count, records.size), fontWeight = FontWeight.SemiBold)
                    records.forEach { record ->
                        val data = record.getJSONObject("data")
                        Text(data.optString("name", data.optString("description")), fontWeight = FontWeight.Medium)
                        if (kind == "food") {
                            if (data.optString("brand").isNotBlank()) Text(data.getString("brand"), style = MaterialTheme.typography.bodySmall)
                            ImportedNutritionPreview(data)
                            if (data.optString("nutritionSource").isNotBlank()) Text(data.getString("nutritionSource"), style = MaterialTheme.typography.bodySmall)
                        } else {
                            if (data.optString("notes").isNotBlank()) Text(data.getString("notes"), style = MaterialTheme.typography.bodySmall)
                            if (!data.isNull("finishedWeightGrams")) Text(stringResource(R.string.json_import_finished_weight, data.getDouble("finishedWeightGrams")), style = MaterialTheme.typography.bodySmall)
                            data.getJSONArray("ingredients").objects().forEach { ingredient ->
                                Text("${ingredient.getString("foodDescription")}: ${ingredient.getDouble("gramsUsed")} g", style = MaterialTheme.typography.bodySmall)
                                ImportedNutritionPreview(ingredient)
                            }
                        }
                    }
                    Text(stringResource(R.string.json_import_defaults_note), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onAction(NutritionAction.ImportCatalogJson(reviewed)) }, enabled = !busy) {
                        Text(stringResource(if (busy) R.string.json_import_saving else R.string.json_import_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportedNutritionPreview(data: JSONObject) {
    Text("${data.getDouble("caloriesPer100g")} kcal / 100 g · P ${data.getDouble("proteinPer100g")} g · C ${data.getDouble("carbsPer100g")} g · F ${data.getDouble("fatPer100g")} g", style = MaterialTheme.typography.bodySmall)
    val fields = listOf(
        Triple("fiberPer100g", R.string.micronutrient_fiber, "g"),
        Triple("sugarPer100g", R.string.micronutrient_sugar, "g"),
        Triple("sodiumMgPer100g", R.string.micronutrient_sodium, "mg"),
        Triple("potassiumMgPer100g", R.string.micronutrient_potassium, "mg"),
        Triple("calciumMgPer100g", R.string.micronutrient_calcium, "mg"),
        Triple("ironMgPer100g", R.string.micronutrient_iron, "mg")
    )
    val labels = fields.map { (key, label, unit) -> "${stringResource(label)}: ${data.getDouble(key)} $unit" }
    Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    Text(if (data.isNull("cholesterolMgPer100g")) stringResource(R.string.cholesterol_unknown)
        else stringResource(R.string.cholesterol_per_100g, data.getDouble("cholesterolMgPer100g")), style = MaterialTheme.typography.bodySmall)
}
