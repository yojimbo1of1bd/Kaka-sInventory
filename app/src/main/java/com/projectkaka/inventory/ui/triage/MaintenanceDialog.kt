package com.projectkaka.inventory.ui.triage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkaka.inventory.data.local.entity.ItemEntity

/**
 * Attaches a recurring care task to an item, e.g. "Put in sunlight" every 7 days.
 * This is the write side of the triage system; the sheet is the read side.
 */
@Composable
fun MaintenanceDialog(
    item: ItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (taskName: String, frequencyDays: Int) -> Unit
) {
    var taskName by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("") }

    var pushToCalendar by remember { mutableStateOf(false) }
    var setAlarmReminder by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val parsedFrequency = frequency.toIntOrNull()
    val canConfirm = taskName.isNotBlank() && parsedFrequency != null && parsedFrequency > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Care Task",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    label = { Text("Task (e.g. Put in sunlight)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = frequency,
                    onValueChange = { frequency = it.filter(Char::isDigit) },
                    label = { Text("Repeat every N days") },
                    singleLine = true,
                    isError = frequency.isNotEmpty() && parsedFrequency == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (frequency.isNotEmpty() && (parsedFrequency == null || parsedFrequency <= 0)) {
                    Text(
                        text = "Enter a whole number of days greater than zero.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Reminders & Notifications", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = pushToCalendar,
                        onCheckedChange = { pushToCalendar = it }
                    )
                    Text("Push to Phone Calendar", fontSize = 13.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = setAlarmReminder,
                        onCheckedChange = { setAlarmReminder = it }
                    )
                    Text("Set Alarm (9:00 AM)", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirm,
                onClick = {
                    val freq = parsedFrequency ?: 1
                    if (pushToCalendar) {
                        com.projectkaka.inventory.util.CareReminderManager.pushToCalendar(context, item.name, taskName, freq)
                    }
                    if (setAlarmReminder) {
                        com.projectkaka.inventory.util.CareReminderManager.setAlarm(context, item.name, taskName, 9, 0)
                    }
                    onConfirm(taskName, freq)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Add", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
