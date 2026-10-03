package com.projectkaka.inventory.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter

// ── Series palette ──
private val SeriesColors = listOf(
    Color(0xFF58A6FF), // Blue
    Color(0xFF39D353), // Green
    Color(0xFFFFBD2E), // Yellow
    Color(0xFFFF5F56), // Red
    Color(0xFFAE87FF), // Purple
    Color(0xFF56D4DD), // Cyan
    Color(0xFFFF9F43), // Orange
    Color(0xFFF368E0), // Pink
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GraphViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
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
                text = "Analytics",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        // ── Timeline Chips ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BucketSize.entries.forEach { filter ->
                FilterChip(
                    selected = state.bucketSize == filter,
                    onClick = { viewModel.setBucketSize(filter) },
                    label = { Text(filter.label) }
                )
            }
        }
        
        // ── Compare By Switch ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Compare by:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Category", color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = state.compareByAccount,
                    onCheckedChange = { viewModel.setCompareMode(it) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Text("Overview", color = MaterialTheme.colorScheme.onBackground)
            }
        }

        // ── Account / Category Multi-select Chips ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.compareByAccount) {
                state.accounts.forEach { acc ->
                    FilterChip(
                        selected = state.selectedAccounts.contains(acc.id),
                        onClick = { viewModel.toggleAccount(acc.id) },
                        label = { Text(acc.name) }
                    )
                }
            } else {
                state.categories.forEach { cat ->
                    FilterChip(
                        selected = state.selectedCategories.contains(cat.id),
                        onClick = { viewModel.toggleCategory(cat.id) },
                        label = { Text(cat.name) }
                    )
                }
            }
        }
        
        // ── Legend ──
        if (state.seriesNames.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.seriesNames.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    SeriesColors[index % SeriesColors.size],
                                    shape = CircleShape
                                )
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
        
        // ── Chart ──
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            if (state.isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else if (state.entryModel == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No data for this selection.",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Try selecting different accounts/categories\nor changing the timeline.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                val horizontalAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                    state.labels[value.toInt()] ?: ""
                }
                
                Chart(
                    chart = lineChart(
                        lines = state.seriesNames.mapIndexed { index, _ ->
                            com.patrykandpatrick.vico.compose.chart.line.lineSpec(
                                lineColor = SeriesColors[index % SeriesColors.size]
                            )
                        }
                    ),
                    model = state.entryModel!!,
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(valueFormatter = horizontalAxisValueFormatter),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
