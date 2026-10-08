package com.projectkaka.inventory.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
    onPopTerminal: (() -> Unit)? = null,
    onPushTerminal: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: AliasGuideViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showManual by remember { mutableStateOf(false) }
    var showMatrix by remember { mutableStateOf(false) }
    var terminalInput by remember { mutableStateOf("") }
    var historyIndex by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val terminalLogs = state.terminalLogs
    val historyList = remember(terminalLogs.size) { viewModel.getCommandHistory() }
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
        historyIndex = -1
        
        viewModel.executeTerminalCommand(trimmed) { action ->
            when (action) {
                "alias" -> showManual = true
                "cmatrix" -> showMatrix = true
                "pop" -> onPopTerminal?.invoke()
                "push" -> onPushTerminal?.invoke()
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

    if (showMatrix) {
        MatrixScreensaver(onDismiss = { showMatrix = false })
    } else {
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
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showManual = true },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF58A6FF))
                    ) {
                        Text("📖 Manual", fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { executeCommand("help/ ?") },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF39D353))
                    ) {
                        Text("⚖️ help/ ?", fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { executeCommand("/help") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFBD2E))
                    ) {
                        Text("❓ /help", fontSize = 12.sp, maxLines = 1)
                    }
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
                            if (historyList.isNotEmpty()) {
                                if (historyIndex == -1) {
                                    historyIndex = historyList.size - 1
                                } else if (historyIndex > 0) {
                                    historyIndex--
                                }
                                terminalInput = historyList.getOrElse(historyIndex) { "" }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Previous Command",
                            tint = Color(0xFF8A8A8A)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (historyList.isNotEmpty() && historyIndex != -1) {
                                if (historyIndex < historyList.size - 1) {
                                    historyIndex++
                                    terminalInput = historyList.getOrElse(historyIndex) { "" }
                                } else {
                                    historyIndex = -1
                                    terminalInput = ""
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Next Command",
                            tint = Color(0xFF8A8A8A)
                        )
                    }
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

                        Text("3. Double-Entry Accounting (f/)", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Strict double-entry bookkeeping: Always DEBIT first, then CREDIT. Syntax: 'f/ <amount> <debit> <credit> [\"notes\"] [@date]'. Example expense: 'f/ -120 exp bkash \"For lunch\"'. Example income: 'f/ +5050 bkash salary \"Father sent money\"'.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        Spacer(Modifier.height(8.dp))

                        Text("4. Linux Man Pages & Accounting Handbook", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Type 'help/ ?' or 'man ?' to read the Debits & Credits Handbook. Type 'man <cmd>' (e.g. 'man f/', 'man log/', 'man settle/') for complete manual pages with real-world tradeoffs and examples.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        Spacer(Modifier.height(8.dp))

                        Text("5. Financial Tradeoffs Audit Log (log/)", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Audit financial tradeoffs and command origins over any time period. Examples: 'log/ august', 'log/ today', 'log/ 2026-10-07 10:00-18:00', 'log/ all'.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        Spacer(Modifier.height(8.dp))

                        Text("6. Action Commands (kaka)", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Type 'kaka show graph' for analytics, 'kaka show alias' for this manual, and 'kaka ledger' for debts.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        Spacer(Modifier.height(8.dp))
                        
                        Text("7. Maintenance (Red Ring)", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Long press an item to set recurring care tasks. The Profile Icon gets a Red Ring when tasks are due.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        Spacer(Modifier.height(8.dp))
                        
                        Text("8. Cryptographic Baseline & Integrity", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("Use 'snapshot/ create' to build a cryptographic baseline, and 'snapshot/ verify' or 'verify/ images' to audit local files against corruption.", fontSize = 14.sp, color = Color(0xFF8A8A8A))
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Text(
                            text = "Command Reference",
                            color = Color(0xFFE6EDF3),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        SectionHeader("Manuals & Accounting Handbook")
                        CommandRow("help/ ?", "Open Debits & Credits Handbook (A + E = L + Eq + R)")
                        CommandRow("man f/", "Detailed man page for double-entry financial commands")
                        CommandRow("man settle/", "Detailed man page for FIFO debt settlements")
                        CommandRow("man log/", "Detailed man page for tradeoff audit logging")
                        CommandRow("man snapshot/", "Detailed man page for cryptographic baseline integrity")
                        CommandRow("man due/", "Detailed man page for receivables and payables")
                        CommandRow("man kaka", "Detailed man page for terminal UI action shortcuts")

                        Spacer(Modifier.height(16.dp))

                        SectionHeader("Financial Double-Entry Entries (DR first, CR second)")
                        CommandRow("f/ -120 exp bkash \"lunch\"", "Debit Expenses (+120), Credit bKash (-120)")
                        CommandRow("f/ +5050 bkash salary \"Aug\"", "Debit bKash (+5050), Credit Salary Revenue (+5050)")
                        CommandRow("bal/ bkash", "Check current balance of bKash account")
                        CommandRow("bal/ all", "View balances across all active accounts")

                        Spacer(Modifier.height(16.dp))

                        SectionHeader("Tradeoff Audit Logging")
                        CommandRow("log/ august", "Audit double-entry tradeoffs for month of August")
                        CommandRow("log/ today", "View all financial tradeoffs logged today")
                        CommandRow("log/ 2026-10-07 10:00-18:00", "Filter tradeoffs by specific date and time window")
                        CommandRow("log/ all", "View complete historical tradeoffs audit log")

                        Spacer(Modifier.height(16.dp))

                        SectionHeader("Transfers & Cash Out")
                        CommandRow("xfer/ 1000 cash bkash", "Transfer ৳1000 from Cash to bKash")
                        CommandRow("charge/ bkash *1.85%", "Configure cashout charge rate for bKash")
                        CommandRow("charge/ all", "View all configured cashout charge rates")
                        CommandRow("cashout/ 1000 bkash", "Cash out ৳1000 from bKash to Cash with charge")

                        Spacer(Modifier.height(16.dp))

                        SectionHeader("Debt & Liability Ledger (FIFO Settlements)")
                        CommandRow("due/ out 500 \"babul mama\" grocery", "Log ৳500 payable (you owe Babul Mama)")
                        CommandRow("due/ in 1200 \"karim\" loan", "Log ৳1200 receivable (Karim owes you)")
                        CommandRow("settle/ \"babul mama\" cash", "Settle all open debts with Babul Mama (FIFO)")
                        CommandRow("settle/ \"babul mama\" 500 cash", "Settle debts up to ৳500, calculate change/split")
                        CommandRow("kaka ledger", "Open the full Debt & Liability ledger")

                        Spacer(Modifier.height(16.dp))

                        SectionHeader("Account & System Management")
                        CommandRow("init/ bkash 5000", "Initialize or create bKash with ৳5000 opening balance")
                        CommandRow("account/ add Bank cash 10000", "Create new account with type and balance")
                        CommandRow("account/ type bkash cash", "Change or fix account type (CASH, LIABILITY, etc.)")
                        CommandRow("account/ archive bkash", "Archive an account without losing history")
                        CommandRow("alter/ bkash type cash", "Change account classification")
                        CommandRow("alter/ bkash rename bKash Mobile", "Rename account")
                        CommandRow("clear/", "Clear terminal output history and reset screen")
                        CommandRow("cmatrix/", "Full-screen Matrix falling digital rain screensaver")
                        CommandRow("pop/", "Pop out terminal into hovering draggable window")
                        CommandRow("push/", "Dock floating terminal back into full screen")
                        CommandRow("snapshot/ take [pass]", "Whole-system Merkle tree snapshot with passphrase")
                        CommandRow("snapshot/ check [pass]", "Live audit against latest baseline (pinpoints alterations)")
                        CommandRow("snapshot/ list", "List snapshot history ledger (root hash protected)")
                        CommandRow("snapshot/ delete <id>", "Delete historical snapshots from ledger")
                        CommandRow("history/ 20", "View recent terminal command history")

                        Spacer(Modifier.height(16.dp))

                        SectionHeader("Financial Statements & Reports")
                        CommandRow("report/ bs", "Generate complete Balance Sheet (A = L + E)")
                        CommandRow("report/ pnl", "Generate Net Income Statement (Revenue - Expenses)")
                        CommandRow("kaka show graph", "Open analytics graph and trajectory")
                        CommandRow("kaka export", "Open backup export & restore screen")
                        CommandRow("/help", "Quick command syntax guide")

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
