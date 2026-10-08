package com.projectkaka.inventory.ui.settings

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.projectkaka.inventory.MainActivity
import com.projectkaka.inventory.R
import kotlin.math.roundToInt

/**
 * FloatingTerminalService: Android System Alert Window Service.
 *
 * Keeps the Kaka Terminal floating on top of the entire Android OS
 * (Pixel 7 Pro / all Android devices) even when Project Kaka is minimized
 * or when the user switches to other apps.
 */
class FloatingTerminalService : Service() {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        startForegroundNotification()
        setupOverlayView()
        TerminalSessionManager.setSystemOverlayActive(true)
    }

    private fun startForegroundNotification() {
        val channelId = "floating_terminal_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Kaka Floating Terminal",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active status for floating terminal overlay"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("NAVIGATE_TO", "alias")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Kaka Terminal Overlay Active")
            .setContentText("Tap to return to full-screen terminal in Project Kaka")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (_: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupOverlayView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Initially non-focusable so background apps receive touches normally
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }
        layoutParams = params

        val owner = OverlayLifecycleOwner()
        owner.onCreate()
        lifecycleOwner = owner

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                FloatingSystemTerminalContent(
                    onDragDelta = { dx, dy ->
                        params.x += dx.roundToInt()
                        params.y += dy.roundToInt()
                        windowManager?.updateViewLayout(this, params)
                    },
                    onToggleFocusable = { focusable ->
                        if (focusable) {
                            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        } else {
                            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        }
                        windowManager?.updateViewLayout(this, params)
                    },
                    onDockToApp = {
                        TerminalSessionManager.dockTerminal(this@FloatingTerminalService, bringActivityToFront = true)
                    },
                    onClose = {
                        TerminalSessionManager.setPoppedOut(false)
                        stopSelf()
                    }
                )
            }
        }

        composeView = view
        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onDestroy() {
        TerminalSessionManager.setSystemOverlayActive(false)
        composeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        lifecycleOwner?.onDestroy()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 9021

        fun start(context: Context) {
            val intent = Intent(context, FloatingTerminalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(intent)
                } catch (_: Exception) {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingTerminalService::class.java)
            context.stopService(intent)
        }
    }
}

/**
 * Floating UI rendered by ComposeView inside the System Alert Window.
 */
@Composable
fun FloatingSystemTerminalContent(
    onDragDelta: (Float, Float) -> Unit,
    onToggleFocusable: (Boolean) -> Unit,
    onDockToApp: () -> Unit,
    onClose: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var terminalInput by remember { mutableStateOf("") }
    val terminalLogs by TerminalSessionManager.terminalLogs.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty() && isExpanded) {
            listState.animateScrollToItem(terminalLogs.size - 1)
        }
    }

    // When expanding, make window focusable so keyboard appears for typing
    LaunchedEffect(isExpanded) {
        onToggleFocusable(isExpanded)
    }

    var currentWidthDp by remember { mutableStateOf(320.dp) }
    var currentHeightDp by remember { mutableStateOf(300.dp) }

    if (!isExpanded) {
        // ── Collapsed Hovering Pill ──
        Box(
            modifier = Modifier
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFF0D1117))
                .border(1.dp, Color(0xFF30363D), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                }
                .clickable { isExpanded = true }
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(Color(0xFF39D353), CircleShape)
                )
                Text(
                    text = ">_ Kaka Term",
                    color = Color(0xFF58A6FF),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    } else {
        // ── Expanded Freeform Resizable Floating Window (Infinix style) ──
        val density = androidx.compose.ui.platform.LocalDensity.current

        Card(
            modifier = Modifier
                .width(currentWidthDp)
                .height(currentHeightDp)
                .shadow(16.dp, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header (Draggable Handle + S/M/L Size Presets)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161B22))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onDragDelta(dragAmount.x, dragAmount.y)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF39D353), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "kaka-term",
                                color = Color(0xFFC9D1D9),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        // Size Presets (Infinix Hovering Display Presets)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // S Preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (currentWidthDp < 290.dp) Color(0xFF238636) else Color(0xFF21262D))
                                    .clickable {
                                        currentWidthDp = 260.dp
                                        currentHeightDp = 230.dp
                                    }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("S", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }

                            // M Preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (currentWidthDp in 290.dp..340.dp) Color(0xFF238636) else Color(0xFF21262D))
                                    .clickable {
                                        currentWidthDp = 320.dp
                                        currentHeightDp = 300.dp
                                    }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("M", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }

                            // L Preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (currentWidthDp > 340.dp) Color(0xFF238636) else Color(0xFF21262D))
                                    .clickable {
                                        currentWidthDp = 370.dp
                                        currentHeightDp = 460.dp
                                    }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("L", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }

                        // Action Controls
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Minimize to Pill
                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Minimize",
                                    tint = Color(0xFF8B949E),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Dock Back to Project Kaka
                            IconButton(
                                onClick = onDockToApp,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Dock",
                                    tint = Color(0xFF58A6FF),
                                    modifier = Modifier.size(13.dp)
                                )
                            }

                            // Close Overlay
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFFF85149),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Quick Action Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1117))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
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
                                    .clickable {
                                        TerminalSessionManager.executeCommand(command)
                                    }
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

                    // Terminal Output Area
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
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
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = if (log.isBold) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }

                    // Command Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161B22))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$ ",
                            color = Color(0xFF39D353),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        OutlinedTextField(
                            value = terminalInput,
                            onValueChange = { terminalInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color(0xFFC9D1D9),
                                unfocusedTextColor = Color(0xFFC9D1D9)
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            placeholder = {
                                Text(
                                    "f/, snapshot/, /help",
                                    color = Color(0xFF484F58),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (terminalInput.isNotBlank()) {
                                        val cmd = terminalInput
                                        terminalInput = ""
                                        TerminalSessionManager.executeCommand(cmd)
                                    }
                                }
                            )
                        )

                        IconButton(
                            onClick = {
                                if (terminalInput.isNotBlank()) {
                                    val cmd = terminalInput
                                    terminalInput = ""
                                    TerminalSessionManager.executeCommand(cmd)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color(0xFF39D353),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Bottom-Right Corner Resize Grip (Freeform drag-to-resize like Infinix hovering window)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val dw = (dragAmount.x / density.density).dp
                                        val dh = (dragAmount.y / density.density).dp
                                        currentWidthDp = (currentWidthDp + dw).coerceIn(240.dp, 400.dp)
                                        currentHeightDp = (currentHeightDp + dh).coerceIn(200.dp, 560.dp)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⤡",
                                color = Color(0xFF58A6FF),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
