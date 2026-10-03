package com.projectkaka.inventory.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.local.entity.ItemStatus
import com.projectkaka.inventory.data.triage.DueTask
import com.projectkaka.inventory.ui.liquidate.LiquidationDialog
import com.projectkaka.inventory.ui.triage.MaintenanceDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.projectkaka.inventory.util.GalleryHelper

@Composable
fun ItemDetailScreen(
    itemId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel = viewModel()
) {
    LaunchedEffect(itemId) { viewModel.load(itemId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showMaintenanceDialog by remember { mutableStateOf(false) }
    var showLiquidationDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val item = state.item
        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (state.loading) "Loading…" else "Item not found.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            DetailContent(
                item = item,
                tasks = state.tasks,
                now = System.currentTimeMillis(),
                onBack = onBack,
                onAddTask = { showMaintenanceDialog = true },
                onCompleteTask = { viewModel.completeTask(it) },
                onDeleteTask = { viewModel.deleteTask(it) },
                onLiquidate = { showLiquidationDialog = true },
                onMove = { showMoveDialog = true },
                onDownload = {
                    val result = GalleryHelper.saveImageToGallery(context, item.imagePath, item.name)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Saved WebP image to Gallery in Pictures/ProjectKaka", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                },
                onDelete = { showDeleteDialog = true }
            )
        }
    }

    state.item?.let { item ->
        if (showMoveDialog) {
            MoveItemDialog(
                item = item,
                onDismiss = { showMoveDialog = false },
                onConfirm = { loc, cat ->
                    viewModel.moveItem(loc, cat)
                    showMoveDialog = false
                    Toast.makeText(context, "Item moved to $loc", Toast.LENGTH_SHORT).show()
                }
            )
        }
        if (showMaintenanceDialog) {
            MaintenanceDialog(
                item = item,
                onDismiss = { showMaintenanceDialog = false },
                onConfirm = { taskName, frequency ->
                    viewModel.addTask(taskName, frequency)
                    showMaintenanceDialog = false
                }
            )
        }
        if (showLiquidationDialog) {
            LiquidationDialog(
                item = item,
                onDismiss = { showLiquidationDialog = false },
                onConfirm = { status, recovered ->
                    viewModel.liquidate(item, status, recovered)
                    showLiquidationDialog = false
                }
            )
        }
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Item", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to permanently delete this item? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteItem()
                            showDeleteDialog = false
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun DetailContent(
    item: ItemEntity,
    tasks: List<CareTaskEntity>,
    now: Long,
    onBack: () -> Unit,
    onAddTask: () -> Unit,
    onCompleteTask: (CareTaskEntity) -> Unit,
    onDeleteTask: (CareTaskEntity) -> Unit,
    onLiquidate: () -> Unit,
    onMove: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // ---- Header ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Item Detail",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = "Save Image to Gallery",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onMove) {
                    Icon(
                        Icons.Default.DriveFileMove,
                        contentDescription = "Move Item",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ---- Photo ----
        AsyncImage(
            model = File(item.imagePath),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Column(Modifier.padding(16.dp)) {

            Text(
                text = item.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "${item.category}  •  ${item.locationTag.ifBlank { "no location" }}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
            Text(
                text = "Status: ${item.status}   |   Value: ${item.estimatedValue}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
            Text(
                text = "Added ${formatDate(item.dateAdded)}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onMove,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DriveFileMove, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Move Item")
                }
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Save Image")
                }
            }

            if (item.status == ItemStatus.ACTIVE) {
                OutlinedButton(
                    onClick = onLiquidate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Liquidate Item")
                }
                Spacer(Modifier.height(20.dp))
            }

            // ---- Care tasks ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Care Tasks",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onAddTask) {
                    Text(
                        "+ Add",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (tasks.isEmpty()) {
                Text(
                    text = "No maintenance scheduled.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tasks.forEach { task ->
                        TaskRow(
                            task = task,
                            overdueDays = DueTask.overdueDays(task, now),
                            onComplete = { onCompleteTask(task) },
                            onDelete = { onDeleteTask(task) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TaskRow(
    task: CareTaskEntity,
    overdueDays: Int,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val overdue = overdueDays > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = task.taskName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (overdue) "$overdueDays day(s) overdue  •  every ${task.frequencyDays}d"
                else "Every ${task.frequencyDays} day(s)  •  on track",
                color = if (overdue) Color(0xFFE74C3C) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = onComplete) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Mark done",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete task",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

@Composable
private fun MoveItemDialog(
    item: ItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (newLocation: String, newCategory: String) -> Unit
) {
    var location by remember { mutableStateOf(item.locationTag) }
    var category by remember { mutableStateOf(item.category) }
    val commonLocations = listOf("Living Room", "Master Bedroom", "Kitchen", "Home Office", "Storage", "Balcony")
    val commonCategories = listOf("Electronics", "Prescriptions & Slips", "Clothing", "Academic", "Tools", "Furniture")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Item", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Update Location / Room", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(commonLocations) { loc ->
                        FilterChip(
                            selected = location.equals(loc, ignoreCase = true),
                            onClick = { location = loc },
                            label = { Text(loc, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Update Category", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(commonCategories) { cat ->
                        FilterChip(
                            selected = category.equals(cat, ignoreCase = true),
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(location, category) }
            ) { Text("Save & Move") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

