package com.projectkaka.inventory.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.MainActivity
import com.projectkaka.inventory.search.TerminalExecutor
import com.projectkaka.inventory.search.TerminalResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * TerminalSessionManager: Central coordinator for Kaka Terminal state.
 *
 * Keeps terminal logs, command execution, and floating state synchronized
 * across full-screen mode, in-app floating overlay, and Android system-wide
 * overlay service (floating over other apps).
 */
object TerminalSessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _terminalLogs = MutableStateFlow<List<TerminalLog>>(
        listOf(
            TerminalLog("Welcome to kaka terminal v1.0", LogType.SUCCESS, isBold = true),
            TerminalLog("Type /help for syntax, or 'help/ ?' / 'man ?' for Debits & Credits Handbook.", LogType.INFO),
            TerminalLog("Type 'kaka show alias' or tap [Terminal Manual] above to open the full guide.", LogType.HINT)
        )
    )
    val terminalLogs: StateFlow<List<TerminalLog>> = _terminalLogs.asStateFlow()

    private var terminalExecutor: TerminalExecutor? = null
    private var appContext: Context? = null

    // In-app or system-wide pop state
    private val _isPoppedOut = MutableStateFlow(false)
    val isPoppedOut: StateFlow<Boolean> = _isPoppedOut.asStateFlow()

    // True when the system overlay service is actively running over other apps
    private val _isSystemOverlayActive = MutableStateFlow(false)
    val isSystemOverlayActive: StateFlow<Boolean> = _isSystemOverlayActive.asStateFlow()

    private var pendingPopLaunch = false

    fun consumePendingPopLaunch(): Boolean {
        val pending = pendingPopLaunch
        pendingPopLaunch = false
        return pending
    }

    fun init(app: KakaApplication) {
        appContext = app.applicationContext
        if (terminalExecutor == null) {
            terminalExecutor = TerminalExecutor(app.financeRepository, app, app.preferences)
        }
    }

    fun setSystemOverlayActive(active: Boolean) {
        _isSystemOverlayActive.value = active
    }

    fun setPoppedOut(popped: Boolean) {
        _isPoppedOut.value = popped
    }

    fun clearLogs() {
        _terminalLogs.value = emptyList()
    }

    fun appendLog(log: TerminalLog) {
        _terminalLogs.value = _terminalLogs.value + log
    }

    fun getCommandHistory(): List<String> {
        val app = appContext as? KakaApplication ?: return emptyList()
        return app.preferences.getCommandHistory()
    }

    /**
     * Executes 'pop/' command:
     * - If 'Display over other apps' is granted, launches the system-wide FloatingTerminalService.
     * - If not granted, prompts user and opens Android Settings so they can enable it.
     */
    fun popTerminal(context: Context) {
        _isPoppedOut.value = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
            // Permission granted -> Start system-wide overlay
            FloatingTerminalService.start(context)
            appendLog(TerminalLog("→ Detached terminal into System Floating Overlay.", LogType.SUCCESS))
            appendLog(TerminalLog("  Terminal will now stay visible while using other apps (Pixel/Android).", LogType.INFO))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Permission missing -> Guide user to Android settings
            pendingPopLaunch = true
            appendLog(TerminalLog("⚠️ Android Permission Needed: 'Display over other apps'", LogType.WARNING, isBold = true))
            appendLog(TerminalLog("👉 IMPORTANT: On the next screen, ONLY toggle ON 'Project Kaka'.", LogType.HINT, isBold = true))
            appendLog(TerminalLog("   (You do NOT need to toggle any other apps!)", LogType.HINT))
            appendLog(TerminalLog("Opening Android Settings...", LogType.INFO))
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.fromParts("package", context.packageName, null)
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val fallbackIntent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (e: Exception) {
                    try {
                        val generalIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(generalIntent)
                    } catch (err: Exception) {
                        appendLog(TerminalLog("Could not open settings automatically: ${err.message}", LogType.ERROR))
                    }
                }
            }
        } else {
            FloatingTerminalService.start(context)
        }
    }

    /**
     * Executes 'push/' command:
     * - Stops system overlay service if running.
     * - Docks terminal back into full-screen mode.
     * - Brings MainActivity to front if requested.
     */
    fun dockTerminal(context: Context, bringActivityToFront: Boolean = true) {
        _isPoppedOut.value = false
        FloatingTerminalService.stop(context)
        appendLog(TerminalLog("→ Docking terminal window back into app...", LogType.INFO))

        if (bringActivityToFront) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAVIGATE_TO", "alias")
            }
            context.startActivity(intent)
        }
    }

    fun executeCommand(
        input: String,
        context: Context? = appContext,
        onActionRequested: (String) -> Unit = {}
    ) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        appendLog(TerminalLog("$ $trimmed", LogType.ECHO, isBold = true))

        scope.launch {
            val executor = terminalExecutor ?: run {
                appendLog(TerminalLog("✗ Terminal executor not initialized", LogType.ERROR))
                return@launch
            }

            val result = executor.execute(trimmed)
            when (result) {
                is TerminalResult.Success -> {
                    val clean = result.message.trimStart('✓', ' ')
                    appendLog(TerminalLog("✓ $clean", LogType.SUCCESS))
                }
                is TerminalResult.Failure -> {
                    val clean = result.reason.trimStart('✗', ' ')
                    appendLog(TerminalLog("✗ $clean", LogType.ERROR))
                    if (result.hint != null) {
                        appendLog(TerminalLog("  Hint: ${result.hint}", LogType.HINT))
                    }
                }
                is TerminalResult.NeedsInput -> {
                    appendLog(TerminalLog("? ${result.question}", LogType.WARNING))
                    if (result.options.isNotEmpty()) {
                        appendLog(TerminalLog("  Options: ${result.options.joinToString(", ")}", LogType.HINT))
                    }
                }
                is TerminalResult.Pending -> {
                    // Do nothing
                }
                is TerminalResult.PendingAction -> {
                    when (result.action) {
                        "clear" -> {
                            clearLogs()
                            return@launch
                        }
                        "pop" -> {
                            if (context != null) {
                                popTerminal(context)
                            } else {
                                _isPoppedOut.value = true
                            }
                        }
                        "push" -> {
                            if (context != null) {
                                dockTerminal(context, bringActivityToFront = true)
                            } else {
                                _isPoppedOut.value = false
                            }
                        }
                        else -> {
                            val msg = when (result.action) {
                                "alias" -> "Opening Terminal Manual..."
                                "cmatrix" -> "Initiating Matrix screen..."
                                else -> "Action requested: ${result.action}"
                            }
                            appendLog(TerminalLog("→ $msg", LogType.INFO))
                        }
                    }
                    onActionRequested(result.action)
                }
            }
        }
    }
}
