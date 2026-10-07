package com.projectkaka.inventory.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectkaka.inventory.data.local.entity.AccountType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsManagerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editAccountId by remember { mutableStateOf<Int?>(null) }
    var editAccountName by remember { mutableStateOf("") }
    var editAccountType by remember { mutableStateOf(AccountType.CASH) }
    var editAmount by remember { mutableStateOf("") }
    var editTypeExpanded by remember { mutableStateOf(false) }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newAccountName by remember { mutableStateOf("") }
    var newAccountBalance by remember { mutableStateOf("") }
    var newAccountType by remember { mutableStateOf(AccountType.CASH) }
    
    var showMessageDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize().systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("Manage Accounts", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Account", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = "Edit opening balances directly. This will not create a transaction record, but balance is recalculated.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
                )
            }

            items(state.accountsWithCounts) { awc ->
                val account = awc.account
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (account.isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("${account.name} (${account.type.name})", fontWeight = FontWeight.Bold, color = if (account.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text("Balance: ৳${account.balance}", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                Text("Opening: ৳${account.openingBalance}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                Text("Transactions: ${awc.transactionCount} | Ledger Links: ${awc.ledgerLinkCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { 
                                editAccountId = account.id
                                editAccountName = account.name
                                editAccountType = account.type
                                editAmount = account.openingBalance.toString()
                            }) {
                                Text("Edit")
                            }
                            TextButton(onClick = { 
                                viewModel.archiveAccount(account.id, account.isActive)
                            }) {
                                Text(if (account.isActive) "Archive" else "Unarchive")
                            }
                            TextButton(
                                onClick = { 
                                    viewModel.deleteAccount(account.id) { success, msg ->
                                        showMessageDialog = msg
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (editAccountId != null) {
        AlertDialog(
            onDismissRequest = { editAccountId = null },
            title = { Text("Edit Account: $editAccountName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editAccountName,
                        onValueChange = { editAccountName = it },
                        label = { Text("Account Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editAccountType.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account Type") },
                            trailingIcon = {
                                IconButton(onClick = { editTypeExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Type")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = editTypeExpanded,
                            onDismissRequest = { editTypeExpanded = false }
                        ) {
                            AccountType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text(type.name, fontWeight = FontWeight.Bold)
                                            val desc = when (type) {
                                                AccountType.CASH -> "Liquid funds (Cash, bKash, Nagad), budget active."
                                                AccountType.ASSET -> "Physical assets/property."
                                                AccountType.LIABILITY -> "Debts/Loans."
                                                AccountType.CAPITAL -> "Owner equity/Capital."
                                                AccountType.REVENUE -> "Sales / Income."
                                                AccountType.EXPENSE -> "Operating Expenses."
                                            }
                                            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                    },
                                    onClick = {
                                        editAccountType = type
                                        editTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = { Text("Opening Balance") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editAccountName.isNotBlank() && editAmount.isNotBlank()) {
                        viewModel.updateAccount(editAccountId!!, editAccountName, editAccountType, editAmount) { success, msg ->
                            showMessageDialog = msg
                        }
                    }
                    editAccountId = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editAccountId = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add New Account") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newAccountName,
                        onValueChange = { newAccountName = it },
                        label = { Text("Account Name") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newAccountBalance,
                        onValueChange = { newAccountBalance = it },
                        label = { Text("Opening Balance") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newAccountType.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            AccountType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text(type.name, fontWeight = FontWeight.Bold)
                                            val desc = when(type) {
                                                AccountType.CASH -> "Liquid funds, included in daily budget."
                                                AccountType.ASSET -> "Physical assets/investments. Excluded from budget."
                                                AccountType.LIABILITY -> "Debts like credit cards. Excluded from budget."
                                                AccountType.CAPITAL -> "Owner equity/savings. Excluded from budget."
                                                AccountType.REVENUE -> "Income/Revenue. Excluded from budget."
                                                AccountType.EXPENSE -> "Expenses/Costs. Excluded from budget."
                                            }
                                            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                        }
                                    },
                                    onClick = { 
                                        newAccountType = type
                                        expanded = false 
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newAccountName.isNotBlank()) {
                        viewModel.createAccount(newAccountName, newAccountType, newAccountBalance.ifBlank { "0.0" }) { success, msg ->
                            showMessageDialog = msg
                        }
                    }
                    showAddDialog = false
                    newAccountName = ""
                    newAccountBalance = ""
                    newAccountType = AccountType.CASH
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showMessageDialog != null) {
        AlertDialog(
            onDismissRequest = { showMessageDialog = null },
            title = { Text("Information") },
            text = { Text(showMessageDialog!!) },
            confirmButton = {
                TextButton(onClick = { showMessageDialog = null }) {
                    Text("OK")
                }
            }
        )
    }
}
