package com.example.carbomon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.DateFormat
import java.util.Date

@Composable
internal fun CatalogSyncSection(state: NutritionUiState, onAction: (NutritionAction) -> Unit) {
    var disconnect by remember { mutableStateOf(false) }
    var showKey by remember(state.catalogAddress) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onAction(NutritionAction.StopCatalogDiscovery)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onAction(NutritionAction.StopCatalogDiscovery)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.catalog_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.catalog_help), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            onAction(if (state.isDiscoveringCatalog) NutritionAction.StopCatalogDiscovery else NutritionAction.FindCatalogServer)
        }, enabled = !state.isSyncing && !state.isImporting) {
            Text(stringResource(if (state.isDiscoveringCatalog) R.string.catalog_discovery_cancel else R.string.catalog_find_server))
        }
        if (state.isDiscoveringCatalog) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.catalog_discovery_searching), style = MaterialTheme.typography.bodySmall)
        }
        if (state.catalogDiscoveryStatus.isNotBlank()) Text(state.catalogDiscoveryStatus, style = MaterialTheme.typography.bodySmall)
        state.discoveredCatalogServers.forEach { server ->
            OutlinedButton(onClick = { onAction(NutritionAction.SelectCatalogServer(server)) },
                enabled = !state.isSyncing && !state.isDiscoveringCatalog, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(server.name)
                    Text(server.address, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedTextField(value = state.catalogAddress, onValueChange = { onAction(NutritionAction.UpdateCatalogAddress(it)) },
            label = { Text(stringResource(R.string.catalog_address)) }, placeholder = { Text("http://192.168.1.10:8765") },
            singleLine = true, enabled = !state.isSyncing, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { showKey = !showKey }, enabled = !state.isSyncing) {
            Text(stringResource(R.string.catalog_key_options))
        }
        if (showKey || state.catalogNeedsKey) OutlinedTextField(
            value = state.catalogKey, onValueChange = { onAction(NutritionAction.UpdateCatalogKey(it)) },
            label = { Text(stringResource(R.string.catalog_key)) }, visualTransformation = PasswordVisualTransformation(),
            supportingText = { Text(stringResource(R.string.catalog_key_help)) },
            singleLine = true, enabled = !state.isSyncing, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onAction(NutritionAction.SyncCatalog) },
            enabled = !state.isSyncing && !state.isImporting && state.catalogAddress.isNotBlank()) {
            Text(stringResource(if (state.isSyncing) R.string.catalog_syncing else R.string.catalog_sync))
        }
        if (state.isSyncing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.catalogLastSync > 0) Text(stringResource(R.string.catalog_last_sync,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.catalogLastSync))),
            style = MaterialTheme.typography.bodySmall)
        if (state.catalogStatus.isNotBlank()) Text(state.catalogStatus, style = MaterialTheme.typography.bodySmall)
        if (state.catalogConflicts.isNotEmpty()) {
            Text(state.catalogConflicts.joinToString("\n") { "• $it" }, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onAction(NutritionAction.KeepLocalCatalogChanges) }, enabled = !state.isSyncing) {
                Text(stringResource(R.string.catalog_keep_local))
            }
            OutlinedButton(onClick = { onAction(NutritionAction.KeepServerCatalogChanges) }, enabled = !state.isSyncing) {
                Text(stringResource(R.string.catalog_keep_server))
            }
        }
        if (state.catalogAddress.isNotBlank()) TextButton(onClick = { disconnect = true }, enabled = !state.isSyncing) {
            Text(stringResource(R.string.catalog_disconnect))
        }
    }
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false },
        title = { Text(stringResource(R.string.catalog_disconnect)) },
        text = { Text(stringResource(R.string.catalog_disconnect_help)) },
        confirmButton = { TextButton(onClick = { disconnect = false; onAction(NutritionAction.ClearCatalogConnection) }) {
            Text(stringResource(R.string.catalog_disconnect))
        } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text(stringResource(android.R.string.cancel)) } })
}
