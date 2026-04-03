package com.example.carbomon

import android.Manifest
import android.content.pm.PackageManager
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.example.carbomon.ui.theme.CarboMonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CarboMonTheme { NutritionApp(Modifier.fillMaxSize()) } }
    }
}

enum class AppTab {
    DIARY,
    ADD_FOOD,
    MICROS,
    SETUP
}

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    HUNGARIAN("hu"),
    POLISH("pl")
}

enum class DiaryViewMode {
    DAY,
    WEEK
}

enum class LookupTab {
    NAME,
    BARCODE,
    MANUAL,
    HISTORY,
    RECIPES
}

enum class FoodSource {
    USDA,
    FATSECRET,
    OPENFOODFACTS,
    MANUAL,
    RECIPE
}

@Composable
fun NutritionApp(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as Application
    val vm: NutritionViewModel = viewModel(factory = NutritionViewModel.factory(app))
    val snack = remember { SnackbarHostState() }
    val state = vm.uiState
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { vm.onAction(NutritionAction.ExportBackup(it)) }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.onAction(NutritionAction.ImportBackup(it)) }
    }

    var selectedTab by rememberSaveable(state.profile != null) {
        mutableStateOf(if (state.profile == null) AppTab.SETUP else AppTab.DIARY)
    }
    var diaryViewMode by rememberSaveable { mutableStateOf(DiaryViewMode.DAY) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snack.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snack) }) { innerPadding ->
        val selectedDateObj = runCatching { LocalDate.parse(state.selectedDate) }.getOrElse { LocalDate.now() }
        val weekStart = selectedDateObj.startOfWeekMonday()
        val weekEnd = weekStart.plusDays(6)
        val entriesForDiary = if (diaryViewMode == DiaryViewMode.DAY) {
            state.consumedEntries.filter { it.date == state.selectedDate }
        } else {
            state.consumedEntries.filter { entry ->
                val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@filter false
                !date.isBefore(weekStart) && !date.isAfter(weekEnd)
            }
        }
        val usedKcal = entriesForDiary.sumOf { it.caloriesPer100g * it.gramsConsumed / 100.0 }
        val usedP = entriesForDiary.sumOf { it.proteinPer100g * it.gramsConsumed / 100.0 }
        val usedC = entriesForDiary.sumOf { it.carbsPer100g * it.gramsConsumed / 100.0 }
        val usedF = entriesForDiary.sumOf { it.fatPer100g * it.gramsConsumed / 100.0 }
        val usedFiber = entriesForDiary.sumOf { it.fiberPer100g * it.gramsConsumed / 100.0 }
        val usedSugar = entriesForDiary.sumOf { it.sugarPer100g * it.gramsConsumed / 100.0 }
        val usedSodiumMg = entriesForDiary.sumOf { it.sodiumMgPer100g * it.gramsConsumed / 100.0 }
        val usedPotassiumMg = entriesForDiary.sumOf { it.potassiumMgPer100g * it.gramsConsumed / 100.0 }
        val usedCalciumMg = entriesForDiary.sumOf { it.calciumMgPer100g * it.gramsConsumed / 100.0 }
        val usedIronMg = entriesForDiary.sumOf { it.ironMgPer100g * it.gramsConsumed / 100.0 }
        val dayMultiplier = if (diaryViewMode == DiaryViewMode.WEEK) 7 else 1
        val targetKcal = (state.profile?.recommendedCalories ?: 0) * dayMultiplier

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.app_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            TabRow(selectedTabIndex = AppTab.entries.indexOf(selectedTab).coerceAtLeast(0)) {
                Tab(
                    selected = selectedTab == AppTab.DIARY,
                    onClick = { selectedTab = AppTab.DIARY },
                    icon = { Icon(Icons.Filled.Today, contentDescription = stringResource(R.string.tab_diary_desc)) }
                )
                Tab(
                    selected = selectedTab == AppTab.ADD_FOOD,
                    onClick = { selectedTab = AppTab.ADD_FOOD },
                    icon = { Icon(Icons.Filled.AddCircle, contentDescription = stringResource(R.string.tab_add_food_desc)) }
                )
                Tab(
                    selected = selectedTab == AppTab.MICROS,
                    onClick = { selectedTab = AppTab.MICROS },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = stringResource(R.string.tab_micros_desc)) }
                )
                Tab(
                    selected = selectedTab == AppTab.SETUP,
                    onClick = { selectedTab = AppTab.SETUP },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_setup_desc)) }
                )
            }

            when (selectedTab) {
                AppTab.DIARY -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (diaryViewMode == DiaryViewMode.DAY) vm.onAction(NutritionAction.PreviousDay)
                                else vm.onAction(NutritionAction.PreviousWeek)
                            }
                        ) { Text("<") }
                        Button(onClick = { vm.onAction(NutritionAction.Today) }) { Text(stringResource(R.string.today)) }
                        Button(
                            onClick = {
                                if (diaryViewMode == DiaryViewMode.DAY) vm.onAction(NutritionAction.NextDay)
                                else vm.onAction(NutritionAction.NextWeek)
                            }
                        ) { Text(">") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { diaryViewMode = DiaryViewMode.DAY },
                            enabled = diaryViewMode != DiaryViewMode.DAY
                        ) { Text(stringResource(R.string.view_day)) }
                        Button(
                            onClick = { diaryViewMode = DiaryViewMode.WEEK },
                            enabled = diaryViewMode != DiaryViewMode.WEEK
                        ) { Text(stringResource(R.string.view_week)) }
                    }
                    Text(
                        if (diaryViewMode == DiaryViewMode.DAY) {
                            stringResource(R.string.diary_day, state.selectedDate)
                        } else {
                            stringResource(R.string.diary_week, weekStart.toString(), weekEnd.toString())
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    state.profile?.macroTargets?.let { targets ->
                        DiaryHealthBars(
                            usedKcal = usedKcal,
                            targetKcal = targetKcal,
                            usedProtein = usedP,
                            targetProtein = targets.proteinGrams * dayMultiplier,
                            usedCarbs = usedC,
                            targetCarbs = targets.carbsGrams * dayMultiplier,
                            usedFat = usedF,
                            targetFat = targets.fatGrams * dayMultiplier
                        )
                    }
                    ConsumedFoodsSection(
                        entries = entriesForDiary,
                        headerText = if (diaryViewMode == DiaryViewMode.DAY) {
                            stringResource(R.string.foods_on_day, state.selectedDate)
                        } else {
                            stringResource(R.string.foods_on_week, weekStart.toString(), weekEnd.toString())
                        },
                        onAction = vm::onAction
                    )
                }

                AppTab.ADD_FOOD -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.onAction(NutritionAction.PreviousDay) }) { Text("<") }
                        Button(onClick = { vm.onAction(NutritionAction.Today) }) { Text(stringResource(R.string.today)) }
                        Button(onClick = { vm.onAction(NutritionAction.NextDay) }) { Text(">") }
                    }
                    Text(
                        text = stringResource(R.string.add_food_for_day, state.selectedDate),
                        style = MaterialTheme.typography.titleMedium
                    )
                    SearchSection(state, vm::onAction)
                }

                AppTab.MICROS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.onAction(NutritionAction.PreviousDay) }) { Text("<") }
                        Button(onClick = { vm.onAction(NutritionAction.Today) }) { Text(stringResource(R.string.today)) }
                        Button(onClick = { vm.onAction(NutritionAction.NextDay) }) { Text(">") }
                    }
                    Text(stringResource(R.string.micros_for_day, state.selectedDate), style = MaterialTheme.typography.titleMedium)
                    DailyMicronutrientsSection(
                        fiberGrams = usedFiber,
                        sugarGrams = usedSugar,
                        sodiumMg = usedSodiumMg,
                        potassiumMg = usedPotassiumMg,
                        calciumMg = usedCalciumMg,
                        ironMg = usedIronMg
                    )
                }

                AppTab.SETUP -> {
                    ProfileSetupSection(
                        state = state,
                        onAction = vm::onAction,
                        onExportBackup = {
                            exportBackupLauncher.launch("carbomon-backup-${LocalDate.now()}.json")
                        },
                        onImportBackup = {
                            importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSetupSection(
    state: NutritionUiState,
    onAction: (NutritionAction) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var sexMenuExpanded by remember { mutableStateOf(false) }
    var activityMenuExpanded by remember { mutableStateOf(false) }
    var goalMenuExpanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(state.weightKgInput, { onAction(NutritionAction.UpdateWeight(it)) }, label = { Text(stringResource(R.string.weight_kg)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.heightCmInput, { onAction(NutritionAction.UpdateHeight(it)) }, label = { Text(stringResource(R.string.height_cm)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.ageInput, { onAction(NutritionAction.UpdateAge(it)) }, label = { Text(stringResource(R.string.age)) }, modifier = Modifier.fillMaxWidth())

            Text(stringResource(R.string.sex))
            val selectedSexLabel = when (state.sex) {
                Sex.MALE -> stringResource(R.string.sex_male)
                Sex.FEMALE -> stringResource(R.string.sex_female)
                Sex.OTHER -> stringResource(R.string.sex_other)
            }
            Box {
                Button(onClick = { sexMenuExpanded = true }) { Text(selectedSexLabel) }
                DropdownMenu(
                    expanded = sexMenuExpanded,
                    onDismissRequest = { sexMenuExpanded = false }
                ) {
                    Sex.entries.forEach { sex ->
                        val label = when (sex) {
                            Sex.MALE -> stringResource(R.string.sex_male)
                            Sex.FEMALE -> stringResource(R.string.sex_female)
                            Sex.OTHER -> stringResource(R.string.sex_other)
                        }
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                sexMenuExpanded = false
                                onAction(NutritionAction.UpdateSex(sex))
                            }
                        )
                    }
                }
            }

            Text(stringResource(R.string.activity))
            val selectedActivityLabel = when (state.activityLevel) {
                ActivityLevel.LOW -> stringResource(R.string.activity_low)
                ActivityLevel.MODERATE -> stringResource(R.string.activity_moderate)
                ActivityLevel.HIGH -> stringResource(R.string.activity_high)
            }
            Box {
                Button(onClick = { activityMenuExpanded = true }) { Text(selectedActivityLabel) }
                DropdownMenu(
                    expanded = activityMenuExpanded,
                    onDismissRequest = { activityMenuExpanded = false }
                ) {
                    ActivityLevel.entries.forEach { level ->
                        val label = when (level) {
                            ActivityLevel.LOW -> stringResource(R.string.activity_low)
                            ActivityLevel.MODERATE -> stringResource(R.string.activity_moderate)
                            ActivityLevel.HIGH -> stringResource(R.string.activity_high)
                        }
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                activityMenuExpanded = false
                                onAction(NutritionAction.UpdateActivity(level))
                            }
                        )
                    }
                }
            }

            Text(stringResource(R.string.goal))
            val selectedGoalLabel = when (state.goal) {
                Goal.KEEP_MUSCLE_WEIGHT_LOSS -> stringResource(R.string.goal_keep_muscle_weight_loss)
                Goal.MAINTAIN -> stringResource(R.string.goal_maintain)
                Goal.BULK_MUSCLE -> stringResource(R.string.goal_bulk_muscle)
            }
            Box {
                Button(onClick = { goalMenuExpanded = true }) { Text(selectedGoalLabel) }
                DropdownMenu(
                    expanded = goalMenuExpanded,
                    onDismissRequest = { goalMenuExpanded = false }
                ) {
                    Goal.entries.forEach { goal ->
                        val label = when (goal) {
                            Goal.KEEP_MUSCLE_WEIGHT_LOSS -> stringResource(R.string.goal_keep_muscle_weight_loss)
                            Goal.MAINTAIN -> stringResource(R.string.goal_maintain)
                            Goal.BULK_MUSCLE -> stringResource(R.string.goal_bulk_muscle)
                        }
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                goalMenuExpanded = false
                                onAction(NutritionAction.UpdateGoal(goal))
                            }
                        )
                    }
                }
            }
            Button({ onAction(NutritionAction.SaveProfile) }) { Text(stringResource(R.string.save_calorie_plan)) }
            state.profile?.let {
                Text(stringResource(R.string.recommended_kcal, it.recommendedCalories))
                Text(
                    stringResource(
                        R.string.macro_targets_summary,
                        it.macroTargets.proteinGrams.roundToInt(),
                        it.macroTargets.carbsGrams.roundToInt(),
                        it.macroTargets.fatGrams.roundToInt()
                    )
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val selectedLanguageLabel = when (state.language) {
                AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                AppLanguage.HUNGARIAN -> stringResource(R.string.language_hungarian)
                AppLanguage.POLISH -> stringResource(R.string.language_polish)
            }
            Box {
                Button(onClick = { languageMenuExpanded = true }) {
                    Text(stringResource(R.string.language_selected, selectedLanguageLabel))
                }
                DropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false }
                ) {
                    AppLanguage.entries.forEach { language ->
                        val label = when (language) {
                            AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                            AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                            AppLanguage.HUNGARIAN -> stringResource(R.string.language_hungarian)
                            AppLanguage.POLISH -> stringResource(R.string.language_polish)
                        }
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                languageMenuExpanded = false
                                onAction(NutritionAction.UpdateLanguage(language))
                            }
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(stringResource(R.string.backup_restore), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.backup_restore_help),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExportBackup,
                    enabled = !state.isExporting && !state.isImporting
                ) {
                    Text(if (state.isExporting) stringResource(R.string.exporting) else stringResource(R.string.export_backup))
                }
                Button(
                    onClick = onImportBackup,
                    enabled = !state.isExporting && !state.isImporting
                ) {
                    Text(if (state.isImporting) stringResource(R.string.importing) else stringResource(R.string.import_backup))
                }
            }
        }
    }
}

@Composable
private fun SearchSection(state: NutritionUiState, onAction: (NutritionAction) -> Unit) {
    val context = LocalContext.current
    var lookupTab by rememberSaveable { mutableStateOf(LookupTab.NAME) }
    var showScanner by rememberSaveable { mutableStateOf(false) }
    var cameraDenied by rememberSaveable { mutableStateOf(false) }
    var manualName by rememberSaveable { mutableStateOf("") }
    var manualBrand by rememberSaveable { mutableStateOf("") }
    var manualCalories by rememberSaveable { mutableStateOf("") }
    var manualProtein by rememberSaveable { mutableStateOf("") }
    var manualCarbs by rememberSaveable { mutableStateOf("") }
    var manualFat by rememberSaveable { mutableStateOf("") }
    var manualFiber by rememberSaveable { mutableStateOf("") }
    var manualSugar by rememberSaveable { mutableStateOf("") }
    var manualSodiumMg by rememberSaveable { mutableStateOf("") }
    var manualPotassiumMg by rememberSaveable { mutableStateOf("") }
    var manualCalciumMg by rememberSaveable { mutableStateOf("") }
    var manualIronMg by rememberSaveable { mutableStateOf("") }
    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraDenied = !granted
        showScanner = granted
    }
    val previouslyEatenFoods = remember(state.consumedEntries) {
        state.consumedEntries
            .asReversed()
            .distinctBy { "${it.source.name}|${it.foodId}|${it.description.lowercase()}" }
            .map { it.asFoodItem() }
            .take(12)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TabRow(
                selectedTabIndex = when (lookupTab) {
                    LookupTab.NAME -> 0
                    LookupTab.BARCODE -> 1
                    LookupTab.MANUAL -> 2
                    LookupTab.HISTORY -> 3
                    LookupTab.RECIPES -> 4
                }
            ) {
                Tab(
                    selected = lookupTab == LookupTab.NAME,
                    onClick = { lookupTab = LookupTab.NAME },
                    icon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.lookup_by_name)) }
                )
                Tab(
                    selected = lookupTab == LookupTab.BARCODE,
                    onClick = { lookupTab = LookupTab.BARCODE },
                    icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.lookup_by_barcode)) }
                )
                Tab(
                    selected = lookupTab == LookupTab.MANUAL,
                    onClick = { lookupTab = LookupTab.MANUAL },
                    icon = { Icon(Icons.Filled.AddCircle, contentDescription = stringResource(R.string.add_food_manually)) }
                )
                Tab(
                    selected = lookupTab == LookupTab.HISTORY,
                    onClick = { lookupTab = LookupTab.HISTORY },
                    icon = { Icon(Icons.Filled.History, contentDescription = stringResource(R.string.previously_eaten)) }
                )
                Tab(
                    selected = lookupTab == LookupTab.RECIPES,
                    onClick = { lookupTab = LookupTab.RECIPES },
                    icon = { Icon(Icons.Filled.BlenderOutlined, contentDescription = "My Recipes") }
                )
            }

            when (lookupTab) {
                LookupTab.NAME -> {
                    Text(stringResource(R.string.food_name_lookup), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(state.query, { onAction(NutritionAction.UpdateQuery(it)) }, label = { Text(stringResource(R.string.type_food_then_search)) }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ onAction(NutritionAction.SearchFoods) }) { Text(stringResource(R.string.search)) }
                        if (state.isLoading) CircularProgressIndicator(Modifier.width(24.dp))
                    }
                    if (BuildConfig.USDA_API_KEY.isBlank()) {
                        Text(stringResource(R.string.usda_api_missing_cached), color = MaterialTheme.colorScheme.error)
                    }
                }

                LookupTab.BARCODE -> {
                    Text(stringResource(R.string.barcode_lookup), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        state.barcodeInput,
                        { onAction(NutritionAction.UpdateBarcode(it)) },
                        label = { Text(stringResource(R.string.barcode_input_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ onAction(NutritionAction.LookupBarcode) }) { Text(stringResource(R.string.lookup_barcode)) }
                        if (state.isLoadingBarcode) CircularProgressIndicator(Modifier.width(24.dp))
                    }
                    Button(
                        onClick = {
                            if (hasCameraPermission) {
                                showScanner = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.scan_barcode_camera))
                    }
                    if (cameraDenied) {
                        Text(
                            stringResource(R.string.camera_permission_denied),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (showScanner) {
                        BarcodeScannerCard(
                            onBarcodeDetected = { code ->
                                onAction(NutritionAction.UpdateBarcode(code))
                                onAction(NutritionAction.LookupBarcode)
                                showScanner = false
                            },
                            onClose = { showScanner = false }
                        )
                    }
                }
                LookupTab.MANUAL -> {
                    Text(stringResource(R.string.add_food_manually), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.manual_help),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text(stringResource(R.string.food_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualBrand,
                        onValueChange = { manualBrand = it },
                        label = { Text(stringResource(R.string.brand_optional)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = manualCalories,
                            onValueChange = { manualCalories = it },
                            label = { Text("kcal") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = manualProtein,
                            onValueChange = { manualProtein = it },
                            label = { Text(stringResource(R.string.protein_g)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = manualCarbs,
                            onValueChange = { manualCarbs = it },
                            label = { Text(stringResource(R.string.carbs_g)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = manualFat,
                            onValueChange = { manualFat = it },
                            label = { Text(stringResource(R.string.fat_g)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = manualFiber,
                            onValueChange = { manualFiber = it },
                            label = { Text(stringResource(R.string.fiber_g)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = manualSugar,
                            onValueChange = { manualSugar = it },
                            label = { Text(stringResource(R.string.sugar_g)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = manualSodiumMg,
                            onValueChange = { manualSodiumMg = it },
                            label = { Text(stringResource(R.string.sodium_mg)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = manualPotassiumMg,
                            onValueChange = { manualPotassiumMg = it },
                            label = { Text(stringResource(R.string.potassium_mg)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = manualCalciumMg,
                            onValueChange = { manualCalciumMg = it },
                            label = { Text(stringResource(R.string.calcium_mg)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = manualIronMg,
                            onValueChange = { manualIronMg = it },
                            label = { Text(stringResource(R.string.iron_mg)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            fun parseOrZero(raw: String): Double = raw.toDoubleOrNull() ?: 0.0
                            val manualFood = FoodItem(
                                id = "manual-${System.currentTimeMillis()}",
                                source = FoodSource.MANUAL,
                                description = manualName.trim(),
                                brand = manualBrand.trim(),
                                caloriesPer100g = parseOrZero(manualCalories),
                                proteinPer100g = parseOrZero(manualProtein),
                                carbsPer100g = parseOrZero(manualCarbs),
                                fatPer100g = parseOrZero(manualFat),
                                fiberPer100g = parseOrZero(manualFiber),
                                sugarPer100g = parseOrZero(manualSugar),
                                sodiumMgPer100g = parseOrZero(manualSodiumMg),
                                potassiumMgPer100g = parseOrZero(manualPotassiumMg),
                                calciumMgPer100g = parseOrZero(manualCalciumMg),
                                ironMgPer100g = parseOrZero(manualIronMg)
                            )
                            onAction(NutritionAction.AddFood(manualFood))
                            manualName = ""
                            manualBrand = ""
                            manualCalories = ""
                            manualProtein = ""
                            manualCarbs = ""
                            manualFat = ""
                            manualFiber = ""
                            manualSugar = ""
                            manualSodiumMg = ""
                            manualPotassiumMg = ""
                            manualCalciumMg = ""
                            manualIronMg = ""
                        },
                        enabled = manualName.trim().isNotBlank()
                    ) {
                        Text(stringResource(R.string.add_manual_food))
                    }
                }
                LookupTab.HISTORY -> {
                    if (previouslyEatenFoods.isNotEmpty()) {
                        Text(stringResource(R.string.previously_eaten), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.previously_eaten_help), style = MaterialTheme.typography.bodySmall)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            previouslyEatenFoods.forEach { food ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(food.description, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "${food.caloriesPer100g.roundToInt()} kcal | P ${food.proteinPer100g.roundToInt()} | C ${food.carbsPer100g.roundToInt()} | F ${food.fatPer100g.roundToInt()}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        TextButton(
                                            onClick = { onAction(NutritionAction.AddFood(food)) },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text(stringResource(R.string.use_food))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(stringResource(R.string.no_foods_for_day))
                    }
                }
                LookupTab.RECIPES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAction(NutritionAction.ShowAddRecipeDialog) }) {
                            Text("Create New Recipe")
                        }
                        if (state.recipes.isNotEmpty()) {
                            Text("My Recipes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.recipes.forEach { recipe ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(recipe.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)\n                                            Text("${recipe.ingredients.size} ingredient(s)", style = MaterialTheme.typography.bodySmall)\n                                            if (recipe.notes.isNotBlank()) {\n                                                Text(recipe.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                                            }\n                                            TextButton(\n                                                onClick = { onAction(NutritionAction.UseRecipe(recipe)) },\n                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)\n                                            ) {\n                                                Text(stringResource(R.string.use_food))\n                                            }\n                                        }\n                                    }\n                                }\n                            }\n                        } else {\n                            Text("No recipes yet. Create one to get started!", style = MaterialTheme.typography.bodySmall)\n                        }\n                    }\n                }
            }

            if (lookupTab == LookupTab.NAME || lookupTab == LookupTab.BARCODE) {
                if (state.searchResults.isNotEmpty()) Text(stringResource(R.string.results))
                state.searchResults.forEach { food ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(food.description, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                val sourceLabel = when (food.source) {
                                    FoodSource.USDA -> stringResource(R.string.source_usda)
                                    FoodSource.FATSECRET -> stringResource(R.string.source_fatsecret)
                                    FoodSource.OPENFOODFACTS -> stringResource(R.string.source_openfoodfacts)
                                    FoodSource.MANUAL -> stringResource(R.string.source_manual)
                                }
                                val sourceAndBrand = if (food.brand.isNotBlank()) "$sourceLabel - ${food.brand}" else sourceLabel
                                Text(sourceAndBrand, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "${food.caloriesPer100g.roundToInt()} kcal | P ${food.proteinPer100g.roundToInt()} | C ${food.carbsPer100g.roundToInt()} | F ${food.fatPer100g.roundToInt()}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = { onAction(NutritionAction.AddFood(food)) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(stringResource(R.string.add))
                            }
                        }
                    }
                }
            }
        }
    }
    
    state.foodToAdd?.let { selectedFood ->
        AlertDialog(
            onDismissRequest = { onAction(NutritionAction.DismissAddFoodDialog) },
            title = { Text(stringResource(R.string.add_item_again, selectedFood.description)) },
            text = {
                OutlinedTextField(
                    value = state.addFoodGramsInput,
                    onValueChange = { onAction(NutritionAction.UpdateAddFoodGrams(it)) },
                    label = { Text(stringResource(R.string.amount_grams)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAction(NutritionAction.ConfirmAddFood(selectedFood, state.addFoodGramsInput))
                    }
                ) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(NutritionAction.DismissAddFoodDialog) }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (state.showAddRecipeDialog) {
        RecipeBuilderDialog(state = state, onAction = onAction)
    }
}

@Composable
private fun RecipeBuilderDialog(state: NutritionUiState, onAction: (NutritionAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(NutritionAction.DismissAddRecipeDialog) },
        title = { Text("Create Recipe") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.recipeNameInput,
                    onValueChange = { onAction(NutritionAction.UpdateRecipeName(it)) },
                    label = { Text("Recipe Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.recipeNotesInput,
                    onValueChange = { onAction(NutritionAction.UpdateRecipeNotes(it)) },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                
                Text("Ingredients (${state.recipeIngredientsInput.size})", style = MaterialTheme.typography.titleSmall)
                
                if (state.recipeIngredientsInput.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        state.recipeIngredientsInput.forEachIndexed { index, ingredient ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ingredient.foodDescription, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text("${ingredient.gramsUsed.roundToInt()}g", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { onAction(NutritionAction.RemoveRecipeIngredient(index)) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                
                Button(
                    onClick = { onAction(NutritionAction.UpdateAddGrams("100")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Ingredient from History")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAction(NutritionAction.SaveRecipe) }) {
                Text("Save Recipe")
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(NutritionAction.DismissAddRecipeDialog) }) {
                Text("Cancel")
            }
        },
        modifier = Modifier.fillMaxWidth(0.9f)
    )
}

@Composable
private fun BarcodeScannerCard(
    onBarcodeDetected: (String) -> Unit,
    onClose: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.point_camera_barcode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                BarcodeCameraPreview(onBarcodeDetected = onBarcodeDetected)
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close_scanner)) }
        }
    }
}

@Composable
private fun BarcodeCameraPreview(onBarcodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val consumedResult = remember { AtomicBoolean(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraExecutor = Executors.newSingleThreadExecutor()
            val scanner = BarcodeScanning.getClient()

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = CameraPreview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || consumedResult.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            if (!consumedResult.get()) {
                                val value = barcodes.firstNotNullOfOrNull { barcode ->
                                    if (barcode.format == Barcode.FORMAT_UPC_A ||
                                        barcode.format == Barcode.FORMAT_UPC_E ||
                                        barcode.format == Barcode.FORMAT_EAN_8 ||
                                        barcode.format == Barcode.FORMAT_EAN_13
                                    ) {
                                        barcode.rawValue
                                    } else {
                                        barcode.rawValue
                                    }
                                }
                                if (!value.isNullOrBlank()) {
                                    if (consumedResult.compareAndSet(false, true)) {
                                        onBarcodeDetected(value)
                                    }
                                }
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@Composable
private fun ConsumedFoodsSection(
    entries: List<ConsumedFoodEntry>,
    headerText: String,
    onAction: (NutritionAction) -> Unit
) {
    var entryToReAdd by remember { mutableStateOf<ConsumedFoodEntry?>(null) }
    var reAddGramsInput by rememberSaveable { mutableStateOf("") }
    var entryToModify by remember { mutableStateOf<ConsumedFoodEntry?>(null) }
    var modifyGramsInput by rememberSaveable { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(headerText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (entries.isEmpty()) Text(stringResource(R.string.no_foods_for_day))
            entries.forEach { entry ->
                val kcal = entry.caloriesPer100g * entry.gramsConsumed / 100.0
                val protein = entry.proteinPer100g * entry.gramsConsumed / 100.0
                val carbs = entry.carbsPer100g * entry.gramsConsumed / 100.0
                val fat = entry.fatPer100g * entry.gramsConsumed / 100.0
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(entry.description, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(
                                R.string.entry_compact_summary,
                                entry.gramsConsumed.roundToInt(),
                                kcal.roundToInt(),
                                protein.roundToInt(),
                                carbs.roundToInt(),
                                fat.roundToInt()
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    entryToModify = entry
                                    modifyGramsInput = entry.gramsConsumed.roundToInt().toString()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) { Text(stringResource(R.string.modify)) }
                            TextButton(
                                onClick = {
                                    entryToReAdd = entry
                                    reAddGramsInput = entry.gramsConsumed.roundToInt().toString()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) { Text(stringResource(R.string.re_add)) }
                            TextButton({ onAction(NutritionAction.RemoveEntry(entry.id)) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text(stringResource(R.string.remove)) }
                        }
                    }
                }
            }
        }
    }
    entryToReAdd?.let { selected ->
        AlertDialog(
            onDismissRequest = { entryToReAdd = null },
            title = { Text(stringResource(R.string.add_item_again, selected.description)) },
            text = {
                OutlinedTextField(
                    value = reAddGramsInput,
                    onValueChange = { reAddGramsInput = it },
                    label = { Text(stringResource(R.string.amount_grams)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAction(NutritionAction.ReAddEntry(selected.id, reAddGramsInput))
                        entryToReAdd = null
                    }
                ) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { entryToReAdd = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    entryToModify?.let { selected ->
        AlertDialog(
            onDismissRequest = { entryToModify = null },
            title = { Text(stringResource(R.string.modify_item_amount, selected.description)) },
            text = {
                OutlinedTextField(
                    value = modifyGramsInput,
                    onValueChange = { modifyGramsInput = it },
                    label = { Text(stringResource(R.string.amount_grams)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAction(NutritionAction.ModifyEntryGrams(selected.id, modifyGramsInput))
                        entryToModify = null
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { entryToModify = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun DailyMicronutrientsSection(
    fiberGrams: Double,
    sugarGrams: Double,
    sodiumMg: Double,
    potassiumMg: Double,
    calciumMg: Double,
    ironMg: Double
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.daily_micronutrients), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MicronutrientProgressRow(stringResource(R.string.micronutrient_fiber), "g", 30.0, fiberGrams)
            MicronutrientProgressRow(stringResource(R.string.micronutrient_sugar), "g", 50.0, sugarGrams)
            MicronutrientProgressRow(stringResource(R.string.micronutrient_sodium), "mg", 2300.0, sodiumMg)
            MicronutrientProgressRow(stringResource(R.string.micronutrient_potassium), "mg", 3500.0, potassiumMg)
            MicronutrientProgressRow(stringResource(R.string.micronutrient_calcium), "mg", 1000.0, calciumMg)
            MicronutrientProgressRow(stringResource(R.string.micronutrient_iron), "mg", 18.0, ironMg)
            Text(
                stringResource(R.string.micronutrient_targets_note),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DiaryHealthBars(
    usedKcal: Double,
    targetKcal: Int,
    usedProtein: Double,
    targetProtein: Double,
    usedCarbs: Double,
    targetCarbs: Double,
    usedFat: Double,
    targetFat: Double
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBarRow(
                label = stringResource(R.string.calories),
                valueText = stringResource(R.string.calories_progress, usedKcal.roundToInt(), targetKcal),
                progress = progressOf(usedKcal, targetKcal.toDouble()),
                color = MaterialTheme.colorScheme.primary
            )
            StatBarRow(
                label = stringResource(R.string.protein),
                valueText = stringResource(R.string.protein_progress, usedProtein.roundToInt(), targetProtein.roundToInt()),
                progress = progressOf(usedProtein, targetProtein),
                color = Color(0xFF2E7D32)
            )
            StatBarRow(
                label = stringResource(R.string.carbs),
                valueText = stringResource(R.string.carbs_progress, usedCarbs.roundToInt(), targetCarbs.roundToInt()),
                progress = progressOf(usedCarbs, targetCarbs),
                color = Color(0xFFEF6C00)
            )
            StatBarRow(
                label = stringResource(R.string.fat),
                valueText = stringResource(R.string.fat_progress, usedFat.roundToInt(), targetFat.roundToInt()),
                progress = progressOf(usedFat, targetFat),
                color = Color(0xFF1565C0)
            )
            Text(stringResource(R.string.remaining_kcal, (targetKcal - usedKcal).roundToInt()), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatBarRow(
    label: String,
    valueText: String,
    progress: Float,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
            Text(valueText, style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = color.copy(alpha = 0.24f)
        )
    }
}

@Composable
private fun MicronutrientProgressRow(
    label: String,
    unit: String,
    target: Double,
    current: Double
) {
    val pct = ((current / target) * 100.0).coerceAtLeast(0.0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${current.roundToInt()} $unit / ${target.roundToInt()} $unit (${pct.roundToInt()}%)",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

data class FoodItem(
    val id: String,
    val source: FoodSource,
    val description: String,
    val brand: String,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double = 0.0,
    val sugarPer100g: Double = 0.0,
    val sodiumMgPer100g: Double = 0.0,
    val potassiumMgPer100g: Double = 0.0,
    val calciumMgPer100g: Double = 0.0,
    val ironMgPer100g: Double = 0.0
)

data class ConsumedFoodEntry(
    val id: String,
    val date: String,
    val foodId: String,
    val source: FoodSource,
    val description: String,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val gramsConsumed: Double,
    val fiberPer100g: Double = 0.0,
    val sugarPer100g: Double = 0.0,
    val sodiumMgPer100g: Double = 0.0,
    val potassiumMgPer100g: Double = 0.0,
    val calciumMgPer100g: Double = 0.0,
    val ironMgPer100g: Double = 0.0
)

data class RecipeIngredient(
    val foodId: String,
    val foodSource: FoodSource,
    val foodDescription: String,
    val gramsUsed: Double
)

data class Recipe(
    val id: String,
    val name: String,
    val ingredients: List<RecipeIngredient>,
    val createdAtEpochMs: Long,
    val notes: String = ""
)

private fun FoodItem.nutrientsLikelyMissing(): Boolean =
    (proteinPer100g == 0.0 && carbsPer100g == 0.0 && fatPer100g == 0.0) ||
        (fiberPer100g == 0.0 && sugarPer100g == 0.0 && sodiumMgPer100g == 0.0 &&
            potassiumMgPer100g == 0.0 && calciumMgPer100g == 0.0 && ironMgPer100g == 0.0)

private fun ConsumedFoodEntry.nutrientsLikelyMissing(): Boolean =
    (proteinPer100g == 0.0 && carbsPer100g == 0.0 && fatPer100g == 0.0) ||
        (fiberPer100g == 0.0 && sugarPer100g == 0.0 && sodiumMgPer100g == 0.0 &&
            potassiumMgPer100g == 0.0 && calciumMgPer100g == 0.0 && ironMgPer100g == 0.0)

private fun ConsumedFoodEntry.asFoodItem(): FoodItem = FoodItem(
    id = foodId,
    source = source,
    description = description,
    brand = "",
    caloriesPer100g = caloriesPer100g,
    proteinPer100g = proteinPer100g,
    carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g,
    fiberPer100g = fiberPer100g,
    sugarPer100g = sugarPer100g,
    sodiumMgPer100g = sodiumMgPer100g,
    potassiumMgPer100g = potassiumMgPer100g,
    calciumMgPer100g = calciumMgPer100g,
    ironMgPer100g = ironMgPer100g
)

data class MacroTargets(val proteinGrams: Double, val carbsGrams: Double, val fatGrams: Double)

enum class Sex(val label: String) { MALE("Male"), FEMALE("Female"), OTHER("Other") }
enum class ActivityLevel(val label: String, val multiplier: Double) { LOW("Low", 1.2), MODERATE("Moderate", 1.55), HIGH("High", 1.725) }
enum class Goal(val label: String, val calorieAdjustment: Int) { KEEP_MUSCLE_WEIGHT_LOSS("Keep muscle, lose fat", -300), MAINTAIN("Maintain", 0), BULK_MUSCLE("Bulk muscle", 300) }

data class UserProfile(
    val weightKg: Double,
    val heightCm: Double,
    val age: Int,
    val sex: Sex,
    val activityLevel: ActivityLevel,
    val goal: Goal,
    val recommendedCalories: Int,
    val macroTargets: MacroTargets
)

data class NutritionUiState(
    val weightKgInput: String = "",
    val heightCmInput: String = "",
    val ageInput: String = "",
    val sex: Sex = Sex.MALE,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: Goal = Goal.MAINTAIN,
    val profile: UserProfile? = null,
    val query: String = "",
    val barcodeInput: String = "",
    val addGramsInput: String = "100",
    val searchResults: List<FoodItem> = emptyList(),
    val consumedEntries: List<ConsumedFoodEntry> = emptyList(),
    val selectedDate: String = LocalDate.now().toString(),
    val isLoading: Boolean = false,
    val isLoadingBarcode: Boolean = false,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val message: String? = null,
    val foodToAdd: FoodItem? = null,
    val addFoodGramsInput: String = "100",
    val recipes: List<Recipe> = emptyList(),
    val recipeNameInput: String = "",
    val recipeNotesInput: String = "",
    val recipeIngredientsInput: List<RecipeIngredient> = emptyList(),
    val recipeIngredientQuery: String = "",
    val showAddRecipeDialog: Boolean = false,
    val showNewIngredientSearch: Boolean = false,
    val recipeIngredientGramsInput: String = "100"
)

sealed interface NutritionAction {
    data class UpdateWeight(val value: String) : NutritionAction
    data class UpdateHeight(val value: String) : NutritionAction
    data class UpdateAge(val value: String) : NutritionAction
    data class UpdateSex(val value: Sex) : NutritionAction
    data class UpdateActivity(val value: ActivityLevel) : NutritionAction
    data class UpdateGoal(val value: Goal) : NutritionAction
    data class UpdateLanguage(val value: AppLanguage) : NutritionAction
    data object SaveProfile : NutritionAction
    data class UpdateQuery(val value: String) : NutritionAction
    data class UpdateBarcode(val value: String) : NutritionAction
    data class UpdateAddGrams(val value: String) : NutritionAction
    data object SearchFoods : NutritionAction
    data object LookupBarcode : NutritionAction
    data object PreviousDay : NutritionAction
    data object NextDay : NutritionAction
    data object PreviousWeek : NutritionAction
    data object NextWeek : NutritionAction
    data object Today : NutritionAction
    data class AddFood(val food: FoodItem) : NutritionAction
    data class ConfirmAddFood(val food: FoodItem, val grams: String) : NutritionAction
    data object DismissAddFoodDialog : NutritionAction
    data class UpdateAddFoodGrams(val value: String) : NutritionAction
    data class ModifyEntryGrams(val entryId: String, val gramsInput: String) : NutritionAction
    data class ReAddEntry(val entryId: String, val gramsInput: String) : NutritionAction
    data class RemoveEntry(val entryId: String) : NutritionAction
    data class ExportBackup(val uri: Uri) : NutritionAction
    data class ImportBackup(val uri: Uri) : NutritionAction
    data object ShowAddRecipeDialog : NutritionAction
    data object DismissAddRecipeDialog : NutritionAction
    data class UpdateRecipeName(val value: String) : NutritionAction
    data class UpdateRecipeNotes(val value: String) : NutritionAction
    data class UpdateRecipeIngredientQuery(val value: String) : NutritionAction
    data class UpdateRecipeIngredientGrams(val value: String) : NutritionAction
    data class AddRecipeIngredient(val food: FoodItem) : NutritionAction
    data class RemoveRecipeIngredient(val index: Int) : NutritionAction
    data object SaveRecipe : NutritionAction
    data class UseRecipe(val recipe: Recipe) : NutritionAction
}
class NutritionViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = NutritionRepository(application.applicationContext)
    var uiState by mutableStateOf(NutritionUiState())
        private set

    init {
        loadInitialState()
    }

    fun onAction(action: NutritionAction) {
        when (action) {
            is NutritionAction.UpdateWeight -> uiState = uiState.copy(weightKgInput = action.value)
            is NutritionAction.UpdateHeight -> uiState = uiState.copy(heightCmInput = action.value)
            is NutritionAction.UpdateAge -> uiState = uiState.copy(ageInput = action.value)
            is NutritionAction.UpdateSex -> uiState = uiState.copy(sex = action.value)
            is NutritionAction.UpdateActivity -> uiState = uiState.copy(activityLevel = action.value)
            is NutritionAction.UpdateGoal -> uiState = uiState.copy(goal = action.value)
            is NutritionAction.UpdateLanguage -> updateLanguage(action.value)
            is NutritionAction.UpdateQuery -> uiState = uiState.copy(query = action.value)
            is NutritionAction.UpdateBarcode -> uiState = uiState.copy(barcodeInput = action.value)
            is NutritionAction.UpdateAddGrams -> uiState = uiState.copy(addGramsInput = action.value)
            NutritionAction.SaveProfile -> saveProfile()
            NutritionAction.SearchFoods -> searchFoods()
            NutritionAction.LookupBarcode -> lookupBarcode()
            NutritionAction.PreviousDay -> shiftSelectedDay(-1)
            NutritionAction.NextDay -> shiftSelectedDay(1)
            NutritionAction.PreviousWeek -> shiftSelectedDay(-7)
            NutritionAction.NextWeek -> shiftSelectedDay(7)
            NutritionAction.Today -> uiState = uiState.copy(selectedDate = LocalDate.now().toString())
            is NutritionAction.AddFood -> uiState = uiState.copy(foodToAdd = action.food, addFoodGramsInput = "100")
            is NutritionAction.ConfirmAddFood -> confirmAddFood(action.food, action.grams)
            NutritionAction.DismissAddFoodDialog -> uiState = uiState.copy(foodToAdd = null, addFoodGramsInput = "100")
            is NutritionAction.UpdateAddFoodGrams -> uiState = uiState.copy(addFoodGramsInput = action.value)
            is NutritionAction.ModifyEntryGrams -> modifyEntryGrams(action.entryId, action.gramsInput)
            is NutritionAction.ReAddEntry -> reAddEntry(action.entryId, action.gramsInput)
            is NutritionAction.RemoveEntry -> removeEntry(action.entryId)
            is NutritionAction.ExportBackup -> exportBackup(action.uri)
            is NutritionAction.ImportBackup -> importBackup(action.uri)
            NutritionAction.ShowAddRecipeDialog -> uiState = uiState.copy(showAddRecipeDialog = true, recipeNameInput = "", recipeNotesInput = "", recipeIngredientsInput = emptyList(), recipeIngredientQuery = "")
            NutritionAction.DismissAddRecipeDialog -> uiState = uiState.copy(showAddRecipeDialog = false, recipeNameInput = "", recipeNotesInput = "", recipeIngredientsInput = emptyList())
            is NutritionAction.UpdateRecipeName -> uiState = uiState.copy(recipeNameInput = action.value)
            is NutritionAction.UpdateRecipeNotes -> uiState = uiState.copy(recipeNotesInput = action.value)
            is NutritionAction.UpdateRecipeIngredientQuery -> uiState = uiState.copy(recipeIngredientQuery = action.value)
            is NutritionAction.UpdateRecipeIngredientGrams -> uiState = uiState.copy(recipeIngredientGramsInput = action.value)
            is NutritionAction.AddRecipeIngredient -> addRecipeIngredient(action.food)
            is NutritionAction.RemoveRecipeIngredient -> uiState = uiState.copy(recipeIngredientsInput = uiState.recipeIngredientsInput.filterIndexed { i, _ -> i != action.index })
            NutritionAction.SaveRecipe -> saveRecipe()
            is NutritionAction.UseRecipe -> uiState = uiState.copy(foodToAdd = FoodItem(id = action.recipe.id, source = FoodSource.RECIPE, description = action.recipe.name, brand = "", caloriesPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 0.0), addFoodGramsInput = "100")
        }
    }

    fun clearMessage() {
        uiState = uiState.copy(message = null)
    }

    private fun shiftSelectedDay(deltaDays: Long) {
        val current = runCatching { LocalDate.parse(uiState.selectedDate) }.getOrElse { LocalDate.now() }
        uiState = uiState.copy(selectedDate = current.plusDays(deltaDays).toString())
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            val profile = repo.loadProfile()
            val language = repo.loadLanguage()
            applyLanguage(language)
            val loaded = repo.loadConsumedEntries()
            val enriched = repo.enrichEntriesIfMissingMacros(loaded, BuildConfig.USDA_API_KEY)
            if (enriched != loaded) repo.saveConsumedEntries(enriched)
            val recipes = repo.loadRecipes()
            uiState = uiState.copy(
                profile = profile,
                consumedEntries = enriched,
                recipes = recipes,
                selectedDate = LocalDate.now().toString(),
                weightKgInput = profile?.weightKg?.toString().orEmpty(),
                heightCmInput = profile?.heightCm?.toString().orEmpty(),
                ageInput = profile?.age?.toString().orEmpty(),
                sex = profile?.sex ?: uiState.sex,
                activityLevel = profile?.activityLevel ?: uiState.activityLevel,
                goal = profile?.goal ?: uiState.goal,
                language = language
            )
        }
    }

    private fun updateLanguage(language: AppLanguage) {
        applyLanguage(language)
        uiState = uiState.copy(language = language)
        viewModelScope.launch {
            repo.saveLanguage(language)
        }
    }

    private fun applyLanguage(language: AppLanguage) {
        val locales = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun saveProfile() {
        val weight = uiState.weightKgInput.toDoubleOrNull()
        val height = uiState.heightCmInput.toDoubleOrNull()
        val age = uiState.ageInput.toIntOrNull()
        if (weight == null || height == null || age == null) {
            uiState = uiState.copy(message = "Please enter valid weight, height, and age.")
            return
        }

        val recommended = calculateRecommendedCalories(weight, height, age, uiState.sex, uiState.activityLevel, uiState.goal)
        val macroTargets = calculateMacroTargets(recommended, weight, uiState.goal)
        val profile = UserProfile(weight, height, age, uiState.sex, uiState.activityLevel, uiState.goal, recommended, macroTargets)

        viewModelScope.launch {
            repo.saveProfile(profile)
            uiState = uiState.copy(profile = profile, message = "Calorie and macro plan saved.")
        }
    }

    private fun searchFoods() {
        if (uiState.query.isBlank()) {
            uiState = uiState.copy(message = "Type a food first.")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val query = uiState.query.trim()
            val cached = repo.searchCachedFoodsByQuery(query)
            if (cached.size >= 8) {
                uiState = uiState.copy(
                    searchResults = cached,
                    isLoading = false,
                    message = "Showing cached results."
                )
                return@launch
            }

            if (BuildConfig.USDA_API_KEY.isBlank()) {
                if (cached.isNotEmpty()) {
                    uiState = uiState.copy(
                        searchResults = cached,
                        isLoading = false,
                        message = "USDA API key missing. Showing cached results."
                    )
                } else {
                    uiState = uiState.copy(
                        isLoading = false,
                        message = "USDA_API_KEY is missing and no cached matches found."
                    )
                }
                return@launch
            }

            runCatching { repo.searchUsdaFoods(query, BuildConfig.USDA_API_KEY) }
                .onSuccess { remote ->
                    val merged = (cached + remote).distinctBy { "${it.source}:${it.id}" }
                    uiState = uiState.copy(searchResults = merged, isLoading = false)
                }
                .onFailure {
                    val fallback = if (cached.isNotEmpty()) cached else uiState.searchResults
                    uiState = uiState.copy(
                        searchResults = fallback,
                        isLoading = false,
                        message = if (cached.isNotEmpty()) {
                            "USDA search failed. Showing cached results."
                        } else {
                            "USDA search failed."
                        }
                    )
                }
        }
    }

    private fun lookupBarcode() {
        val barcode = uiState.barcodeInput.filter { it.isDigit() }
        if (barcode.length < 8) {
            uiState = uiState.copy(message = "Enter a valid barcode number.")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(isLoadingBarcode = true)
            runCatching {
                repo.lookupOpenFoodFactsBarcode(barcode)
            }.onSuccess { food ->
                uiState = uiState.copy(
                    searchResults = listOf(food),
                    isLoadingBarcode = false,
                    message = "Barcode matched: ${food.description}"
                )
            }.onFailure {
                uiState = uiState.copy(
                    isLoadingBarcode = false,
                    message = "OpenFoodFacts lookup failed: ${(it.message ?: "unknown error").take(180)}"
                )
            }
        }
    }

    private fun confirmAddFood(food: FoodItem, gramsInput: String) {
        val grams = gramsInput.toDoubleOrNull()
        if (grams == null || grams <= 0.0) {
            uiState = uiState.copy(message = "Enter a valid grams amount (e.g. 125).")
            return
        }

        viewModelScope.launch {
            val resolvedFood = repo.resolveFoodWithCompleteMacros(food, BuildConfig.USDA_API_KEY)
            val entry = ConsumedFoodEntry(
                id = "${resolvedFood.source.name}-${resolvedFood.id}-${System.currentTimeMillis()}",
                date = uiState.selectedDate,
                foodId = resolvedFood.id,
                source = resolvedFood.source,
                description = resolvedFood.description,
                caloriesPer100g = resolvedFood.caloriesPer100g,
                proteinPer100g = resolvedFood.proteinPer100g,
                carbsPer100g = resolvedFood.carbsPer100g,
                fatPer100g = resolvedFood.fatPer100g,
                gramsConsumed = grams,
                fiberPer100g = resolvedFood.fiberPer100g,
                sugarPer100g = resolvedFood.sugarPer100g,
                sodiumMgPer100g = resolvedFood.sodiumMgPer100g,
                potassiumMgPer100g = resolvedFood.potassiumMgPer100g,
                calciumMgPer100g = resolvedFood.calciumMgPer100g,
                ironMgPer100g = resolvedFood.ironMgPer100g
            )
            val all = repo.loadConsumedEntries().toMutableList()
            all.add(entry)
            repo.saveConsumedEntries(all)
            uiState = uiState.copy(
                consumedEntries = all,
                searchResults = emptyList(),
                query = "",
                barcodeInput = "",
                foodToAdd = null,
                addFoodGramsInput = "100",
                message = "Food added with ${grams.roundToInt()}g."
            )
        }
    }

    private fun modifyEntryGrams(entryId: String, gramsInput: String) {
        val grams = gramsInput.toDoubleOrNull()
        if (grams == null || grams <= 0.0) {
            uiState = uiState.copy(message = "Enter a valid grams amount.")
            return
        }
        viewModelScope.launch {
            val all = repo.loadConsumedEntries().toMutableList()
            val idx = all.indexOfFirst { it.id == entryId }
            if (idx == -1) return@launch
            all[idx] = all[idx].copy(gramsConsumed = grams)
            repo.saveConsumedEntries(all)
            uiState = uiState.copy(
                consumedEntries = all,
                message = "Entry updated to ${grams.roundToInt()}g."
            )
        }
    }

    private fun removeEntry(entryId: String) {
        viewModelScope.launch {
            val all = repo.loadConsumedEntries().filterNot { it.id == entryId }
            repo.saveConsumedEntries(all)
            uiState = uiState.copy(consumedEntries = all)
        }
    }

    private fun reAddEntry(entryId: String, gramsInput: String) {
        val grams = gramsInput.toDoubleOrNull()
        if (grams == null || grams <= 0.0) {
            uiState = uiState.copy(message = "Enter a valid grams amount to re-add.")
            return
        }

        viewModelScope.launch {
            val all = repo.loadConsumedEntries().toMutableList()
            val original = all.firstOrNull { it.id == entryId }
            if (original == null) {
                uiState = uiState.copy(message = "Could not find that food entry.")
                return@launch
            }

            val copy = original.copy(
                id = "${original.source.name}-${original.foodId}-${System.currentTimeMillis()}",
                date = uiState.selectedDate,
                gramsConsumed = grams
            )
            all.add(copy)
            repo.saveConsumedEntries(all)
            uiState = uiState.copy(
                consumedEntries = all,
                message = "Added again with ${grams.roundToInt()}g."
            )
        }
    }

    private fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            uiState = uiState.copy(isExporting = true)
            runCatching {
                repo.exportBackupToUri(uri)
            }.onSuccess { bytesWritten ->
                uiState = uiState.copy(
                    isExporting = false,
                    message = "Backup exported (${bytesWritten} bytes)."
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    isExporting = false,
                    message = "Export failed: ${(error.message ?: "unknown error").take(180)}"
                )
            }
        }
    }

    private fun importBackup(uri: Uri) {
        viewModelScope.launch {
            uiState = uiState.copy(isImporting = true)
            runCatching {
                repo.importBackupFromUri(uri)
            }.onSuccess {
                val profile = repo.loadProfile()
                val entries = repo.loadConsumedEntries()
                uiState = uiState.copy(
                    isImporting = false,
                    profile = profile,
                    consumedEntries = entries,
                    weightKgInput = profile?.weightKg?.toString().orEmpty(),
                    heightCmInput = profile?.heightCm?.toString().orEmpty(),
                    ageInput = profile?.age?.toString().orEmpty(),
                    sex = profile?.sex ?: uiState.sex,
                    activityLevel = profile?.activityLevel ?: uiState.activityLevel,
                    goal = profile?.goal ?: uiState.goal,
                    message = "Backup imported."
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    isImporting = false,
                    message = "Import failed: ${(error.message ?: "unknown error").take(180)}"
                )
            }
        }
    }

    private fun addRecipeIngredient(food: FoodItem) {
        val grams = uiState.recipeIngredientGramsInput.toDoubleOrNull()
        if (grams == null || grams <= 0.0) {
            uiState = uiState.copy(message = "Enter a valid grams amount for ingredient.")
            return
        }
        
        val ingredient = RecipeIngredient(
            foodId = food.id,
            foodSource = food.source,
            foodDescription = food.description,
            gramsUsed = grams
        )
        
        val updated = uiState.recipeIngredientsInput + ingredient
        uiState = uiState.copy(
            recipeIngredientsInput = updated,
            recipeIngredientQuery = "",
            recipeIngredientGramsInput = "100",
            showNewIngredientSearch = false,
            message = "Added ${food.description} to recipe."
        )
    }

    private fun saveRecipe() {
        val name = uiState.recipeNameInput.trim()
        if (name.isBlank()) {
            uiState = uiState.copy(message = "Enter a recipe name.")
            return
        }
        
        if (uiState.recipeIngredientsInput.isEmpty()) {
            uiState = uiState.copy(message = "Add at least one ingredient to the recipe.")
            return
        }
        
        viewModelScope.launch {
            val recipe = Recipe(
                id = "recipe-${System.currentTimeMillis()}",
                name = name,
                ingredients = uiState.recipeIngredientsInput,
                createdAtEpochMs = System.currentTimeMillis(),
                notes = uiState.recipeNotesInput.trim()
            )
            
            val allRecipes = (uiState.recipes + recipe).toMutableList()
            repo.saveRecipes(allRecipes)
            
            uiState = uiState.copy(
                recipes = allRecipes,
                showAddRecipeDialog = false,
                recipeNameInput = "",
                recipeNotesInput = "",
                recipeIngredientsInput = emptyList(),
                message = "Recipe '$name' saved successfully."
            )
        }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = NutritionViewModel(app) as T
        }
    }
}

private fun LocalDate.startOfWeekMonday(): LocalDate {
    val offset = (dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    return minusDays(offset.toLong())
}

private fun progressOf(current: Double, target: Double): Float {
    if (target <= 0.0) return 0f
    return (current / target).coerceIn(0.0, 1.0).toFloat()
}

private fun calculateRecommendedCalories(weightKg: Double, heightCm: Double, age: Int, sex: Sex, activityLevel: ActivityLevel, goal: Goal): Int {
    val bmr = when (sex) {
        Sex.MALE -> 10 * weightKg + 6.25 * heightCm - 5 * age + 5
        Sex.FEMALE -> 10 * weightKg + 6.25 * heightCm - 5 * age - 161
        Sex.OTHER -> 10 * weightKg + 6.25 * heightCm - 5 * age - 78
    }
    return (bmr * activityLevel.multiplier + goal.calorieAdjustment).roundToInt()
}

private fun calculateMacroTargets(calories: Int, weightKg: Double, goal: Goal): MacroTargets {
    val protein = weightKg * when (goal) {
        Goal.KEEP_MUSCLE_WEIGHT_LOSS -> 2.2
        Goal.MAINTAIN -> 1.8
        Goal.BULK_MUSCLE -> 1.8
    }
    val fat = weightKg * 0.8
    val carbs = max(0.0, calories - (protein * 4.0 + fat * 9.0)) / 4.0
    return MacroTargets(protein, carbs, fat)
}
class NutritionRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("nutrition_store", Context.MODE_PRIVATE)
    private var fatSecretToken: String? = null
    private var fatSecretTokenExpiryEpochMs: Long = 0

    suspend fun searchUsdaFoods(query: String, apiKey: String): List<FoodItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = URL("https://api.nal.usda.gov/fdc/v1/foods/search?query=$q&pageSize=25&api_key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
        }
        conn.connect()
        if (conn.responseCode !in 200..299) throw IllegalStateException("USDA API error ${conn.responseCode}")
        val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        conn.disconnect()
        val foods = parseUsdaFoods(JSONObject(response))
        cacheFoods(foods)
        foods
    }

    suspend fun searchCachedFoodsByQuery(query: String, limit: Int = 25): List<FoodItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val raw = prefs.getString("foods_cache_json", null) ?: return@withContext emptyList()
        val arr = JSONArray(raw)
        val results = mutableListOf<FoodItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val source = FoodSource.entries.firstOrNull { it.name == o.optString("source", FoodSource.USDA.name) } ?: FoodSource.USDA
            val id = o.optString("id", o.optLong("fdcId", -1L).toString())
            if (id.isBlank() || id == "-1") continue
            val description = o.optString("description")
            val brand = o.optString("brand")
            if (!description.contains(q, ignoreCase = true) && !brand.contains(q, ignoreCase = true)) continue
            results.add(
                FoodItem(
                    id = id,
                    source = source,
                    description = description,
                    brand = brand,
                    caloriesPer100g = o.optDouble("caloriesPer100g", o.optDouble("calories", 0.0)),
                    proteinPer100g = o.optDouble("proteinPer100g", 0.0),
                    carbsPer100g = o.optDouble("carbsPer100g", 0.0),
                    fatPer100g = o.optDouble("fatPer100g", 0.0),
                    fiberPer100g = o.optDouble("fiberPer100g", 0.0),
                    sugarPer100g = o.optDouble("sugarPer100g", 0.0),
                    sodiumMgPer100g = o.optDouble("sodiumMgPer100g", 0.0),
                    potassiumMgPer100g = o.optDouble("potassiumMgPer100g", 0.0),
                    calciumMgPer100g = o.optDouble("calciumMgPer100g", 0.0),
                    ironMgPer100g = o.optDouble("ironMgPer100g", 0.0)
                )
            )
        }
        return@withContext results
            .sortedWith(compareBy<FoodItem> { !it.description.startsWith(q, ignoreCase = true) }.thenBy { it.description.length })
            .take(limit)
    }

    suspend fun lookupOpenFoodFactsBarcode(barcode: String): FoodItem = withContext(Dispatchers.IO) {
        val clean = barcode.filter { it.isDigit() }
        val url = URL("https://world.openfoodfacts.org/api/v0/product/$clean.json")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { br -> br.readText() } } ?: ""
            conn.disconnect()
            throw IllegalStateException("HTTP ${conn.responseCode}: $err")
        }
        val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        conn.disconnect()
        val root = JSONObject(response)
        if (root.optInt("status", 0) != 1) {
            throw IllegalStateException("Barcode not found")
        }
        val product = root.optJSONObject("product") ?: throw IllegalStateException("Missing product details")
        val nutriments = product.optJSONObject("nutriments") ?: JSONObject()

        fun n(vararg keys: String): Double {
            return keys.firstNotNullOfOrNull { k -> parseFlexibleNumber(nutriments.opt(k)) } ?: 0.0
        }

        val food = FoodItem(
            id = clean,
            source = FoodSource.OPENFOODFACTS,
            description = product.optString("product_name", "").ifBlank { product.optString("generic_name", "Unknown food") },
            brand = product.optString("brands", ""),
            caloriesPer100g = n("energy-kcal_100g", "energy-kcal", "energy-kcal_value"),
            proteinPer100g = n("proteins_100g", "proteins"),
            carbsPer100g = n("carbohydrates_100g", "carbohydrates"),
            fatPer100g = n("fat_100g", "fat"),
            fiberPer100g = n("fiber_100g", "fiber"),
            sugarPer100g = n("sugars_100g", "sugars"),
            sodiumMgPer100g = n("sodium_100g", "sodium").let { if (it > 0.0) it else n("salt_100g", "salt") * 393.4 },
            potassiumMgPer100g = n("potassium_100g", "potassium"),
            calciumMgPer100g = n("calcium_100g", "calcium"),
            ironMgPer100g = n("iron_100g", "iron")
        )
        cacheFoods(listOf(food))
        food
    }

    suspend fun lookupFatSecretBarcode(barcode: String, clientId: String, clientSecret: String): FoodItem = withContext(Dispatchers.IO) {
        val token = getFatSecretAccessToken(clientId, clientSecret)
        val normalized = normalizeToGtin13(barcode)
        val b = URLEncoder.encode(normalized, StandardCharsets.UTF_8.name())

        val v2Result = runCatching {
            val response = fatSecretGetJson(
                "https://platform.fatsecret.com/rest/food/barcode/find-by-id/v2?barcode=$b&format=json&region=US&language=en",
                token
            )
            parseFatSecretFood(JSONObject(response))
        }

        val food = v2Result.getOrElse {
            // Fallback for accounts/endpoints where v2 barcode route is restricted.
            lookupFatSecretBarcodeLegacy(normalized, token)
        }
        cacheFoods(listOf(food))
        food
    }

    private fun lookupFatSecretBarcodeLegacy(barcode13: String, token: String): FoodItem {
        val b = URLEncoder.encode(barcode13, StandardCharsets.UTF_8.name())
        val findIdResponse = fatSecretGetJson(
            "https://platform.fatsecret.com/rest/server.api?method=food.find_id_for_barcode&barcode=$b&format=json",
            token
        )
        val findJson = JSONObject(findIdResponse)
        val foodIdAny = findJson.opt("food_id")
        val foodId = when (foodIdAny) {
            is JSONObject -> foodIdAny.optString("value", "")
            is Number -> foodIdAny.toLong().toString()
            is String -> foodIdAny
            else -> ""
        }.trim()
        if (foodId.isBlank()) {
            throw IllegalStateException("FatSecret barcode found no food_id")
        }

        val id = URLEncoder.encode(foodId, StandardCharsets.UTF_8.name())
        val getFoodResponse = fatSecretGetJson(
            "https://platform.fatsecret.com/rest/server.api?method=food.get.v5&food_id=$id&format=json",
            token
        )
        return parseFatSecretFood(JSONObject(getFoodResponse))
    }

    private fun fatSecretGetJson(urlText: String, token: String): String {
        val conn = (URL(urlText).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Authorization", "Bearer $token")
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            val errorBody = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { br -> br.readText() } } ?: ""
            conn.disconnect()
            throw IllegalStateException("FatSecret error ${conn.responseCode}: $errorBody")
        }
        val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        conn.disconnect()
        return response
    }

    suspend fun loadProfile(): UserProfile? = withContext(Dispatchers.IO) {
        prefs.getString("profile_json", null)?.let { profileFromJson(JSONObject(it)) }
    }

    suspend fun saveProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        prefs.edit().putString("profile_json", profileToJson(profile).toString()).apply()
    }

    suspend fun loadLanguage(): AppLanguage = withContext(Dispatchers.IO) {
        val tag = prefs.getString("language_tag", AppLanguage.SYSTEM.tag) ?: AppLanguage.SYSTEM.tag
        AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.SYSTEM
    }

    suspend fun saveLanguage(language: AppLanguage) = withContext(Dispatchers.IO) {
        prefs.edit().putString("language_tag", language.tag).apply()
    }

    suspend fun loadConsumedEntries(): List<ConsumedFoodEntry> = withContext(Dispatchers.IO) {
        val raw = prefs.getString("entries_json", null) ?: return@withContext emptyList()
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val legacyServings = o.optInt("servings", 1)
                val legacyKcal = o.optDouble("caloriesPerServing", 0.0)
                val source = FoodSource.entries.firstOrNull { it.name == o.optString("source", FoodSource.USDA.name) } ?: FoodSource.USDA
                val oldFdcId = o.optLong("fdcId", -1L)
                val oldFoodId = if (oldFdcId > 0) oldFdcId.toString() else o.optString("foodId", "")
                add(
                    ConsumedFoodEntry(
                        id = o.getString("id"),
                        date = o.getString("date"),
                        foodId = oldFoodId,
                        source = source,
                        description = o.getString("description"),
                        caloriesPer100g = o.optDouble("caloriesPer100g", legacyKcal),
                        proteinPer100g = o.optDouble("proteinPer100g", 0.0),
                        carbsPer100g = o.optDouble("carbsPer100g", 0.0),
                        fatPer100g = o.optDouble("fatPer100g", 0.0),
                        gramsConsumed = o.optDouble("gramsConsumed", legacyServings * 100.0),
                        fiberPer100g = o.optDouble("fiberPer100g", 0.0),
                        sugarPer100g = o.optDouble("sugarPer100g", 0.0),
                        sodiumMgPer100g = o.optDouble("sodiumMgPer100g", 0.0),
                        potassiumMgPer100g = o.optDouble("potassiumMgPer100g", 0.0),
                        calciumMgPer100g = o.optDouble("calciumMgPer100g", 0.0),
                        ironMgPer100g = o.optDouble("ironMgPer100g", 0.0)
                    )
                )
            }
        }
    }

    suspend fun saveConsumedEntries(entries: List<ConsumedFoodEntry>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("date", e.date)
                    .put("foodId", e.foodId)
                    .put("source", e.source.name)
                    .put("description", e.description)
                    .put("caloriesPer100g", e.caloriesPer100g)
                    .put("proteinPer100g", e.proteinPer100g)
                    .put("carbsPer100g", e.carbsPer100g)
                    .put("fatPer100g", e.fatPer100g)
                    .put("fiberPer100g", e.fiberPer100g)
                    .put("sugarPer100g", e.sugarPer100g)
                    .put("sodiumMgPer100g", e.sodiumMgPer100g)
                    .put("potassiumMgPer100g", e.potassiumMgPer100g)
                    .put("calciumMgPer100g", e.calciumMgPer100g)
                    .put("ironMgPer100g", e.ironMgPer100g)
                    .put("gramsConsumed", e.gramsConsumed)
            )
        }
        prefs.edit().putString("entries_json", arr.toString()).apply()
    }

    suspend fun saveRecipes(recipes: List<Recipe>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        recipes.forEach { recipe ->
            val ingredientsArr = JSONArray()
            recipe.ingredients.forEach { ing ->
                ingredientsArr.put(
                    JSONObject()
                        .put("foodId", ing.foodId)
                        .put("foodSource", ing.foodSource.name)
                        .put("foodDescription", ing.foodDescription)
                        .put("gramsUsed", ing.gramsUsed)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", recipe.id)
                    .put("name", recipe.name)
                    .put("ingredients", ingredientsArr)
                    .put("createdAtEpochMs", recipe.createdAtEpochMs)
                    .put("notes", recipe.notes)
            )
        }
        prefs.edit().putString("recipes_json", arr.toString()).apply()
    }

    suspend fun loadRecipes(): List<Recipe> = withContext(Dispatchers.IO) {
        val raw = prefs.getString("recipes_json", null) ?: return@withContext emptyList()
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ingredientsArr = o.optJSONArray("ingredients") ?: JSONArray()
                val ingredients = mutableListOf<RecipeIngredient>()
                for (j in 0 until ingredientsArr.length()) {
                    val ingObj = ingredientsArr.getJSONObject(j)
                    val source = FoodSource.entries.firstOrNull { it.name == ingObj.optString("foodSource", FoodSource.MANUAL.name) } ?: FoodSource.MANUAL
                    ingredients.add(
                        RecipeIngredient(
                            foodId = ingObj.getString("foodId"),
                            foodSource = source,
                            foodDescription = ingObj.getString("foodDescription"),
                            gramsUsed = ingObj.getDouble("gramsUsed")
                        )
                    )
                }
                add(
                    Recipe(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        ingredients = ingredients,
                        createdAtEpochMs = o.getLong("createdAtEpochMs"),
                        notes = o.optString("notes", "")
                    )
                )
            }
        }
    }

    suspend fun exportBackupToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("schemaVersion", 1)
            .put("exportedAtEpochMs", System.currentTimeMillis())
            .put("profile_json", prefs.getString("profile_json", null))
            .put("entries_json", prefs.getString("entries_json", null))
            .put("foods_cache_json", prefs.getString("foods_cache_json", null))
            .put("recipes_json", prefs.getString("recipes_json", null))
            .toString(2)

        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }
        } ?: throw IllegalStateException("Unable to open selected file for export")

        payload.toByteArray(StandardCharsets.UTF_8).size
    }

    suspend fun importBackupFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        } ?: throw IllegalStateException("Unable to open selected file for import")

        val json = JSONObject(raw)
        val schemaVersion = json.optInt("schemaVersion", 1)
        if (schemaVersion != 1) {
            throw IllegalStateException("Unsupported backup version: $schemaVersion")
        }

        val profileJson = json.opt("profile_json")?.takeIf { it != JSONObject.NULL }?.toString()
        val entriesJson = json.opt("entries_json")?.takeIf { it != JSONObject.NULL }?.toString()
        val foodsCacheJson = json.opt("foods_cache_json")?.takeIf { it != JSONObject.NULL }?.toString()
        val recipesJson = json.opt("recipes_json")?.takeIf { it != JSONObject.NULL }?.toString()

        validateJsonString(profileJson, isArray = false)
        validateJsonString(entriesJson, isArray = true)
        validateJsonString(foodsCacheJson, isArray = true)
        validateJsonString(recipesJson, isArray = true)

        prefs.edit()
            .putString("profile_json", profileJson)
            .putString("entries_json", entriesJson)
            .putString("foods_cache_json", foodsCacheJson)
            .putString("recipes_json", recipesJson)
            .apply()
    }

    suspend fun resolveFoodWithCompleteMacros(food: FoodItem, usdaApiKey: String): FoodItem = withContext(Dispatchers.IO) {
        if (!food.nutrientsLikelyMissing()) return@withContext food
        if (food.source != FoodSource.USDA || usdaApiKey.isBlank()) return@withContext food
        fetchUsdaFoodById(food.id, usdaApiKey) ?: food
    }

    suspend fun resolveRecipeToFood(recipe: Recipe): FoodItem = withContext(Dispatchers.IO) {
        val cachedFoods = mutableMapOf<Pair<String, FoodSource>, FoodItem>()
        val raw = prefs.getString("foods_cache_json", null)
        if (raw != null) {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val source = FoodSource.entries.firstOrNull { it.name == o.optString("source", FoodSource.USDA.name) } ?: FoodSource.USDA
                val id = o.optString("id", o.optLong("fdcId", -1L).toString())
                if (id.isNotBlank() && id != "-1") {
                    val food = FoodItem(
                        id = id,
                        source = source,
                        description = o.optString("description"),
                        brand = o.optString("brand"),
                        caloriesPer100g = o.optDouble("caloriesPer100g", o.optDouble("calories", 0.0)),
                        proteinPer100g = o.optDouble("proteinPer100g", 0.0),
                        carbsPer100g = o.optDouble("carbsPer100g", 0.0),
                        fatPer100g = o.optDouble("fatPer100g", 0.0),
                        fiberPer100g = o.optDouble("fiberPer100g", 0.0),
                        sugarPer100g = o.optDouble("sugarPer100g", 0.0),
                        sodiumMgPer100g = o.optDouble("sodiumMgPer100g", 0.0),
                        potassiumMgPer100g = o.optDouble("potassiumMgPer100g", 0.0),
                        calciumMgPer100g = o.optDouble("calciumMgPer100g", 0.0),
                        ironMgPer100g = o.optDouble("ironMgPer100g", 0.0)
                    )
                    cachedFoods[id to source] = food
                }
            }
        }
        
        val totalGramsInRecipe = recipe.ingredients.sumOf { it.gramsUsed }
        if (totalGramsInRecipe == 0.0) {
            return@withContext FoodItem(
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
        
        for (ingredient in recipe.ingredients) {
            val food = cachedFoods[ingredient.foodId to ingredient.foodSource]
            if (food != null) {
                val multiplier = ingredient.gramsUsed / 100.0
                totalCalories += food.caloriesPer100g * multiplier
                totalProtein += food.proteinPer100g * multiplier
                totalCarbs += food.carbsPer100g * multiplier
                totalFat += food.fatPer100g * multiplier
                totalFiber += food.fiberPer100g * multiplier
                totalSugar += food.sugarPer100g * multiplier
                totalSodiumMg += food.sodiumMgPer100g * multiplier
                totalPotassiumMg += food.potassiumMgPer100g * multiplier
                totalCalciumMg += food.calciumMgPer100g * multiplier
                totalIronMg += food.ironMgPer100g * multiplier
            }
        }
        
        // Convert totals to per 100g
        val divisor = totalGramsInRecipe / 100.0
        return@withContext FoodItem(
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
            ironMgPer100g = totalIronMg / divisor
        )
    }

    suspend fun enrichEntriesIfMissingMacros(
        entries: List<ConsumedFoodEntry>,
        usdaApiKey: String
    ): List<ConsumedFoodEntry> = withContext(Dispatchers.IO) {
        if (usdaApiKey.isBlank()) return@withContext entries
        entries.map { entry ->
            if (entry.nutrientsLikelyMissing() && entry.source == FoodSource.USDA && entry.foodId.isNotBlank()) {
                val fetched = fetchUsdaFoodById(entry.foodId, usdaApiKey)
                if (fetched != null) {
                    entry.copy(
                        caloriesPer100g = if (entry.caloriesPer100g == 0.0) fetched.caloriesPer100g else entry.caloriesPer100g,
                        proteinPer100g = fetched.proteinPer100g,
                        carbsPer100g = fetched.carbsPer100g,
                        fatPer100g = fetched.fatPer100g,
                        fiberPer100g = fetched.fiberPer100g,
                        sugarPer100g = fetched.sugarPer100g,
                        sodiumMgPer100g = fetched.sodiumMgPer100g,
                        potassiumMgPer100g = fetched.potassiumMgPer100g,
                        calciumMgPer100g = fetched.calciumMgPer100g,
                        ironMgPer100g = fetched.ironMgPer100g
                    )
                } else {
                    entry
                }
            } else {
                entry
            }
        }
    }

    private fun fetchUsdaFoodById(foodId: String, apiKey: String): FoodItem? {
        val cleanId = foodId.filter { it.isDigit() }
        if (cleanId.isBlank()) return null
        val url = URL("https://api.nal.usda.gov/fdc/v1/food/$cleanId?api_key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            return null
        }
        val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        conn.disconnect()
        return parseUsdaFoodDetail(JSONObject(response))
    }

    private fun parseUsdaFoods(root: JSONObject): List<FoodItem> {
        val foods = root.optJSONArray("foods") ?: JSONArray()
        return buildList {
            for (i in 0 until foods.length()) {
                val o = foods.optJSONObject(i) ?: continue
                val id = o.optLong("fdcId", -1L)
                if (id <= 0) continue
                val desc = o.optString("description", "Unknown food")
                val brand = o.optString("brandOwner", "").ifBlank { o.optString("brandName", "") }
                add(
                    FoodItem(
                        id = id.toString(),
                        source = FoodSource.USDA,
                        description = desc,
                        brand = brand,
                        caloriesPer100g = extractUsdaPer100g(o, "208", "Energy", "calories"),
                        proteinPer100g = extractUsdaPer100g(o, "203", "Protein", "protein"),
                        carbsPer100g = extractUsdaPer100g(o, "205", "Carbohydrate", "carbohydrates"),
                        fatPer100g = extractUsdaPer100g(o, "204", "Total lipid", "fat"),
                        fiberPer100g = extractUsdaPer100g(o, "291", "Fiber", "fiber"),
                        sugarPer100g = extractUsdaPer100g(o, "269", "Sugars", "sugars"),
                        sodiumMgPer100g = extractUsdaPer100g(o, "307", "Sodium", "sodium"),
                        potassiumMgPer100g = extractUsdaPer100g(o, "306", "Potassium", "potassium"),
                        calciumMgPer100g = extractUsdaPer100g(o, "301", "Calcium", "calcium"),
                        ironMgPer100g = extractUsdaPer100g(o, "303", "Iron", "iron")
                    )
                )
            }
        }
    }

    private fun parseUsdaFoodDetail(root: JSONObject): FoodItem {
        val id = root.optLong("fdcId", -1L)
        val description = root.optString("description", "Unknown food")
        val brand = root.optString("brandOwner", "").ifBlank { root.optString("brandName", "") }
        return FoodItem(
            id = if (id > 0) id.toString() else root.optString("fdcId", ""),
            source = FoodSource.USDA,
            description = description,
            brand = brand,
            caloriesPer100g = extractUsdaPer100g(root, "208", "Energy", "calories"),
            proteinPer100g = extractUsdaPer100g(root, "203", "Protein", "protein"),
            carbsPer100g = extractUsdaPer100g(root, "205", "Carbohydrate", "carbohydrates"),
            fatPer100g = extractUsdaPer100g(root, "204", "Total lipid", "fat"),
            fiberPer100g = extractUsdaPer100g(root, "291", "Fiber", "fiber"),
            sugarPer100g = extractUsdaPer100g(root, "269", "Sugars", "sugars"),
            sodiumMgPer100g = extractUsdaPer100g(root, "307", "Sodium", "sodium"),
            potassiumMgPer100g = extractUsdaPer100g(root, "306", "Potassium", "potassium"),
            calciumMgPer100g = extractUsdaPer100g(root, "301", "Calcium", "calcium"),
            ironMgPer100g = extractUsdaPer100g(root, "303", "Iron", "iron")
        )
    }

    private fun parseFatSecretFood(root: JSONObject): FoodItem {
        val food = root.optJSONObject("food") ?: throw IllegalStateException("No food found for barcode")
        val foodId = food.optString("food_id", "")
        if (foodId.isBlank()) throw IllegalStateException("Missing food_id from FatSecret")

        val servingArray = food.optJSONObject("servings")?.opt("serving")
        val servings = when (servingArray) {
            is JSONArray -> servingArray
            is JSONObject -> JSONArray().put(servingArray)
            else -> JSONArray()
        }
        if (servings.length() == 0) throw IllegalStateException("No serving data from FatSecret")

        var bestServing: JSONObject? = null
        var bestDistance = Double.MAX_VALUE
        for (i in 0 until servings.length()) {
            val s = servings.optJSONObject(i) ?: continue
            val amount = s.optDouble("metric_serving_amount", Double.NaN)
            val unit = s.optString("metric_serving_unit", "")
            if (amount.isFinite() && amount > 0.0 && unit.equals("g", ignoreCase = true)) {
                val d = abs(amount - 100.0)
                if (d < bestDistance) {
                    bestDistance = d
                    bestServing = s
                }
            }
        }
        if (bestServing == null) bestServing = servings.optJSONObject(0)
        val s = bestServing ?: throw IllegalStateException("No usable serving from FatSecret")

        val metricAmount = s.optDouble("metric_serving_amount", Double.NaN)
        val metricUnit = s.optString("metric_serving_unit", "")
        val factor = if (metricAmount.isFinite() && metricAmount > 0.0 && metricUnit.equals("g", ignoreCase = true)) {
            100.0 / metricAmount
        } else {
            1.0
        }

        fun num(vararg keys: String): Double {
            val raw = keys.firstNotNullOfOrNull { k ->
                parseFlexibleNumber(s.opt(k))
            } ?: 0.0
            return raw * factor
        }

        return FoodItem(
            id = foodId,
            source = FoodSource.FATSECRET,
            description = food.optString("food_name", "Unknown food"),
            brand = food.optString("brand_name", ""),
            caloriesPer100g = num("calories", "kcal"),
            proteinPer100g = num("protein"),
            carbsPer100g = num("carbohydrate", "carbohydrates"),
            fatPer100g = num("fat", "total_fat"),
            fiberPer100g = num("fiber", "dietary_fiber"),
            sugarPer100g = num("sugar", "sugars"),
            sodiumMgPer100g = num("sodium"),
            potassiumMgPer100g = num("potassium"),
            calciumMgPer100g = num("calcium"),
            ironMgPer100g = num("iron")
        )
    }

    private fun getFatSecretAccessToken(clientId: String, clientSecret: String): String {
        val now = System.currentTimeMillis()
        if (!fatSecretToken.isNullOrBlank() && now < fatSecretTokenExpiryEpochMs - 30_000L) {
            return fatSecretToken!!
        }

        val auth = Base64.getEncoder().encodeToString(
            "$clientId:$clientSecret".toByteArray(StandardCharsets.UTF_8)
        )
        val scopes = listOf("premier", "basic")
        var lastError: String? = null

        for (scope in scopes) {
            val url = URL("https://oauth.fatsecret.com/connect/token")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Authorization", "Basic $auth")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            val body = "grant_type=client_credentials&scope=$scope"
            DataOutputStream(conn.outputStream).use {
                it.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            if (conn.responseCode in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                conn.disconnect()
                val json = JSONObject(response)
                val token = json.optString("access_token", "")
                val expiresInSec = json.optLong("expires_in", 3600)
                if (token.isNotBlank()) {
                    fatSecretToken = token
                    fatSecretTokenExpiryEpochMs = now + expiresInSec * 1000
                    return token
                }
                lastError = "token missing for scope $scope"
            } else {
                val err = conn.errorStream?.let {
                    BufferedReader(InputStreamReader(it)).use { br -> br.readText() }
                } ?: ""
                conn.disconnect()
                lastError = "scope $scope: HTTP ${conn.responseCode} $err"
            }
        }
        throw IllegalStateException("FatSecret token error: ${lastError ?: "unknown"}")
    }

    private fun normalizeToGtin13(rawBarcode: String): String {
        val digits = rawBarcode.filter { it.isDigit() }
        return when {
            digits.length == 13 -> digits
            digits.length in 8..12 -> digits.padStart(13, '0')
            else -> digits
        }
    }

    private fun extractUsdaPer100g(food: JSONObject, nutrientNumber: String, nutrientNameContains: String, labelKey: String): Double {
        val fromNutrients = extractFromUsdaFoodNutrients(food, nutrientNumber, nutrientNameContains)
        if (fromNutrients.isFinite()) return fromNutrients

        val labelPerServing = food.optJSONObject("labelNutrients")?.optJSONObject(labelKey)?.optDouble("value", Double.NaN)
        if (labelPerServing != null && labelPerServing.isFinite()) {
            val servingSize = food.optDouble("servingSize", Double.NaN)
            val unit = food.optString("servingSizeUnit", "")
            if (servingSize.isFinite() && servingSize > 0.0 && unit.contains("g", ignoreCase = true)) {
                return labelPerServing * (100.0 / servingSize)
            }
            return labelPerServing
        }
        return 0.0
    }

    private fun extractFromUsdaFoodNutrients(food: JSONObject, nutrientNumber: String, nutrientNameContains: String): Double {
        val nutrients = food.optJSONArray("foodNutrients") ?: return Double.NaN
        for (i in 0 until nutrients.length()) {
            val n = nutrients.optJSONObject(i) ?: continue
            val nested = n.optJSONObject("nutrient")
            val num = n.optString("nutrientNumber", nested?.optString("number", "") ?: "")
            val name = n.optString("nutrientName", nested?.optString("name", "") ?: "")
            val unit = n.optString("unitName", nested?.optString("unitName", "") ?: "")
            if (num == nutrientNumber || name.contains(nutrientNameContains, ignoreCase = true)) {
                val direct = n.optDouble("value", Double.NaN)
                if (direct.isFinite()) return direct
                val amount = n.optDouble("amount", Double.NaN)
                if (amount.isFinite()) {
                    return if (unit.equals("kJ", ignoreCase = true) && nutrientNumber == "208") amount / 4.184 else amount
                }
                val fallback = parseFlexibleNumber(n.opt("value")) ?: parseFlexibleNumber(n.opt("amount"))
                if (fallback != null) {
                    return if (unit.equals("kJ", ignoreCase = true) && nutrientNumber == "208") fallback / 4.184 else fallback
                }
            }
        }
        return Double.NaN
    }

    private fun parseFlexibleNumber(value: Any?): Double? {
        return when (value) {
            null -> null
            is Number -> value.toDouble()
            is String -> {
                val cleaned = value
                    .trim()
                    .replace(",", ".")
                    .replace(Regex("[^0-9.\\-]"), "")
                cleaned.toDoubleOrNull()
            }
            else -> null
        }
    }

    private fun validateJsonString(raw: String?, isArray: Boolean) {
        if (raw == null) return
        if (isArray) JSONArray(raw) else JSONObject(raw)
    }

    private fun cacheFoods(newFoods: List<FoodItem>) {
        val existing = mutableMapOf<String, FoodItem>()
        prefs.getString("foods_cache_json", null)?.let { raw ->
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val source = FoodSource.entries.firstOrNull { it.name == o.optString("source", FoodSource.USDA.name) } ?: FoodSource.USDA
                val id = o.optString("id", o.optLong("fdcId", -1L).toString())
                if (id.isBlank() || id == "-1") continue
                val item = FoodItem(
                    id = id,
                    source = source,
                    description = o.optString("description"),
                    brand = o.optString("brand"),
                    caloriesPer100g = o.optDouble("caloriesPer100g", o.optDouble("calories", 0.0)),
                    proteinPer100g = o.optDouble("proteinPer100g", 0.0),
                    carbsPer100g = o.optDouble("carbsPer100g", 0.0),
                    fatPer100g = o.optDouble("fatPer100g", 0.0),
                    fiberPer100g = o.optDouble("fiberPer100g", 0.0),
                    sugarPer100g = o.optDouble("sugarPer100g", 0.0),
                    sodiumMgPer100g = o.optDouble("sodiumMgPer100g", 0.0),
                    potassiumMgPer100g = o.optDouble("potassiumMgPer100g", 0.0),
                    calciumMgPer100g = o.optDouble("calciumMgPer100g", 0.0),
                    ironMgPer100g = o.optDouble("ironMgPer100g", 0.0)
                )
                existing["${item.source.name}:${item.id}"] = item
            }
        }

        var changed = false
        newFoods.forEach { f ->
            val key = "${f.source.name}:${f.id}"
            if (!existing.containsKey(key)) {
                existing[key] = f
                changed = true
            }
        }
        if (!changed) return

        val out = JSONArray()
        existing.values.forEach { e ->
            out.put(
                JSONObject()
                    .put("id", e.id)
                    .put("source", e.source.name)
                    .put("description", e.description)
                    .put("brand", e.brand)
                    .put("caloriesPer100g", e.caloriesPer100g)
                    .put("proteinPer100g", e.proteinPer100g)
                    .put("carbsPer100g", e.carbsPer100g)
                    .put("fatPer100g", e.fatPer100g)
                    .put("fiberPer100g", e.fiberPer100g)
                    .put("sugarPer100g", e.sugarPer100g)
                    .put("sodiumMgPer100g", e.sodiumMgPer100g)
                    .put("potassiumMgPer100g", e.potassiumMgPer100g)
                    .put("calciumMgPer100g", e.calciumMgPer100g)
                    .put("ironMgPer100g", e.ironMgPer100g)
            )
        }
        prefs.edit().putString("foods_cache_json", out.toString()).apply()
    }

    private fun profileToJson(profile: UserProfile): JSONObject =
        JSONObject()
            .put("weightKg", profile.weightKg)
            .put("heightCm", profile.heightCm)
            .put("age", profile.age)
            .put("sex", profile.sex.name)
            .put("activity", profile.activityLevel.name)
            .put("goal", profile.goal.name)
            .put("recommendedCalories", profile.recommendedCalories)
            .put(
                "macroTargets",
                JSONObject()
                    .put("proteinGrams", profile.macroTargets.proteinGrams)
                    .put("carbsGrams", profile.macroTargets.carbsGrams)
                    .put("fatGrams", profile.macroTargets.fatGrams)
            )

    private fun profileFromJson(json: JSONObject): UserProfile {
        val weight = json.getDouble("weightKg")
        val calories = json.getInt("recommendedCalories")
        val goal = Goal.valueOf(json.getString("goal"))
        val fallback = calculateMacroTargets(calories, weight, goal)
        val macroJson = json.optJSONObject("macroTargets")
        val macros = if (macroJson == null) {
            fallback
        } else {
            MacroTargets(
                macroJson.optDouble("proteinGrams", fallback.proteinGrams),
                macroJson.optDouble("carbsGrams", fallback.carbsGrams),
                macroJson.optDouble("fatGrams", fallback.fatGrams)
            )
        }
        return UserProfile(
            weightKg = weight,
            heightCm = json.getDouble("heightCm"),
            age = json.getInt("age"),
            sex = Sex.valueOf(json.getString("sex")),
            activityLevel = ActivityLevel.valueOf(json.getString("activity")),
            goal = goal,
            recommendedCalories = calories,
            macroTargets = macros
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NutritionPreview() {
    CarboMonTheme { NutritionApp() }
}
