package com.projectkaka.inventory.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkaka.inventory.data.settings.ThemeMode

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    useBottomNav: Boolean,
    onUseBottomNavChange: (Boolean) -> Unit,
    appLockEnabled: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    isPinSet: Boolean,
    onAppPinChange: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenEmergencyContact: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenBaskets: () -> Unit,
    alertsEnabled: Boolean,
    onAlertsEnabledChange: (Boolean) -> Unit,
    alertMethod: String,
    onAlertMethodChange: (String) -> Unit,
    permCamera: Boolean,
    onPermCameraChange: (Boolean) -> Unit,
    permContacts: Boolean,
    onPermContactsChange: (Boolean) -> Unit,
    permSmsCalls: Boolean,
    onPermSmsCallsChange: (Boolean) -> Unit,
    permMicrophone: Boolean,
    onPermMicrophoneChange: (Boolean) -> Unit,
    permStorage: Boolean,
    onPermStorageChange: (Boolean) -> Unit,
    businessMode: Boolean,
    onBusinessModeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showGuide by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {

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
                text = "Settings",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Theme Appearance ──
            SectionHeader("Theme Appearance", "Appearance is stored locally on this device.")
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeButton(
                    text = "System",
                    selected = themeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
                ThemeButton(
                    text = "Light",
                    selected = themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ThemeButton(
                    text = "Dark",
                    selected = themeMode == ThemeMode.DARK,
                    onClick = { onThemeModeChange(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Bottom Navigation ──
            SettingsToggle(
                title = "Bottom Navigation",
                subtitle = "Move primary controls to the bottom of the screen.",
                checked = useBottomNav,
                onCheckedChange = onUseBottomNavChange
            )

            Spacer(Modifier.height(24.dp))
            
            // ── Business Mode ──
            SettingsToggle(
                title = "Business Mode",
                subtitle = "Enable accrual accounting accounts (Prepaid, Unearned Revenue, AR, AP).",
                checked = businessMode,
                onCheckedChange = onBusinessModeChange
            )

            Spacer(Modifier.height(24.dp))
            
            // ── Biometric App Lock ──
            SettingsToggle(
                title = "Biometric App Lock",
                subtitle = "Require fingerprint/PIN to open the app.",
                checked = appLockEnabled,
                onCheckedChange = { checked ->
                    if (checked && !isPinSet) {
                        showPinDialog = true
                    } else {
                        onAppLockChange(checked)
                    }
                }
            )
            
            if (showPinDialog) {
                PinSetupDialog(
                    onDismiss = { showPinDialog = false },
                    onPinSet = { pin ->
                        onAppPinChange(pin)
                        onAppLockChange(true)
                        showPinDialog = false
                    }
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            // ── Moving & Transportation (Baskets & Cartons) ──
            SectionHeader("Moving & Transportation", "Organize items into boxes, carts, or cartons with scannable QR/barcodes.")
            OutlinedButton(onClick = onOpenBaskets, modifier = Modifier.padding(top = 8.dp)) {
                Text("Manage Moving Cartons & Baskets")
            }

            Spacer(Modifier.height(24.dp))
            
            // ── Accounts Management ──
            SectionHeader("Accounts Management", "Set initial balances and configure aliases.")
            OutlinedButton(onClick = onOpenAccounts, modifier = Modifier.padding(top = 8.dp)) {
                Text("Manage Accounts")
            }

            Spacer(Modifier.height(24.dp))
            
            // ── Emergency Contact ──
            SectionHeader("Emergency Contact", "Configure the contact called during a spending crisis.")
            OutlinedButton(onClick = onOpenEmergencyContact, modifier = Modifier.padding(top = 8.dp)) {
                Text("Set Emergency Contact")
            }
            
            Spacer(Modifier.height(24.dp))
            
            // ══════════════════════════════════════════════════════════════
            //  ALERTS SECTION (NEW)
            // ══════════════════════════════════════════════════════════════
            AlertsSection(
                alertsEnabled = alertsEnabled,
                onAlertsEnabledChange = onAlertsEnabledChange,
                alertMethod = alertMethod,
                onAlertMethodChange = onAlertMethodChange
            )

            Spacer(Modifier.height(24.dp))
            
            // ── Backup & Export ──
            SectionHeader("Backup & Export", "Export your data to CSV or JSON. Import from backup files.")
            OutlinedButton(onClick = onOpenExport, modifier = Modifier.padding(top = 8.dp)) {
                Text("Export / Import Data")
            }

            Spacer(Modifier.height(24.dp))
            
            // ══════════════════════════════════════════════════════════════
            //  PERMISSIONS (KILL-SWITCHES)
            // ══════════════════════════════════════════════════════════════
            SectionHeader("Privacy & Permissions", "App-level kill switches for sensitive capabilities.")
            Spacer(Modifier.height(8.dp))
            SettingsToggle(
                title = "Camera",
                subtitle = "Allow taking photos for items",
                checked = permCamera,
                onCheckedChange = onPermCameraChange
            )
            SettingsToggle(
                title = "Contacts",
                subtitle = "Allow picking emergency or ledger contacts",
                checked = permContacts,
                onCheckedChange = onPermContactsChange
            )
            SettingsToggle(
                title = "SMS & Phone Calls",
                subtitle = "Allow launching dialer or SMS apps",
                checked = permSmsCalls,
                onCheckedChange = onPermSmsCallsChange
            )
            SettingsToggle(
                title = "Microphone",
                subtitle = "Allow voice-to-text input",
                checked = permMicrophone,
                onCheckedChange = onPermMicrophoneChange
            )
            SettingsToggle(
                title = "Storage & Media",
                subtitle = "Allow browsing local images and backups",
                checked = permStorage,
                onCheckedChange = onPermStorageChange
            )

            Spacer(Modifier.height(24.dp))
            


            Spacer(Modifier.height(32.dp))
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
//  ALERTS SECTION — Call, SMS, WhatsApp options
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun AlertsSection(
    alertsEnabled: Boolean,
    onAlertsEnabledChange: (Boolean) -> Unit,
    alertMethod: String,
    onAlertMethodChange: (String) -> Unit
) {
    SectionHeader(
        title = "Automated Overspend Alerts",
        subtitle = "Automatically notify your emergency contact when overspending is detected."
    )
    Spacer(Modifier.height(8.dp))
    
    SettingsToggle(
        title = "Enable Auto-Alerts",
        subtitle = "Send messages when spending strikes hit maximum limit.",
        checked = alertsEnabled,
        onCheckedChange = onAlertsEnabledChange
    )
    
    if (alertsEnabled) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Alert Method",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeButton(
                text = "SMS",
                selected = alertMethod == "SMS",
                onClick = { onAlertMethodChange("SMS") },
                modifier = Modifier.weight(1f)
            )
            ThemeButton(
                text = "WhatsApp",
                selected = alertMethod == "WHATSAPP",
                onClick = { onAlertMethodChange("WHATSAPP") },
                modifier = Modifier.weight(1f)
            )
            ThemeButton(
                text = "Call",
                selected = alertMethod == "CALL",
                onClick = { onAlertMethodChange("CALL") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}


// ══════════════════════════════════════════════════════════════════════════
//  REUSABLE COMPONENTS
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = subtitle,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        fontSize = 12.sp
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                fontSize = 12.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}





@Composable
private fun ThemeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(text, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onPinSet: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (step == 1) "Set Security PIN" else "Confirm PIN", color = MaterialTheme.colorScheme.primary)
        },
        text = {
            Column {
                Text(
                    text = "This PIN will be used as a fallback if biometric authentication fails.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = if (step == 1) pin else confirmPin,
                    onValueChange = { 
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            if (step == 1) pin = it else confirmPin = it
                            error = null
                        }
                    },
                    label = { Text("Enter PIN (4-6 digits)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    singleLine = true
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (step == 1) {
                        if (pin.length < 4) {
                            error = "PIN must be at least 4 digits"
                        } else {
                            step = 2
                        }
                    } else {
                        if (pin == confirmPin) {
                            onPinSet(pin)
                        } else {
                            error = "PINs do not match"
                        }
                    }
                }
            ) {
                Text(if (step == 1) "Next" else "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}



