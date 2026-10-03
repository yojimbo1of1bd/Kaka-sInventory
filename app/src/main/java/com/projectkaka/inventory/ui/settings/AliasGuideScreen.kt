package com.projectkaka.inventory.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectkaka.inventory.data.local.entity.CategoryType
import com.projectkaka.inventory.search.TerminalResult

import com.projectkaka.inventory.ui.settings.LogType
import com.projectkaka.inventory.ui.settings.TerminalLog

// We no longer define TerminalLine here, we use TerminalLog from ViewModel
// mapping it locally to UI colors
@Composable
fun TerminalLog.toColor(): Color = when (this.type) {
    LogType.SUCCESS -> Color(0xFF39D353)
    LogType.ERROR -> Color(0xFFFF7B72)
    LogType.WARNING -> Color(0xFFFFBD2E)
    LogType.INFO, LogType.HINT -> Color(0xFF8A8A8A)
    LogType.ECHO -> Color(0xFF58A6FF)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliasGuideScreen(
    onBack: () -> Unit,
    onOpenGraph: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenStatements: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AliasGuideViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showManual by remember { mutableStateOf(false) }
    var terminalInput by remember { mutableStateOf("") }
    val terminalLogs = state.terminalLogs
    val listState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty()) {
            listState.animateScrollToItem(terminalLogs.size - 1)
        }
    }

    fun executeCommand(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return
        
        viewModel.executeTerminalCommand(trimmed) { action ->
            when (action) {
                "alias" -> showManual = true
                "graph" -> onOpenGraph()
                "ledger" -> onOpenLedger()
                "export" -> onOpenExport()
                "settings" -> onOpenSettings()
                "accounts" -> onOpenAccounts()
                "report" -> onOpenStatements()
            }
        }
        terminalInput = ""
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Terminal", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
                .padding(padding)
                .imePadding()
        ) {
            if (!showManual) {
                // ── Terminal description ──
                Text(
                    "Run kaka commands and view aliases",
                    color = Color(0xFF8A8A8A),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                
                OutlinedButton(
                    onClick = { showManual = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Open Terminal Manual", color = Color(0xFF58A6FF))
                }

                Spacer(Modifier.height(8.dp))

                // ── Terminal output area ──
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        // Terminal header
                        Row(modifier = Modifier.padding(bottom = 8.dp)) {
                            listOf(Color(0xFFFF5F56), Color(0xFFFFBD2E), Color(0xFF27C93F)).forEach { dotColor ->
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .background(dotColor, RoundedCornerShape(50))
                                        .width(12.dp)
                                        .height(12.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "kaka@device",
                                color = Color(0xFF8A8A8A),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        // Terminal lines
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            state = listState
                        ) {
                            items(terminalLogs) { log ->
                                Text(
                                    text = log.text,
                                    color = log.toColor(),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (log.isBold) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Terminal input ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$",
                        color = Color(0xFF39D353),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    OutlinedTextField(
                        value = terminalInput,
                        onValueChange = { terminalInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a command...", color = Color(0xFF4A5568), fontFamily = FontFamily.Monospace) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (terminalInput.isNotBlank()) {
                                    executeCommand(terminalInput)
                                    terminalInput = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            cursorColor = Color(0xFF39D353),
                            focusedBorderColor = Color(0xFF30363D),
                            unfocusedBorderColor = Color(0xFF21262D)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    IconButton(
                        onClick = {
                            if (terminalInput.isNotBlank()) {
                                executeCommand(terminalInput)
                                terminalInput = ""
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color(0xFF39D353)
                        )
                    }
                }
            } else {
                // ── Manual / Reference View ──
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        Button(
                            onClick = { showManual = false },
                            modifier = Modifier.padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF21262D),
                                contentColor = Color(0xFF58A6FF)
                            )
                        ) {
                            Text("← Back to Terminal")
                        }
                        
                        Text(
                            text = "Project Kaka Guide",
                            color = Color(0xFFE6EDF3),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                        )

                        Text("1. Rapid Capture", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Take photos of your items. Use burst mode for multiple items. All images go to Drafts.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        Spacer(Modifier.height(8.dp))
                        
                        Text("2. Triage & Drafts", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Go to Drafts to categorize your items. Items in Drafts have red borders in the grid.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        Spacer(Modifier.height(8.dp))

                        Text("3. Financial Commands (f/)", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Use the search bar with 'f/ [amount] [account] [category]' to log money. Example: 'f/ -150 bkash lunch'", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        Spacer(Modifier.height(8.dp))

                        Text("4. Action Commands (kaka)", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Type 'kaka show graph' for analytics, 'kaka show alias' for financial shortcuts, and 'kaka ledger' for debts.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        Spacer(Modifier.height(8.dp))
                        
                        Text("5. Maintenance (Red Ring)", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Long press an item to set recurring care tasks. The Profile Icon gets a Red Ring when tasks are due.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        Spacer(Modifier.height(8.dp))
                        
                        Text("6. Alerts", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Configure alert recipients in Settings. When overspending is detected (3 strikes), an emergency message is sent.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        Spacer(Modifier.height(8.dp))
                        
                        Text("7. Voice Input", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Tap the microphone icon in the Ledger note field to dictate entries in English.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Text(
                            text = "Command Reference",
                            color = Color(0xFFE6EDF3),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        SectionHeader("Financial Quick-Entry")
                        CommandRow("f/ -500 cash food", "Log ৳500 expense from Cash → Food")
                        CommandRow("f/ +200 bkash salary", "Log ৳200 income to bKash → Salary")
                        CommandRow("credit/ 500 bkash salary", "Shorthand for f/ +500 bkash salary")
                        CommandRow("debit/ 300 cash food", "Shorthand for f/ -300 cash food")

                        Spacer(Modifier.height(16.dp))

                        SectionHeader("Double-Entry & Accruals")
                        CommandRow("xfer/ 1000 cash bkash", "Transfer ৳1000 from Cash to bKash")
                        CommandRow("accrue/ 500 ar sales", "Accrue ৳500 Accounts Receivable to Sales")
                        CommandRow("realize/ 500 cash ar", "Realize ৳500 Cash from Accounts Receivable")
                        CommandRow("due/ in 500 rahim payable", "Log a ৳500 debt you owe Rahim")
                        CommandRow("settle/ rahim 500", "Settle ৳500 of debt with Rahim")
                        CommandRow("bal/ cash", "Check the balance of Cash account")

                        Spacer(Modifier.height(16.dp))

                        SectionHeader("Account Management")
                        CommandRow("init/ bkash 5000", "Set bKash balance to ৳5000")
                        CommandRow("alter/ bkash bKash Mobile", "Rename bkash to 'bKash Mobile'")
                        CommandRow("delete/ oldaccount", "Delete an account")

                        Spacer(Modifier.height(16.dp))

                        SectionHeader("Navigation")
                        CommandRow("kaka show graph", "Open analytics / balance trajectory")
                        CommandRow("kaka ledger", "Open the Debt & Liability ledger")
                        CommandRow("kaka show alias", "Open this terminal manual")
                        CommandRow("report/ bs", "View Balance Sheet")
                        CommandRow("report/ pnl", "View Income Statement")
                        CommandRow("kaka export", "Open export screen")
                        CommandRow("/help", "Show this command reference")

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = "Configured Aliases",
                            color = Color(0xFFE6EDF3),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    val visibleAccounts = state.accounts.filter { it.id !in state.hiddenAccountIds }
                    if (visibleAccounts.isNotEmpty()) {
                        item { SectionHeader("Accounts") }
                        items(visibleAccounts) { account ->
                            AliasRow(title = account.name, aliases = account.aliases)
                        }
                    }

                    val incomeCats = state.categories.filter { it.type == CategoryType.INCOME }
                    if (incomeCats.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)); SectionHeader("Income Categories") }
                        items(incomeCats) { cat ->
                            AliasRow(title = cat.name, aliases = cat.aliases)
                        }
                    }

                    val expenseCats = state.categories.filter { it.type == CategoryType.EXPENSE }
                    if (expenseCats.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)); SectionHeader("Expense Categories") }
                        items(expenseCats) { cat ->
                            AliasRow(title = cat.name, aliases = cat.aliases)
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF58A6FF),
        fontWeight = FontWeight.Black,
        fontSize = 16.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun AliasRow(title: String, aliases: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
            Spacer(Modifier.height(4.dp))
            Text(
                text = aliases.split(",").joinToString(" • ") { it.trim() },
                color = Color(0xFF8A8A8A),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun CommandRow(command: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(
                command,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF39D353),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = description,
                color = Color(0xFF8A8A8A),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
