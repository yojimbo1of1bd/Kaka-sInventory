package com.projectkaka.inventory.ui.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Description
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalContext
import com.projectkaka.inventory.KakaApplication
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.ui.liquidate.LiquidationDialog
import com.projectkaka.inventory.ui.search.MagicInputBar
import com.projectkaka.inventory.ui.triage.MaintenanceDialog
import com.projectkaka.inventory.ui.triage.ProfileIcon
import com.projectkaka.inventory.ui.triage.TriageSheet

@Composable
fun DashboardScreen(
    useBottomNav: Boolean,
    userName: String,
    onUserNameSet: (String) -> Unit,
    onOpenCapture: () -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenItem: (Int) -> Unit,
    onOpenGraph: () -> Unit,
    onOpenAlias: () -> Unit,
    onOpenLedger: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ── Username prompt on first launch ──
    var showNameDialog by remember { mutableStateOf(userName.isBlank()) }
    var nameInput by remember { mutableStateOf("") }

    LaunchedEffect(state.triggerEmergencyAlert) {
        if (state.triggerEmergencyAlert) {
            val app = context.applicationContext as KakaApplication
            val alertsEnabled = app.preferences.alertsEnabled.value
            val alertMethod = app.preferences.alertMethod.value
            val permSmsCalls = app.preferences.permSmsCalls.value
            
            if (alertsEnabled) {
                if (!permSmsCalls) {
                    android.widget.Toast.makeText(context, "Emergency alert blocked: SMS & Calls disabled. Enable in Settings -> Privacy.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    val phone = app.preferences.emergencyContactNumber.value.replace(Regex("[^0-9+]"), "")
                    val formattedPhone = if (phone.startsWith("0")) "+88$phone" else phone
                    val message = "🚨 EMERGENCY ALERT from Project Kaka: I've overspent my budget 3 times in a row. Please check on me."
                    
                    when (alertMethod) {
                        "CALL" -> {
                            try {
                                val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$formattedPhone"))
                                context.startActivity(callIntent)
                            } catch (e: Exception) {}
                        }
                        "SMS" -> {
                            try {
                                val smsUri = Uri.parse("smsto:$formattedPhone")
                                val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                                    putExtra("sms_body", message)
                                }
                                context.startActivity(smsIntent)
                            } catch (e: Exception) {}
                        }
                        else -> { // WHATSAPP
                            try {
                                val waUri = Uri.parse("https://wa.me/$formattedPhone?text=${Uri.encode(message)}")
                                val waIntent = Intent(Intent.ACTION_VIEW, waUri)
                                context.startActivity(waIntent)
                            } catch (e: Exception) {
                                try {
                                    val smsUri = Uri.parse("smsto:$formattedPhone")
                                    val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                                        putExtra("sms_body", message)
                                    }
                                    context.startActivity(smsIntent)
                                } catch (e: Exception) {}
                            }
                        }
                    }
                }
            }
            viewModel.clearEmergencyAlert()
        }
    }



    var pendingLiquidation by remember { mutableStateOf<ItemEntity?>(null) }
    var pendingCareTask by remember { mutableStateOf<ItemEntity?>(null) }
    var showTriageSheet by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (useBottomNav) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = "Drafts") },
                        label = { Text("Drafts") },
                        selected = false,
                        onClick = onOpenDrafts
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Description, contentDescription = "Notes") },
                        label = { Text("Notes") },
                        selected = false,
                        onClick = onOpenLedger
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = false,
                        onClick = onOpenSettings
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Terminal, contentDescription = "Terminal") },
                        label = { Text("Terminal") },
                        selected = false,
                        onClick = onOpenAlias
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    val displayName = if (userName.isNotBlank()) "${userName}'s Project" else "Kaka's Project"
                    Text(
                        text = displayName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
                if (!useBottomNav) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onOpenDrafts) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = "Drafts",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onOpenLedger) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = "Notes",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onOpenAlias) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = "Terminal",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Budget Card ──
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize()
                    .padding(vertical = 4.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Daily Budget", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Text(
                            "৳${"%.2f".format(state.dailyBudget.minorUnits / 100.0)} / day",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("${state.remainingDays} days left", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Today's Spend", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        val spendColor = if (state.todaySpending > state.dailyBudget && state.dailyBudget.minorUnits > 0L) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                        Text(
                            "৳${"%.2f".format(state.todaySpending.minorUnits / 100.0)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = spendColor
                        )
                        Text("Visible Balance: ৳${"%.2f".format(state.totalCashBalance.minorUnits / 100.0)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
            
            if (state.accountBalances.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
                ) {
                    items(state.accountBalances) { acc ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(acc.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                Text("৳${"%.2f".format(acc.balance.minorUnits / 100.0)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Overspending Spike Alert ──
            if (state.todaySpending > state.dailyBudget && state.dailyBudget.minorUnits > 0L) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = " Overspending Spike Detected! (Strike ${state.overspendStrikeCount}/3) You've exceeded today's budget by ৳${"%.2f".format((state.todaySpending.minorUnits - state.dailyBudget.minorUnits) / 100.0)}.",
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            


            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    MagicInputBar(
                        query = state.query,
                        onQueryChange = viewModel::onQueryChange,
                        error = state.searchError
                    )
                }
                IconButton(onClick = { isGridView = !isGridView }) {
                    Icon(
                        if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle View",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (state.showQuickLog) {
                QuickNoteCard(
                    defaultDebitAcc = state.qlDebitAcc,
                    defaultDebitCat = state.qlDebitCat,
                    defaultCreditAcc = state.qlCreditAcc,
                    defaultCreditCat = state.qlCreditCat,
                    onLogDebit = { note, acc, cat -> 
                        val amount = note.split(" ").firstOrNull()?.toDoubleOrNull() ?: 0.0
                        val text = note.substringAfter(" ", missingDelimiterValue = "")
                        viewModel.executeTerminalCommand("f/ -$amount $acc $cat $text") { msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLogCredit = { note, acc, cat ->
                        val amount = note.split(" ").firstOrNull()?.toDoubleOrNull() ?: 0.0
                        val text = note.substringAfter(" ", missingDelimiterValue = "")
                        viewModel.executeTerminalCommand("f/ +$amount $acc $cat $text") { msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Button(
                onClick = onOpenCapture,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Text("  Rapid Capture", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

                    } // End Header Column
                } // End item

                when {
                state.items.isEmpty() && state.isFiltering ->
                    item { EmptyState("No items match this query.") }

                state.items.isEmpty() ->
                    item { EmptyState("No active items yet. Tap Rapid Capture to begin.") }

                else -> {
                    if (isGridView) {
                        items(state.items.chunked(2)) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { item ->
                                    SwipeableItemGridCell(
                                        item = item,
                                        onOpenDetail = { onOpenItem(it.id) },
                                        onLiquidateRequested = { pendingLiquidation = it },
                                        onCareTaskRequested = { pendingCareTask = it },
                                        modifier = Modifier.weight(1f).animateContentSize()
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(state.items, key = { it.id }) { item ->
                            SwipeableItemRow(
                                item = item,
                                onOpenDetail = { onOpenItem(it.id) },
                                onLiquidateRequested = { pendingLiquidation = it },
                                onCareTaskRequested = { pendingCareTask = it },
                                modifier = Modifier.animateContentSize().padding(bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}



    // Exit strategy: swipe right -> liquidate.
    pendingLiquidation?.let { item ->
        LiquidationDialog(
            item = item,
            onDismiss = { pendingLiquidation = null },
            onConfirm = { status, recovered ->
                viewModel.liquidate(item, status, com.projectkaka.inventory.model.Money((recovered * 100).toLong()))
                pendingLiquidation = null
            }
        )
    }

    // Maintenance: long press -> add a recurring care task.
    pendingCareTask?.let { item ->
        MaintenanceDialog(
            item = item,
            onDismiss = { pendingCareTask = null },
            onConfirm = { taskName, frequencyDays ->
                viewModel.addCareTask(item.id, taskName, frequencyDays)
                pendingCareTask = null
            }
        )
    }

    // The Ring's destination: every overdue care task, joined with its item.
    if (showTriageSheet) {
        TriageSheet(
            dueTasks = state.dueTasks,
            onComplete = { task -> viewModel.completeTask(task) },
            onDismiss = { showTriageSheet = false }
        )
    }

    // ── Username Prompt (first launch only) ──
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { /* Can't dismiss without entering name */ },
            title = {
                Text(
                    text = "Welcome to Project Kaka!",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column {
                    Text(
                        text = "What should we call you?",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Your name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            onUserNameSet(nameInput.trim())
                            showNameDialog = false
                        }
                    },
                    enabled = nameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("Let's Go!", fontWeight = FontWeight.Bold) }
            }
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
    }
}
