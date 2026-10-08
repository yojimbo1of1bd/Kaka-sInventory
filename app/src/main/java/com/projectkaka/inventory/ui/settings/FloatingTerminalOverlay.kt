package com.projectkaka.inventory.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

/**
 * FloatingTerminalOverlay: In-app draggable, collapsible, and resizable floating terminal window
 * with Infinix-style S/M/L presets and freeform corner resize grip.
 */
@Composable
fun FloatingTerminalOverlay(
    onPush: () -> Unit,
    onOpenGraph: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenStatements: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AliasGuideViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isExpanded by remember { mutableStateOf(true) }
    var terminalInput by remember { mutableStateOf("") }
    var historyIndex by remember { mutableIntStateOf(-1) }

    // Coordinates for dragging
    var offsetX by remember { mutableFloatStateOf(40f) }
    var offsetY by remember { mutableFloatStateOf(160f) }

    // Resizable dimensions (Infinix style)
    var windowWidthDp by remember { mutableStateOf(320.dp) }
    var windowHeightDp by remember { mutableStateOf(300.dp) }
    val density = LocalDensity.current

    val terminalLogs = state.terminalLogs
    val historyList = remember(terminalLogs.size) { viewModel.getCommandHistory() }
    val listState = rememberLazyListState()

    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty() && isExpanded) {
            listState.animateScrollToItem(terminalLogs.size - 1)
        }
    }

    fun submitCommand(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return
        historyIndex = -1

        viewModel.executeTerminalCommand(trimmed) { action ->
            when (action) {
                "push" -> onPush()
                "graph" -> onOpenGraph()
                "ledger" -> onOpenLedger()
                "export" -> onOpenExport()
                "settings" -> onOpenSettings()
                "accounts" -> onOpenAccounts()
                "report" -> onOpenStatements()
            }
        }
        terminalInput = ""
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
    ) {
        if (!isExpanded) {
            // ── Collapsed Floating Pill ──
            Box(
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0D1117))
                    .border(1.5.dp, Color(0xFF39D353), RoundedCornerShape(24.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .clickable { isExpanded = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF39D353), CircleShape)
                    )
                    Text(
                        ">_ Kaka Term",
                        color = Color(0xFF58A6FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = onPush,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.OpenInFull,
                            contentDescription = "Dock Terminal",
                            tint = Color(0xFF8A8A8A),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        } else {
            // ── Expanded Freeform Resizable Floating Window ──
            Card(
                modifier = Modifier
                    .width(windowWidthDp)
                    .height(windowHeightDp)
                    .shadow(16.dp, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF30363D))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        // Header Bar (Window Controls & Title & S/M/L Presets)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        offsetX += dragAmount.x
                                        offsetY += dragAmount.y
                                    }
                                }
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .background(Color(0xFF39D353), CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "kaka-term",
                                    color = Color(0xFF39D353),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // S/M/L Size Presets
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (windowWidthDp < 290.dp) Color(0xFF238636) else Color(0xFF21262D))
                                        .clickable {
                                            windowWidthDp = 260.dp
                                            windowHeightDp = 230.dp
                                        }
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("S", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (windowWidthDp in 290.dp..340.dp) Color(0xFF238636) else Color(0xFF21262D))
                                        .clickable {
                                            windowWidthDp = 320.dp
                                            windowHeightDp = 300.dp
                                        }
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("M", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (windowWidthDp > 340.dp) Color(0xFF238636) else Color(0xFF21262D))
                                        .clickable {
                                            windowWidthDp = 370.dp
                                            windowHeightDp = 460.dp
                                        }
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("L", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Minimize button
                                IconButton(
                                    onClick = { isExpanded = false },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Text(
                                        "—",
                                        color = Color(0xFF8A8A8A),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(2.dp))
                                // Dock button (push to full-screen)
                                IconButton(
                                    onClick = onPush,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.OpenInFull,
                                        contentDescription = "Dock",
                                        tint = Color(0xFF58A6FF),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        // Quick Action Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "/help" to "/help",
                                "f/" to "f/",
                                "snapshot/" to "snapshot/ check",
                                "clear" to "clear/",
                                "push" to "push/"
                            ).forEach { (label, command) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF161B22))
                                        .border(0.5.dp, Color(0xFF30363D), RoundedCornerShape(4.dp))
                                        .clickable { submitCommand(command) }
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = Color(0xFF58A6FF),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Terminal Log Output
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF010409), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF21262D), RoundedCornerShape(8.dp))
                                .padding(6.dp)
                        ) {
                            items(terminalLogs) { log ->
                                val color = when (log.type) {
                                    LogType.INFO -> Color(0xFF8B949E)
                                    LogType.ECHO -> Color(0xFF58A6FF)
                                    LogType.SUCCESS -> Color(0xFF39D353)
                                    LogType.ERROR -> Color(0xFFF85149)
                                    LogType.HINT -> Color(0xFFD29922)
                                    LogType.WARNING -> Color(0xFFDB6D28)
                                }
                                Text(
                                    text = log.text,
                                    color = color,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (log.isBold) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Terminal Input Field + Corner Grip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$",
                                color = Color(0xFF39D353),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(end = 4.dp)
                            )

                            OutlinedTextField(
                                value = terminalInput,
                                onValueChange = { terminalInput = it },
                                placeholder = { Text("cmd (or push/)...", fontSize = 10.sp, color = Color(0xFF6E7681)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFFE6EDF3),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF161B22),
                                    unfocusedContainerColor = Color(0xFF161B22),
                                    focusedBorderColor = Color(0xFF39D353),
                                    unfocusedBorderColor = Color(0xFF30363D)
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submitCommand(terminalInput) })
                            )

                            // Send button
                            IconButton(
                                onClick = { submitCommand(terminalInput) },
                                modifier = Modifier.size(28.dp).padding(start = 2.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = Color(0xFF39D353),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            // Bottom-Right Corner Resize Handle (Freeform drag-to-resize)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dw = (dragAmount.x / density.density).dp
                                            val dh = (dragAmount.y / density.density).dp
                                            windowWidthDp = (windowWidthDp + dw).coerceIn(240.dp, 400.dp)
                                            windowHeightDp = (windowHeightDp + dh).coerceIn(200.dp, 560.dp)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⤡",
                                    color = Color(0xFF58A6FF),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
