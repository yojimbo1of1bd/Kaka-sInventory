package com.projectkaka.inventory.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ExportScreen(
    permStorage: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExportViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Export Data",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }

        if (pendingImportUri != null) {
            AlertDialog(
                onDismissRequest = { pendingImportUri = null },
                title = { Text("Confirm Import") },
                text = { Text("Importing this backup will overwrite and replace existing data in the app. This action cannot be undone. Do you want to proceed?") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingImportUri?.let { viewModel.checkAndImportKakaZip(it) }
                        pendingImportUri = null
                    }) {
                        Text("Import & Replace", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImportUri = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        val mismatchDialog = state.balanceMismatchDialog
        if (mismatchDialog != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissMismatchDialog() },
                title = { Text("Balance Mismatch Detected", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "The financial records in this backup contain balance discrepancies or conflicts:\n• ${mismatchDialog.reason}",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Are you willing to update your inventory (${mismatchDialog.itemCount} item(s), ${mismatchDialog.docCount} document(s)) and remaining image files from the zip, while declining the financial & liability ledger import?"
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.importKakaZip(mismatchDialog.uri, skipFinance = true)
                    }) {
                        Text("Update Inventory Only", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissMismatchDialog() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(Modifier.padding(16.dp)) {
            Text(
                text = "${state.itemCount} item(s) • ${state.documentCount} document(s) in local store",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Files are written to your device's Downloads/ProjectKaka folder. Nothing leaves the device " +
                    "unless you share it yourself.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { 
                    if (!permStorage) {
                        android.widget.Toast.makeText(context, "Storage & Media disabled. Enable in Settings -> Privacy.", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.exportCsv() 
                    }
                },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Export CSV", fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { 
                    if (!permStorage) {
                        android.widget.Toast.makeText(context, "Storage & Media disabled. Enable in Settings -> Privacy.", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.exportJson() 
                    }
                },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export JSON") }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { 
                    if (!permStorage) {
                        android.widget.Toast.makeText(context, "Storage & Media disabled. Enable in Settings -> Privacy.", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.exportKakaZip() 
                    }
                },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) { Text("Backup to .kaka (ZIP)", fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { 
                    if (!permStorage) {
                        android.widget.Toast.makeText(context, "Storage & Media disabled. Enable in Settings -> Privacy.", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        filePickerLauncher.launch("*/*") 
                    }
                },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restore from .kaka (ZIP)", fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(18.dp))

            // ── Visual Belongings Map (PNG) ──
            Text(
                text = "Visual Belongings Map (PNG Infographic)",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Export a high-resolution visual diagram / infographic of your electronics or any category directly to your phone's Gallery.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                fontSize = 11.sp
            )

            Spacer(Modifier.height(8.dp))

            var selectedMapCategory by remember { mutableStateOf("All") }
            var customCategoryInput by remember { mutableStateOf("") }
            val mapCategories = remember(state.availableCategories) {
                (listOf("All", "Electronics", "Prescriptions & Slips") + state.availableCategories).distinct()
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                items(mapCategories) { cat ->
                    FilterChip(
                        selected = selectedMapCategory.equals(cat, ignoreCase = true) && customCategoryInput.isBlank(),
                        onClick = {
                            selectedMapCategory = cat
                            customCategoryInput = ""
                        },
                        label = { Text(cat, fontSize = 11.sp) }
                    )
                }
            }

            androidx.compose.material3.OutlinedTextField(
                value = customCategoryInput,
                onValueChange = {
                    customCategoryInput = it
                    if (it.isNotBlank()) {
                        selectedMapCategory = it.trim()
                    }
                },
                label = { Text("Or enter custom category name", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            val effectiveCategory = if (customCategoryInput.isNotBlank()) customCategoryInput.trim() else selectedMapCategory

            Button(
                onClick = {
                    viewModel.exportVisualMap(effectiveCategory)
                },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text(
                    if (effectiveCategory.equals("all", ignoreCase = true))
                        "Export Complete Inventory Visual Map (PNG)"
                    else
                        "Export Visual Map for $effectiveCategory (PNG)",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(20.dp))

            state.lastCsvName?.let {
                Text(
                    text = "✅ CSV: $it",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            }
            state.lastJsonName?.let {
                Text(
                    text = "✅ JSON: $it",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            }
            state.lastKakaName?.let {
                Text(
                    text = "✅ Backup: $it",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            state.lastPngName?.let {
                Text(
                    text = "✅ Visual Map PNG: $it",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (state.lastCsvName != null || state.lastJsonName != null || state.lastKakaName != null || state.lastPngName != null) {
                Text(
                    text = "Files saved to Downloads or Pictures/ProjectKaka in Gallery",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            state.error?.let {
                Text(
                    text = "Error: $it",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
            state.restoreResult?.let {
                Text(
                    text = "Restore Complete: ${it.itemsRestored} items, ${it.documentsRestored} documents, ${it.accountsRestored} accounts, ${it.categoriesRestored} categories, ${it.transactionsRestored} transactions, ${it.ledgerEntriesRestored} ledger entries.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
