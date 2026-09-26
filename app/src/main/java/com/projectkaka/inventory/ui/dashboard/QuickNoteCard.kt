package com.projectkaka.inventory.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectkaka.inventory.data.local.entity.CategoryType
import com.projectkaka.inventory.ui.settings.AliasGuideViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteCard(
    defaultDebitAcc: String,
    defaultDebitCat: String,
    defaultCreditAcc: String,
    defaultCreditCat: String,
    onLogDebit: (String, String, String) -> Unit,
    onLogCredit: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AliasGuideViewModel = viewModel()
) {
    var noteText by remember { mutableStateOf("") }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    var selectedAccount by remember { mutableStateOf(defaultDebitAcc) }
    var selectedCategory by remember { mutableStateOf(defaultDebitCat) }
    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    
    // Filter categories by type for display
    val expenseCategories = state.categories.filter { it.type == CategoryType.EXPENSE }
    
    // Update selections when defaults change
    LaunchedEffect(defaultDebitAcc, defaultDebitCat) {
        selectedAccount = defaultDebitAcc
        selectedCategory = defaultDebitCat
    }
    
    // Auto-correct if the selected category is invalid (e.g. "general" or "Uncategorized Expense")
    LaunchedEffect(expenseCategories, selectedCategory) {
        if (expenseCategories.isNotEmpty() && expenseCategories.none { it.name == selectedCategory }) {
            selectedCategory = expenseCategories.first().name
        }
    }

    Card(
        modifier = modifier.fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp,
            hoveredElevation = 10.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Quick Log", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. 500 for lunch") },
                trailingIcon = {
                    IconButton(onClick = { /* STT Not Implemented Yet */ }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Input")
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedAccount,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                        state.accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc.name) }, onClick = { selectedAccount = acc.name; accountExpanded = false })
                        }
                    }
                }
                
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        expenseCategories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat.name) }, onClick = { selectedCategory = cat.name; categoryExpanded = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            onLogDebit(noteText, selectedAccount, selectedCategory)
                            noteText = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log as Debit", color = MaterialTheme.colorScheme.onError)
                }
                
                Button(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            // If they kept the debit defaults, flip to credit defaults
                            val acc = if (selectedAccount == defaultDebitAcc) defaultCreditAcc else selectedAccount
                            val cat = if (selectedCategory == defaultDebitCat) defaultCreditCat else selectedCategory
                            onLogCredit(noteText, acc, cat)
                            noteText = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Log as Credit", color = Color.White)
                }
            }
        }
    }
}
