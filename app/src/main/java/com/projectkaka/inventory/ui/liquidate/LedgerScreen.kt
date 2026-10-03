package com.projectkaka.inventory.ui.liquidate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.LedgerType
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LedgerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permContacts by viewModel.permContacts.collectAsStateWithLifecycle()
    val permSmsCalls by viewModel.permSmsCalls.collectAsStateWithLifecycle()
    val permMicrophone by viewModel.permMicrophone.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("Entries") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Liability Ledger", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Entry", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Owed to me", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text("৳${"%.2f".format(state.totalReceivable.minorUnits / 100.0)}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("I owe", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text("৳${"%.2f".format(state.totalPayable.minorUnits / 100.0)}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                SegmentedButton(
                    selected = viewMode == "Entries",
                    onClick = { viewMode = "Entries" },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Entries") }
                SegmentedButton(
                    selected = viewMode == "Contacts",
                    onClick = { viewMode = "Contacts" },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Contacts") }
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Search by name, alias, or phone") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                SegmentedButton(
                    selected = state.filterSettled == false,
                    onClick = { viewModel.updateFilterSettled(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Text("Unsettled") }
                SegmentedButton(
                    selected = state.filterSettled == true,
                    onClick = { viewModel.updateFilterSettled(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Text("Settled") }
                SegmentedButton(
                    selected = state.filterSettled == null,
                    onClick = { viewModel.updateFilterSettled(null) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Text("All") }
            }

            if (viewMode == "Entries") {
                if (state.entries.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No unsettled liabilities.", color = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.entries, key = { it.id }) { entry ->
                            LedgerEntryCard(
                                entry = entry,
                                accounts = state.accounts,
                                permSmsCalls = permSmsCalls,
                                onSettle = { e, accountId -> viewModel.markSettled(e, accountId) },
                                onDelete = { viewModel.deleteEntry(it) }
                            )
                        }
                    }
                }
            } else {
                if (state.summaries.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No contacts found.", color = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.summaries, key = { it.contactName }) { summary ->
                            ContactSummaryCard(
                                summary = summary,
                                onClick = {
                                    viewModel.updateSearchQuery(summary.contactName)
                                    viewMode = "Entries"
                                }
                            )
                        }
                    }
                }
            }
        }
        
        if (showAddDialog) {
            AddLedgerEntryDialog(
                accounts = state.accounts,
                permContacts = permContacts,
                permMicrophone = permMicrophone,
                onDismiss = { showAddDialog = false },
                onConfirm = { name, phone, amount, type, note, accountId, dueDate ->
                    viewModel.addEntry(name, phone, amount, type, note, accountId, dueDate)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
private fun LedgerEntryCard(
    entry: LedgerEntryEntity,
    accounts: List<com.projectkaka.inventory.data.local.entity.AccountEntity>,
    permSmsCalls: Boolean,
    onSettle: (LedgerEntryEntity, Int) -> Unit,
    onDelete: (LedgerEntryEntity) -> Unit
) {
    val isReceivable = entry.type == LedgerType.RECEIVABLE
    val color = if (isReceivable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.contactName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = if (isReceivable) "Owes me ৳${"%.2f".format(entry.amount.minorUnits / 100.0)}" else "I owe ৳${"%.2f".format(entry.amount.minorUnits / 100.0)}",
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (entry.note.isNotBlank()) {
                    Text(entry.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                // Show created date
                val dateStr = Instant.ofEpochMilli(entry.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate().toString()
                Text("Added: $dateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            if (entry.contactPhone.isNotBlank() && isReceivable) {
                IconButton(onClick = {
                    if (!permSmsCalls) {
                        android.widget.Toast.makeText(context, "SMS & Calls disabled. Enable in Settings -> Privacy.", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        val rawPhone = entry.contactPhone.replace(Regex("[^0-9+]"), "")
                        val phone = if (rawPhone.startsWith("0")) "+88$rawPhone" else rawPhone
                        val message = "Hi ${entry.contactName}, just a gentle reminder regarding the ৳${"%.2f".format(entry.amount.minorUnits / 100.0)} that is due. Let me know when you can settle it. Thanks!"
                        val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Remind via WhatsApp", tint = Color(0xFF25D366))
                }
            }
            var showSettleDialog by remember { mutableStateOf(false) }
            
            IconButton(onClick = { showSettleDialog = true }) {
                Icon(Icons.Default.Check, contentDescription = "Settle", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { onDelete(entry) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }

            if (showSettleDialog) {
                var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
                AlertDialog(
                    onDismissRequest = { showSettleDialog = false },
                    title = { Text("Settle Liability") },
                    text = {
                        Column {
                            Text("Select account for this settlement:")
                            Spacer(Modifier.height(8.dp))
                            accounts.forEach { acc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedAccount = acc }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedAccount?.id == acc.id,
                                        onClick = { selectedAccount = acc }
                                    )
                                    Text(acc.name, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            selectedAccount?.let { onSettle(entry, it.id) }
                            showSettleDialog = false
                        }) { Text("Settle") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSettleDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

@Composable
private fun ContactSummaryCard(
    summary: com.projectkaka.inventory.data.local.dao.ContactSummaryRow,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(summary.contactName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                
                val amountText = if (summary.netOutstanding.minorUnits >= 0) {
                    "Owes me ৳${"%.2f".format(summary.netOutstanding.minorUnits / 100.0)}"
                } else {
                    "I owe ৳${"%.2f".format(-summary.netOutstanding.minorUnits / 100.0)}"
                }
                val color = if (summary.netOutstanding.minorUnits >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                
                Text(
                    text = amountText,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text("${summary.entryCount} active entries", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                
                if (summary.earliestDueDate != null) {
                    val dateStr = Instant.ofEpochMilli(summary.earliestDueDate)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate().toString()
                    Text("Earliest due: $dateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLedgerEntryDialog(
    accounts: List<com.projectkaka.inventory.data.local.entity.AccountEntity>,
    permContacts: Boolean,
    permMicrophone: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, LedgerType, String, Int, Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isReceivable by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    
    val context = LocalContext.current
    
    // ── Contact Permission & Picker ──
    // Track whether we have permission
    var hasContactPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Contact picker result handler
    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            try {
                // Step 1: Get contact ID and display name from the picked URI
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.HAS_PHONE_NUMBER
                    ),
                    null, null, null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                    
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx) ?: ""
                    }
                    
                    // Step 2: Query phone number using contact ID
                    if (hasPhoneIdx >= 0 && idIdx >= 0 && cursor.getInt(hasPhoneIdx) > 0) {
                        val contactId = cursor.getString(idIdx)
                        val phoneCursor = context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId),
                            null
                        )
                        if (phoneCursor != null && phoneCursor.moveToFirst()) {
                            val numberIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (numberIdx >= 0) {
                                phone = phoneCursor.getString(numberIdx) ?: ""
                            }
                            phoneCursor.close()
                        }
                    }
                    cursor.close()
                }
            } catch (e: SecurityException) {
                // Permission was revoked between check and use
                Toast.makeText(context, "Contact access denied. Please grant permission in Settings.", Toast.LENGTH_LONG).show()
                hasContactPermission = false
            } catch (e: Exception) {
                Toast.makeText(context, "Could not read contact: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactPermission = granted
        if (granted) {
            // Permission just granted — launch the picker immediately
            pickContactLauncher.launch(null)
        } else {
            Toast.makeText(context, "Contact permission is required to pick a contact.", Toast.LENGTH_LONG).show()
        }
    }
    
    // ── Voice Input (Speech-to-Text) ──
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                note = if (note.isBlank()) spoken else "$note $spoken"
            }
        }
    }
    
    fun launchVoiceInput() {
        if (!permMicrophone) {
            Toast.makeText(context, "Microphone disabled. Enable in Settings -> Privacy.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictate your note…")
        }
        // Check if a speech recognizer exists on this device
        if (intent.resolveActivity(context.packageManager) != null) {
            speechLauncher.launch(intent)
        } else {
            // Fallback: prompt user to install Google Speech Services
            Toast.makeText(
                context,
                "Speech recognizer not found. Please install Google Speech Services from the Play Store.",
                Toast.LENGTH_LONG
            ).show()
            try {
                val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.tts"))
                context.startActivity(storeIntent)
            } catch (_: Exception) {
                // Play Store not available — silently fail
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Liability", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    SegmentedButton(
                        selected = isReceivable,
                        onClick = { isReceivable = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Owed to me") }
                    SegmentedButton(
                        selected = !isReceivable,
                        onClick = { isReceivable = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("I owe") }
                }
                
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Contact Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            if (!permContacts) {
                                Toast.makeText(context, "Contacts permission disabled. Enable in Settings -> Privacy.", Toast.LENGTH_LONG).show()
                                return@IconButton
                            }
                            // Gate the contact picker behind a runtime permission check
                            if (hasContactPermission) {
                                pickContactLauncher.launch(null)
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "Import from Contacts", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (Optional)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )
                
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (৳)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )
                
                Text("Account", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    accounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAccountId = acc.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = acc.id }
                            )
                            Text(acc.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                
                // Note field with voice input button
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = false,
                    maxLines = 3,
                    trailingIcon = {
                        IconButton(onClick = { launchVoiceInput() }) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                )
                
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(dueDate?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    } ?: "Set Due Date (Optional)")
                }
                
                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState()
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                dueDate = datePickerState.selectedDateMillis
                                showDatePicker = false
                            }) { Text("OK") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && amountStr.isNotBlank() && selectedAccountId != null) {
                        onConfirm(name, phone, amountStr, if (isReceivable) LedgerType.RECEIVABLE else LedgerType.PAYABLE, note, selectedAccountId!!, dueDate)
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
