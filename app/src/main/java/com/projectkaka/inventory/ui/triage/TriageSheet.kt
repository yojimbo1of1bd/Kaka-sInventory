package com.projectkaka.inventory.ui.triage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.triage.DueTask
import java.io.File
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

/**
 * Modal sheet listing every overdue care task. Completing a task resets its
 * timer immediately, which can drop the item out of the due list (and clear
 * the Red Ring) while the sheet is still open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageSheet(
    dueTasks: List<DueTask>,
    onComplete: (CareTaskEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Due Maintenance",
                    color = Color(0xFFE74C3C),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "(${dueTasks.size})",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "These tasks passed their interval. Tap to mark done.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            if (dueTasks.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nothing overdue. The village is calm.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dueTasks, key = { it.task.id }) { due ->
                        DueTaskRow(due = due, onComplete = { onComplete(due.task) })
                    }
                }
            }
            if (dueTasks.isNotEmpty()) {
                val context = LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            dueTasks.forEach { due ->
                                com.projectkaka.inventory.util.CareReminderManager.pushToCalendar(
                                    context,
                                    due.itemName,
                                    due.task.taskName,
                                    due.task.frequencyDays
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Calendar All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            dueTasks.forEach { due ->
                                com.projectkaka.inventory.util.CareReminderManager.postDueNotification(
                                    context,
                                    due.task.id,
                                    due.itemName,
                                    due.task.taskName,
                                    due.daysOverdue
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Notify All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val context = LocalContext.current
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse("https://wa.me/?text=Emergency%3A%20Please%20help%20me%20resolve%20my%20overdue%20tasks%20in%20Project%20Kaka.")
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // WhatsApp not installed
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
            ) {
                Text("Send WhatsApp Emergency", fontWeight = FontWeight.Bold, color = Color.White)
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DueTaskRow(due: DueTask, onComplete: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = File(due.itemImagePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = due.task.taskName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = due.itemName,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Text(
                text = "${due.daysOverdue} day(s) overdue  •  every ${due.task.frequencyDays}d",
                color = Color(0xFFE74C3C),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        androidx.compose.material3.IconButton(
            onClick = {
                com.projectkaka.inventory.util.CareReminderManager.pushToCalendar(
                    context,
                    due.itemName,
                    due.task.taskName,
                    due.task.frequencyDays
                )
            }
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = "Push to Calendar",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        androidx.compose.material3.IconButton(
            onClick = {
                com.projectkaka.inventory.util.CareReminderManager.setAlarm(
                    context,
                    due.itemName,
                    due.task.taskName,
                    9,
                    0
                )
            }
        ) {
            Icon(
                Icons.Default.Alarm,
                contentDescription = "Set Alarm",
                tint = MaterialTheme.colorScheme.secondary
            )
        }

        androidx.compose.material3.IconButton(
            onClick = {
                com.projectkaka.inventory.util.CareReminderManager.postDueNotification(
                    context,
                    due.task.id,
                    due.itemName,
                    due.task.taskName,
                    due.daysOverdue
                )
            }
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = "Post Notification",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        TextButton(onClick = onComplete) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Mark done",
                tint = MaterialTheme.colorScheme.primary
            )
            Text(" Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}
