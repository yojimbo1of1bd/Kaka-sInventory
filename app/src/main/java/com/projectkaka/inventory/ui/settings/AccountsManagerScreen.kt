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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AliasGuideViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editAccountAlias by remember { mutableStateOf<String?>(null) }
    var editAmount by remember { mutableStateOf("") }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newAccountName by remember { mutableStateOf("") }
    var newAccountBalance by remember { mutableStateOf("") }
    var newAccountType by remember { mutableStateOf(AccountType.CASH) }

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
                    text = "Edit starting balances directly. This will not create a transaction record.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
                )
            }

            items(state.accounts) { account ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.name, fontWeight = FontWeight.Bold)
                            Text("Base: ৳${account.openingBalance}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Dashboard", 
                                fontSize = 12.sp, 
                                modifier = Modifier.padding(end = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Switch(
                                checked = !state.hiddenAccountIds.contains(account.id),
                                onCheckedChange = { isVisible ->
                                    viewModel.toggleAccountVisibility(account.id, !isVisible)
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Button(onClick = { 
                                editAccountAlias = account.name
                                editAmount = account.openingBalance.toString()
                            }) {
                                Text("Edit")
                            }
                        }
                    }
                }
            }
        }
    }

    if (editAccountAlias != null) {
        AlertDialog(
            onDismissRequest = { editAccountAlias = null },
            title = { Text("Set Balance for ${editAccountAlias}") },
            text = {
                OutlinedTextField(
                    value = editAmount,
                    onValueChange = { editAmount = it },
                    label = { Text("New Active Balance") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = editAmount.toDoubleOrNull()
                    if (amount != null) {
                        viewModel.initializeAccountBalance(editAccountAlias!!, amount)
                    }
                    editAccountAlias = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editAccountAlias = null }) { Text("Cancel") }
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
                        label = { Text("Initial Balance") },
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
                                    text = { Text(type.name) },
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
                    val bal = newAccountBalance.toDoubleOrNull() ?: 0.0
                    if (newAccountName.isNotBlank()) {
                        viewModel.createAccount(newAccountName, newAccountType, bal)
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
}
