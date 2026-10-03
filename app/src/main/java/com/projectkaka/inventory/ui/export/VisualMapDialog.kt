package com.projectkaka.inventory.ui.export

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.repository.ItemExportRow
import com.projectkaka.inventory.util.GalleryHelper
import com.projectkaka.inventory.util.VisualMapGenerator
import kotlinx.coroutines.launch

/**
 * Interactive dialog allowing users to pick ANY category, all belongings, or enter a custom category
 * to export a visual infographic map as PNG to the phone's gallery.
 */
@JvmName("VisualMapDialogForItems")
@Composable
fun VisualMapDialog(
    items: List<ItemEntity>,
    onDismiss: () -> Unit
) {
    val rows = remember(items) {
        items.map { item ->
            ItemExportRow(
                id = item.id,
                name = item.name,
                category = item.category,
                locationTag = item.locationTag,
                estimatedValue = item.estimatedValue,
                status = item.status.name,
                isDraft = item.isDraft,
                dateAdded = item.dateAdded,
                imagePath = item.imagePath,
                careTasks = emptyList()
            )
        }
    }
    VisualMapDialog(allItems = rows, onDismiss = onDismiss)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualMapDialog(
    allItems: List<ItemExportRow>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val availableCategories = remember(allItems) {
        allItems.map { it.category.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    var selectedMode by remember { mutableStateOf("ALL") } // "ALL", "EXISTING", "CUSTOM"
    var selectedCategory by remember { mutableStateOf(availableCategories.firstOrNull() ?: "Electronics") }
    var customCategoryText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    val effectiveCategory = when (selectedMode) {
        "ALL" -> "All Belongings"
        "EXISTING" -> selectedCategory
        "CUSTOM" -> customCategoryText.trim().ifEmpty { "Custom Map" }
        else -> "All Belongings"
    }

    val matchingItems = remember(allItems, selectedMode, selectedCategory, customCategoryText) {
        when (selectedMode) {
            "ALL" -> allItems
            "EXISTING" -> allItems.filter { it.category.equals(selectedCategory, ignoreCase = true) }
            "CUSTOM" -> {
                val q = customCategoryText.trim()
                if (q.isEmpty()) emptyList()
                else allItems.filter { it.category.contains(q, ignoreCase = true) }
            }
            else -> allItems
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Export Visual Map (PNG)")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Generate a high-resolution visual infographic diagram and save it directly to your phone's Gallery.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Mode Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == "ALL",
                        onClick = { selectedMode = "ALL" },
                        label = { Text("Complete Inventory", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedMode == "EXISTING",
                        onClick = { selectedMode = "EXISTING" },
                        label = { Text("Pick Category", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedMode == "CUSTOM",
                        onClick = { selectedMode = "CUSTOM" },
                        label = { Text("Custom", fontSize = 11.sp) }
                    )
                }

                if (selectedMode == "EXISTING") {
                    Text(
                        text = "Select a Category:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val categoriesToShow = (listOf("Electronics", "Prescriptions & Slips") + availableCategories).distinct()
                        for (cat in categoriesToShow) {
                            val count = allItems.count { it.category.equals(cat, ignoreCase = true) }
                            FilterChip(
                                selected = selectedCategory.equals(cat, ignoreCase = true),
                                onClick = { selectedCategory = cat },
                                label = { Text("$cat ($count)", fontSize = 11.sp) }
                            )
                        }
                    }
                } else if (selectedMode == "CUSTOM") {
                    OutlinedTextField(
                        value = customCategoryText,
                        onValueChange = { customCategoryText = it },
                        label = { Text("Type custom category name") },
                        placeholder = { Text("e.g. Tools, Kitchen, Bedroom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Match summary card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Target: $effectiveCategory",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${matchingItems.size} items",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isGenerating = true
                    scope.launch {
                        val bitmap = VisualMapGenerator.generateVisualMap(effectiveCategory, matchingItems)
                        val safeCategory = effectiveCategory.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
                        val filename = "visual_map_${safeCategory}_${System.currentTimeMillis()}"
                        val uri = GalleryHelper.saveBitmapToGallery(context, bitmap, filename).getOrNull()
                        isGenerating = false
                        if (uri != null) {
                            Toast.makeText(context, "Visual map for '$effectiveCategory' saved to Gallery!", Toast.LENGTH_LONG).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Could not save visual map to Gallery", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isGenerating
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isGenerating) "Exporting..." else "Save to Gallery (PNG)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isGenerating) {
                Text("Cancel")
            }
        }
    )
}
