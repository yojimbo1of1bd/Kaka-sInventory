package com.projectkaka.inventory.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LogType {
    INFO, ECHO, SUCCESS, ERROR, HINT, WARNING
}

data class TerminalLog(
    val text: String,
    val type: LogType,
    val isBold: Boolean = false
)

data class AliasGuideUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<FinancialCategoryEntity> = emptyList(),
    val hiddenAccountIds: Set<Int> = emptySet(),
    val isLoading: Boolean = true,
    val terminalLogs: List<TerminalLog> = listOf(
        TerminalLog("Welcome to kaka terminal v1.0", LogType.SUCCESS, isBold = true),
        TerminalLog("Type /help for syntax, or 'help/ ?' / 'man ?' for Debits & Credits Handbook.", LogType.INFO),
        TerminalLog("Type 'kaka show alias' or tap [Terminal Manual] above to open the full guide.", LogType.HINT)
    )
)

class AliasGuideViewModel(application: Application) : AndroidViewModel(application) {
    private val financeRepo = getApplication<KakaApplication>().financeRepository
    private val prefs = getApplication<KakaApplication>().preferences
    private val terminalExecutor by lazy { com.projectkaka.inventory.search.TerminalExecutor(financeRepo, getApplication(), prefs) }

    fun getCommandHistory(): List<String> = prefs.getCommandHistory()

    private val _terminalLogs = kotlinx.coroutines.flow.MutableStateFlow<List<TerminalLog>>(
        listOf(
            TerminalLog("Welcome to kaka terminal v1.0", LogType.SUCCESS, isBold = true),
            TerminalLog("Type /help for syntax, or 'help/ ?' / 'man ?' for Debits & Credits Handbook.", LogType.INFO),
            TerminalLog("Type 'kaka show alias' or tap [Terminal Manual] above to open the full guide.", LogType.HINT)
        )
    )

    val uiState: StateFlow<AliasGuideUiState> = combine(
        financeRepo.getAllAccounts(),
        financeRepo.getAllCategories(),
        prefs.hiddenAccountIds,
        _terminalLogs
    ) { accounts, categories, hiddenIds, logs ->
        AliasGuideUiState(
            accounts = accounts,
            categories = categories,
            hiddenAccountIds = hiddenIds,
            isLoading = false,
            terminalLogs = logs
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AliasGuideUiState()
    )

    fun toggleAccountVisibility(accountId: Int, isHidden: Boolean) {
        prefs.toggleAccountVisibility(accountId, isHidden)
    }

    fun executeTerminalCommand(input: String, onActionRequested: (String) -> Unit = {}) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return
        
        appendLog(TerminalLog("$ $trimmed", LogType.ECHO, isBold = true))
        
        viewModelScope.launch {
            val result = terminalExecutor.execute(trimmed)
            when (result) {
                is com.projectkaka.inventory.search.TerminalResult.Success -> {
                    val clean = result.message.trimStart('✓', ' ')
                    appendLog(TerminalLog("✓ $clean", LogType.SUCCESS))
                }
                is com.projectkaka.inventory.search.TerminalResult.Failure -> {
                    val clean = result.reason.trimStart('✗', ' ')
                    appendLog(TerminalLog("✗ $clean", LogType.ERROR))
                    if (result.hint != null) {
                        appendLog(TerminalLog("  Hint: ${result.hint}", LogType.HINT))
                    }
                }
                is com.projectkaka.inventory.search.TerminalResult.NeedsInput -> {
                    appendLog(TerminalLog("? ${result.question}", LogType.WARNING))
                    if (result.options.isNotEmpty()) {
                        appendLog(TerminalLog("  Options: ${result.options.joinToString(", ")}", LogType.HINT))
                    }
                }
                is com.projectkaka.inventory.search.TerminalResult.Pending -> {
                    // Do nothing
                }
                is com.projectkaka.inventory.search.TerminalResult.PendingAction -> {
                    val msg = if (result.action == "alias") "Opening Terminal Manual..." else "Action requested: ${result.action}"
                    appendLog(TerminalLog("→ $msg", LogType.INFO))
                    onActionRequested(result.action)
                }
            }
        }
    }
    
    private fun appendLog(log: TerminalLog) {
        _terminalLogs.value = _terminalLogs.value + log
    }
}
