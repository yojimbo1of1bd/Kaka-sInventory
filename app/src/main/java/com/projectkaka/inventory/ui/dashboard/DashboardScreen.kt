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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Description
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import com.projectkaka.inventory.ui.export.VisualMapDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import com.projectkaka.inventory.data.repository.ItemExportRow
import com.projectkaka.inventory.util.VisualMapGenerator
import com.projectkaka.inventory.util.GalleryHelper
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

@OptIn(ExperimentalFoundationApi::class)
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
    onOpenStatements: () -> Unit,
    onOpenDocumentCapture: () -> Unit = {},
    onOpenDocument: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ── Username prompt on first launch ──
    var showNameDialog by remember { mutableStateOf(userName.isBlank()) }
    var nameInput by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("All") }

    val otherCategories = remember(state.items) {
        state.items.map { it.category.trim() }
            .filter { it.isNotBlank() && !it.equals("Prescriptions & Slips", ignoreCase = true) && !it.equals("Electronics", ignoreCase = true) }
            .distinct()
            .sorted()
    }
    val categoryTabs = remember(otherCategories) {
        listOf("All", "Prescriptions & Slips", "Electronics") + otherCategories
    }

    val displayedItems = remember(state.items, selectedCategory) {
        if (selectedCategory == "All") {
            state.items
        } else if (selectedCategory == "Prescriptions & Slips") {
            state.items.filter {
                it.category.contains("Prescription", ignoreCase = true) ||
                it.category.contains("Slip", ignoreCase = true) ||
                it.category.contains("Receipt", ignoreCase = true) ||
                it.category.contains("Medical", ignoreCase = true) ||
                it.category.contains("Doc", ignoreCase = true)
            }
        } else {
            state.items.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

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
    var isBudgetExpanded by remember { mutableStateOf(false) }
    var showVisualMapDialog by remember { mutableStateOf(false) }

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
                        IconButton(onClick = onOpenStatements) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = "Statements",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Budget & Accounts Collapsible Bar ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(vertical = 4.dp)
                    .shadow(
                        elevation = if (isBudgetExpanded) 8.dp else 3.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
                    .clickable { isBudgetExpanded = !isBudgetExpanded },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isBudgetExpanded) 8.dp else 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!isBudgetExpanded) {
                    // ── Collapsed Single Bar ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "৳${"%.2f".format(state.dailyBudget.minorUnits / 100.0)}/d",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                " • Today: ৳${"%.2f".format(state.todaySpending.minorUnits / 100.0)}",
                                fontSize = 12.sp,
                                color = if (state.todaySpending > state.dailyBudget && state.dailyBudget.minorUnits > 0L) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Total: ৳${"%.2f".format(state.totalCashBalance.minorUnits / 100.0)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand budget and accounts",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    // ── Expanded Bar Details ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Daily Budget & Accounts",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Collapse budget and accounts",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Daily Budget", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                Text(
                                    "৳${"%.2f".format(state.dailyBudget.minorUnits / 100.0)} / day",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("${state.remainingDays} days left", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Today's Spend", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                val spendColor = if (state.todaySpending > state.dailyBudget && state.dailyBudget.minorUnits > 0L) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.primary
                                Text(
                                    "৳${"%.2f".format(state.todaySpending.minorUnits / 100.0)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = spendColor
                                )
                                Text("Visible Balance: ৳${"%.2f".format(state.totalCashBalance.minorUnits / 100.0)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }

                        if (state.accountBalances.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Accounts",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(state.accountBalances) { acc ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
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


            val haptic = LocalHapticFeedback.current
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .combinedClickable(
                        onClick = onOpenCapture,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenDocumentCapture()
                        }
                    ),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Rapid Capture", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Category Filter Bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categoryTabs) { cat ->
                        val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 12.sp) },
                            leadingIcon = if (cat == "Prescriptions & Slips") {
                                {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else null
                        )
                    }
                }

                IconButton(
                    onClick = { showVisualMapDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Export Visual Map (PNG)",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Prescriptions & Slips Document Banner
            if (selectedCategory == "Prescriptions & Slips") {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Prescriptions & Slips Archive",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Review medical prescriptions, warranty slips, and receipts alongside your physical inventory.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

                    } // End Header Column
                } // End item

                when {
                    displayedItems.isEmpty() && state.documents.isEmpty() && selectedCategory == "Prescriptions & Slips" ->
                        item { EmptyState("No prescriptions or slips cataloged yet. Long-press Rapid Capture to photograph document slips.") }

                    selectedCategory == "Prescriptions & Slips" && state.documents.isNotEmpty() -> {
                        items(state.documents, key = { "doc_${it.id}" }) { doc ->
                            DocumentDashboardCard(
                                doc = doc,
                                onClick = { onOpenDocument(doc.id) },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }

                    displayedItems.isEmpty() && state.isFiltering ->
                        item { EmptyState("No items match this query.") }

                    displayedItems.isEmpty() && selectedCategory != "All" ->
                        item { EmptyState("No items found in category '$selectedCategory'.") }

                    displayedItems.isEmpty() ->
                        item { EmptyState("No active items yet. Tap Rapid Capture to begin.") }

                    else -> {
                    if (isGridView) {
                        items(displayedItems.chunked(2)) { rowItems ->
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
                        items(displayedItems, key = { it.id }) { item ->
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
                val recoveredMoney = try { com.projectkaka.inventory.model.Money.fromDecimalString(recovered) } catch (e: Exception) { com.projectkaka.inventory.model.Money.ZERO }
                viewModel.liquidate(item, status, recoveredMoney)
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

    if (showVisualMapDialog) {
        VisualMapDialog(
            items = state.items,
            onDismiss = { showVisualMapDialog = false }
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

@Composable
fun DocumentDashboardCard(
    doc: com.projectkaka.inventory.data.local.entity.DocumentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp, 75.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (doc.coverImagePath.isNotBlank() && java.io.File(doc.coverImagePath).exists()) {
                    coil.compose.AsyncImage(
                        model = java.io.File(doc.coverImagePath),
                        contentDescription = doc.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF58A6FF).copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = doc.docType.replace("_", " "),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF58A6FF)
                        )
                    }
                    Text(
                        text = "${doc.pageCount} page${if (doc.pageCount > 1) "s" else ""}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = doc.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                val dateStr = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(doc.issueDate))
                Text(
                    text = "Issued: $dateStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (doc.notes.isNotBlank()) {
                    Text(
                        text = doc.notes,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

