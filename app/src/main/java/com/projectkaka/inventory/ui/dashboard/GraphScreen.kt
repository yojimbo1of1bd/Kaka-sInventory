package com.projectkaka.inventory.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.projectkaka.inventory.data.local.entity.TransactionType

// ── Curated Series palette ──
private val SeriesColors = listOf(
    Color(0xFF58A6FF), // Neon Blue
    Color(0xFF39D353), // Electric Green
    Color(0xFFFFBD2E), // Bright Gold
    Color(0xFFFF5F56), // Crimson Coral
    Color(0xFFAE87FF), // Lavender Purple
    Color(0xFF56D4DD), // Aqua Cyan
    Color(0xFFFF9F43), // Vivid Tangerine
    Color(0xFFF368E0), // Hot Magenta
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GraphViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFullscreenModal by remember { mutableStateOf(false) }
    var showDetailBottomSheet by remember { mutableStateOf(false) }

    // When a bucket is selected, open the detail sheet
    LaunchedEffect(state.selectedBucketIndex) {
        if (state.selectedBucketIndex != null) {
            showDetailBottomSheet = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // ── Top App Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
                    text = "Financial Analytics",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // "Pop it up" Fullscreen Expand Action
            IconButton(
                onClick = { showFullscreenModal = true },
                enabled = state.entryModel != null
            ) {
                Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = "Expand Chart Fullscreen",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ── Financial Summary Card ──
        if (state.entryModel != null) {
            SummaryMetricCard(
                state = state,
                onInspectLatest = {
                    val lastIdx = state.bucketDetails.size - 1
                    if (lastIdx >= 0) {
                        viewModel.selectBucket(lastIdx)
                        showDetailBottomSheet = true
                    }
                }
            )
        }

        // ── Timeline Granularity Pills (Daily, Weekly, Monthly) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BucketSize.entries.forEach { size ->
                FilterChip(
                    selected = state.bucketSize == size,
                    onClick = { viewModel.setBucketSize(size) },
                    label = { Text(size.label) }
                )
            }
        }

        // ── Analytics Mode Selector: Overview | Accounts | Categories ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "View:",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = 8.dp)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalyticsMode.entries.forEach { mode ->
                    val isSelected = state.analyticsMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .clickable { viewModel.setAnalyticsMode(mode) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Filter Chips (Multi-Select Accounts or Categories) ──
        AnimatedVisibility(visible = state.analyticsMode != AnalyticsMode.OVERVIEW) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.analyticsMode == AnalyticsMode.ACCOUNTS) {
                    state.accounts.forEach { acc ->
                        FilterChip(
                            selected = state.selectedAccounts.contains(acc.id),
                            onClick = { viewModel.toggleAccount(acc.id) },
                            label = { Text(acc.name) }
                        )
                    }
                } else if (state.analyticsMode == AnalyticsMode.CATEGORIES) {
                    state.categories.forEach { cat ->
                        FilterChip(
                            selected = state.selectedCategories.contains(cat.id),
                            onClick = { viewModel.toggleCategory(cat.id) },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }
        }

        // ── Series Legend ──
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
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // ── Main Chart Canvas (Fixed Green Monolith & Truncation) ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
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
                            val color = SeriesColors[index % SeriesColors.size]
                            com.patrykandpatrick.vico.compose.chart.line.lineSpec(
                                lineColor = color,
                                lineThickness = 3.dp,
                                lineBackgroundShader = null, // Fixed: NO MORE GIANT GREEN WALL!
                                point = shapeComponent(
                                    shape = Shapes.pillShape,
                                    color = color
                                ),
                                pointSize = 8.dp
                            )
                        }
                    ),
                    model = state.entryModel!!,
                    startAxis = rememberStartAxis(
                        itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 5)
                    ),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = horizontalAxisValueFormatter,
                        guideline = null
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Interactive Timeline Scrubber Strip ("Tap to Inspect Date") ──
        if (state.bucketDetails.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Interactive Timeline (Tap a date to inspect)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${state.bucketDetails.size} points",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.bucketDetails.forEach { detail ->
                        val isSelected = state.selectedBucketIndex == detail.index
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .clickable {
                                    viewModel.selectBucket(detail.index)
                                    showDetailBottomSheet = true
                                }
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = detail.dateLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = detail.netWorth.format(),
                                    fontSize = 10.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Drill-down Interactive Bottom Sheet ──
    if (showDetailBottomSheet && state.selectedBucketDetail != null) {
        val detail = state.selectedBucketDetail!!
        ModalBottomSheet(
            onDismissRequest = {
                showDetailBottomSheet = false
                viewModel.clearSelectedBucket()
            }
        ) {
            BucketDetailSheetContent(
                detail = detail,
                onDismiss = {
                    showDetailBottomSheet = false
                    viewModel.clearSelectedBucket()
                }
            )
        }
    }

    // ── "Pop It Up" Fullscreen Interactive Modal Dialog ──
    if (showFullscreenModal && state.entryModel != null) {
        Dialog(
            onDismissRequest = { showFullscreenModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Expanded Interactive View",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Pannable & Unconstrained Canvas",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showFullscreenModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Horizontal scrolling container with generous room (no ellipses!)
                    val unconstrainedWidth = maxOf(480.dp, (state.labels.size * 80).dp)
                    val horizontalScrollState = rememberScrollState()

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontalScrollState)
                                .padding(16.dp)
                        ) {
                            val horizontalAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                                state.labels[value.toInt()] ?: ""
                            }

                            Chart(
                                chart = lineChart(
                                    lines = state.seriesNames.mapIndexed { index, _ ->
                                        val color = SeriesColors[index % SeriesColors.size]
                                        com.patrykandpatrick.vico.compose.chart.line.lineSpec(
                                            lineColor = color,
                                            lineThickness = 3.5.dp,
                                            lineBackgroundShader = null,
                                            point = shapeComponent(
                                                shape = Shapes.pillShape,
                                                color = color
                                            ),
                                            pointSize = 10.dp
                                        )
                                    }
                                ),
                                model = state.entryModel!!,
                                startAxis = rememberStartAxis(
                                    itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 6)
                                ),
                                bottomAxis = rememberBottomAxis(
                                    valueFormatter = horizontalAxisValueFormatter,
                                    guideline = null
                                ),
                                modifier = Modifier
                                    .width(unconstrainedWidth)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Legend & Quick Tap Selector inside modal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.bucketDetails.forEach { detail ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.selectBucket(detail.index)
                                    showDetailBottomSheet = true
                                }
                            ) {
                                Text("${detail.dateLabel}: ${detail.netWorth.format()}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(
    state: GraphUiState,
    onInspectLatest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when (state.analyticsMode) {
                        AnalyticsMode.OVERVIEW -> "Current Net Worth"
                        AnalyticsMode.ACCOUNTS -> if (state.seriesNames.size == 1) "${state.seriesNames.first()} Balance" else "Selected Accounts Balance"
                        AnalyticsMode.CATEGORIES -> if (state.seriesNames.size == 1) "${state.seriesNames.first()} Spend" else "Total Spending"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.summaryMetrics.endBalance.format(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Net Change Pill (+/-)
            val isPositive = state.summaryMetrics.netChange.minorUnits >= 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isPositive) Color(0xFF2EA043).copy(alpha = 0.15f) else Color(0xFFDA3633).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = if (isPositive) Color(0xFF2EA043) else Color(0xFFDA3633),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${if (isPositive) "+" else ""}${state.summaryMetrics.netChange.format()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isPositive) Color(0xFF2EA043) else Color(0xFFDA3633)
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                IconButton(
                    onClick = onInspectLatest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Inspect Details",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BucketDetailSheetContent(
    detail: BucketDetail,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // Date Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = detail.fullDateRange,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Point #${detail.index + 1} Snapshot Breakdown",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Macro Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("Net Worth", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(detail.netWorth.format(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("Total Assets", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(detail.totalAssets.format(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Per-Account Balances on this date
        Text(
            text = "Account Balances on this date",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))

        detail.accountBalances.forEach { (accName, bal) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(accName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(bal.format(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Transactions in this bucket
        Text(
            text = "Transactions in this period (${detail.transactions.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))

        if (detail.transactions.isEmpty()) {
            Text(
                text = "No direct transactions in this period. Balances carry forward.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(detail.transactions) { tx ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = tx.note,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${tx.accountName} • ${tx.formattedTime}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val isIncome = !tx.isCredit && tx.type != TransactionType.DEBT_SETTLE
                            Text(
                                text = "${if (isIncome) "+" else "-"}${tx.amount.format()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncome) Color(0xFF2EA043) else Color(0xFFDA3633)
                            )
                        }
                    }
                }
            }
        }
    }
}
