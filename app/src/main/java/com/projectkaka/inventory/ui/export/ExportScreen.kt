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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    val context = androidx.compose.ui.platform.LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importKakaZip(uri)
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

        Column(Modifier.padding(16.dp)) {
            Text(
                text = "${state.itemCount} item(s) in the local store.",
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
            if (state.lastCsvName != null || state.lastJsonName != null || state.lastKakaName != null) {
                Text(
                    text = "Saved to Downloads/ProjectKaka",
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
                    text = "Restore Complete: ${it.itemsRestored} items, ${it.accountsRestored} accounts, ${it.categoriesRestored} categories, ${it.transactionsRestored} transactions, ${it.ledgerEntriesRestored} ledger entries.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
