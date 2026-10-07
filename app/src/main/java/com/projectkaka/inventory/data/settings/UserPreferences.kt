package com.projectkaka.inventory.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

/**
 * A tiny, fully on-device preference store.
 *
 * SharedPreferences is a single XML file inside the app sandbox — no network, no account,
 * no sync. It is intentionally the *only* thing outside the sovereign `.db` + folders,
 * because a theme flag does not belong in the inventory database.
 *
 * Writes update the in-memory StateFlow synchronously so the UI reacts immediately,
 * and persist with `apply()` so the disk write never blocks the frame.
 */
class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _useBottomNav = MutableStateFlow(prefs.getBoolean(KEY_USE_BOTTOM_NAV, false))
    val useBottomNav: StateFlow<Boolean> = _useBottomNav.asStateFlow()

    private val _appLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_APP_LOCK, false))
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    private val _appPin = MutableStateFlow(prefs.getString(KEY_APP_PIN, "") ?: "")
    val appPin: StateFlow<String> = _appPin.asStateFlow()
    
    private val _appPinHash = MutableStateFlow(prefs.getString(KEY_APP_PIN_HASH, "") ?: "")
    val appPinHash: StateFlow<String> = _appPinHash.asStateFlow()

    private val _appPinSalt = MutableStateFlow(prefs.getString(KEY_APP_PIN_SALT, "") ?: "")
    val appPinSalt: StateFlow<String> = _appPinSalt.asStateFlow()

    private val _appPinIterations = MutableStateFlow(prefs.getInt(KEY_APP_PIN_ITERATIONS, 100000))
    val appPinIterations: StateFlow<Int> = _appPinIterations.asStateFlow()
    
    private val _lockoutAttempts = MutableStateFlow(prefs.getInt(KEY_LOCKOUT_ATTEMPTS, 0))
    val lockoutAttempts: StateFlow<Int> = _lockoutAttempts.asStateFlow()

    private val _lockoutUntil = MutableStateFlow(prefs.getLong(KEY_LOCKOUT_UNTIL, 0L))
    val lockoutUntil: StateFlow<Long> = _lockoutUntil.asStateFlow()

    private val _emergencyContactName = MutableStateFlow(prefs.getString(KEY_EMERGENCY_NAME, "") ?: "")
    val emergencyContactName: StateFlow<String> = _emergencyContactName.asStateFlow()
    
    private val _emergencyContactNumber = MutableStateFlow(prefs.getString(KEY_EMERGENCY_NUMBER, "") ?: "")
    val emergencyContactNumber: StateFlow<String> = _emergencyContactNumber.asStateFlow()
    
    private val _emergencyContactRelation = MutableStateFlow(prefs.getString(KEY_EMERGENCY_RELATION, "") ?: "")
    val emergencyContactRelation: StateFlow<String> = _emergencyContactRelation.asStateFlow()
    
    private val _emergencyContactAvatar = MutableStateFlow(prefs.getString(KEY_EMERGENCY_AVATAR, "") ?: "")
    val emergencyContactAvatar: StateFlow<String> = _emergencyContactAvatar.asStateFlow()

    private val _showQuickLog = MutableStateFlow(prefs.getBoolean(KEY_SHOW_QUICK_LOG, true))
    val showQuickLog: StateFlow<Boolean> = _showQuickLog.asStateFlow()

    private val _defaultQuickLogDebitAccount = MutableStateFlow(prefs.getString(KEY_QL_DEBIT_ACC, "Cash") ?: "Cash")
    val defaultQuickLogDebitAccount: StateFlow<String> = _defaultQuickLogDebitAccount.asStateFlow()

    private val _defaultQuickLogDebitCategory = MutableStateFlow(prefs.getString(KEY_QL_DEBIT_CAT, "Food") ?: "Food")
    val defaultQuickLogDebitCategory: StateFlow<String> = _defaultQuickLogDebitCategory.asStateFlow()

    private val _defaultQuickLogCreditAccount = MutableStateFlow(prefs.getString(KEY_QL_CREDIT_ACC, "Cash") ?: "Cash")
    val defaultQuickLogCreditAccount: StateFlow<String> = _defaultQuickLogCreditAccount.asStateFlow()

    private val _defaultQuickLogCreditCategory = MutableStateFlow(prefs.getString(KEY_QL_CREDIT_CAT, "Family") ?: "Family")
    val defaultQuickLogCreditCategory: StateFlow<String> = _defaultQuickLogCreditCategory.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString(KEY_USER_NAME, "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _overspendStrikeCount = MutableStateFlow(prefs.getInt(KEY_OVERSPEND_STRIKES, 0))
    val overspendStrikeCount: StateFlow<Int> = _overspendStrikeCount.asStateFlow()

    private val _lastStrikeDate = MutableStateFlow(prefs.getString(KEY_LAST_STRIKE_DATE, "") ?: "")
    val lastStrikeDate: StateFlow<String> = _lastStrikeDate.asStateFlow()

    private val _alertsEnabled = MutableStateFlow(prefs.getBoolean(KEY_ALERTS_ENABLED, true))
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled.asStateFlow()

    private val _alertMethod = MutableStateFlow(prefs.getString(KEY_ALERT_METHOD, "whatsapp") ?: "whatsapp")
    val alertMethod: StateFlow<String> = _alertMethod.asStateFlow()

    private val _hiddenAccountIds = MutableStateFlow(
        prefs.getStringSet(KEY_HIDDEN_ACCOUNT_IDS, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    )
    val hiddenAccountIds: StateFlow<Set<Int>> = _hiddenAccountIds.asStateFlow()

    private val _incomeCycleDay = MutableStateFlow(prefs.getInt(KEY_INCOME_CYCLE_DAY, 1))
    val incomeCycleDay: StateFlow<Int> = _incomeCycleDay.asStateFlow()

    private val _businessMode = MutableStateFlow(prefs.getBoolean(KEY_BUSINESS_MODE, false))
    val businessMode: StateFlow<Boolean> = _businessMode.asStateFlow()

    private val _permCamera = MutableStateFlow(prefs.getBoolean(KEY_PERM_CAMERA, true))
    val permCamera: StateFlow<Boolean> = _permCamera.asStateFlow()

    private val _permContacts = MutableStateFlow(prefs.getBoolean(KEY_PERM_CONTACTS, true))
    val permContacts: StateFlow<Boolean> = _permContacts.asStateFlow()

    private val _permSmsCalls = MutableStateFlow(prefs.getBoolean(KEY_PERM_SMS_CALLS, true))
    val permSmsCalls: StateFlow<Boolean> = _permSmsCalls.asStateFlow()

    private val _permMicrophone = MutableStateFlow(prefs.getBoolean(KEY_PERM_MICROPHONE, true))
    val permMicrophone: StateFlow<Boolean> = _permMicrophone.asStateFlow()

    private val _permStorage = MutableStateFlow(prefs.getBoolean(KEY_PERM_STORAGE, true))
    val permStorage: StateFlow<Boolean> = _permStorage.asStateFlow()

    private val _hasSeenDocMode = MutableStateFlow(prefs.getBoolean(KEY_HAS_SEEN_DOC_MODE, false))
    val hasSeenDocMode: StateFlow<Boolean> = _hasSeenDocMode.asStateFlow()

    fun setHasSeenDocMode(seen: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_DOC_MODE, seen).apply()
        _hasSeenDocMode.value = seen
    }

    fun setPermissionEnabled(key: String, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
        when (key) {
            KEY_PERM_CAMERA -> _permCamera.value = enabled
            KEY_PERM_CONTACTS -> _permContacts.value = enabled
            KEY_PERM_SMS_CALLS -> _permSmsCalls.value = enabled
            KEY_PERM_MICROPHONE -> _permMicrophone.value = enabled
            KEY_PERM_STORAGE -> _permStorage.value = enabled
        }
    }


    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name.trim()).apply()
        _userName.value = name.trim()
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setUseBottomNav(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_BOTTOM_NAV, enabled).apply()
        _useBottomNav.value = enabled
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK, enabled).apply()
        _appLockEnabled.value = enabled
    }

    fun setAppPin(pin: String) {
        prefs.edit().putString(KEY_APP_PIN, pin).apply()
        _appPin.value = pin
    }

    fun setAppPinHash(hash: String, salt: String, iterations: Int) {
        prefs.edit()
            .putString(KEY_APP_PIN_HASH, hash)
            .putString(KEY_APP_PIN_SALT, salt)
            .putInt(KEY_APP_PIN_ITERATIONS, iterations)
            .apply()
        _appPinHash.value = hash
        _appPinSalt.value = salt
        _appPinIterations.value = iterations
    }

    fun clearPlaintextAppPin() {
        prefs.edit().remove(KEY_APP_PIN).apply()
        _appPin.value = ""
    }
    
    fun setLockoutAttempts(attempts: Int) {
        prefs.edit().putInt(KEY_LOCKOUT_ATTEMPTS, attempts).apply()
        _lockoutAttempts.value = attempts
    }

    fun setLockoutUntil(timestamp: Long) {
        prefs.edit().putLong(KEY_LOCKOUT_UNTIL, timestamp).apply()
        _lockoutUntil.value = timestamp
    }

    fun setEmergencyContact(name: String, number: String, relation: String, avatarUri: String) {
        prefs.edit()
            .putString(KEY_EMERGENCY_NAME, name)
            .putString(KEY_EMERGENCY_NUMBER, number)
            .putString(KEY_EMERGENCY_RELATION, relation)
            .putString(KEY_EMERGENCY_AVATAR, avatarUri)
            .apply()
        _emergencyContactName.value = name
        _emergencyContactNumber.value = number
        _emergencyContactRelation.value = relation
        _emergencyContactAvatar.value = avatarUri
    }

    fun setShowQuickLog(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_QUICK_LOG, show).apply()
        _showQuickLog.value = show
    }

    fun setDefaultQuickLogDebit(account: String, category: String) {
        prefs.edit().putString(KEY_QL_DEBIT_ACC, account).putString(KEY_QL_DEBIT_CAT, category).apply()
        _defaultQuickLogDebitAccount.value = account
        _defaultQuickLogDebitCategory.value = category
    }

    fun setDefaultQuickLogCredit(account: String, category: String) {
        prefs.edit().putString(KEY_QL_CREDIT_ACC, account).putString(KEY_QL_CREDIT_CAT, category).apply()
        _defaultQuickLogCreditAccount.value = account
        _defaultQuickLogCreditCategory.value = category
    }

    fun incrementOverspendStrike(): Int {
        val today = java.time.LocalDate.now().toString()
        val currentDate = _lastStrikeDate.value
        val newCount = if (currentDate != today) 1 else _overspendStrikeCount.value + 1
        prefs.edit()
            .putInt(KEY_OVERSPEND_STRIKES, newCount)
            .putString(KEY_LAST_STRIKE_DATE, today)
            .apply()
        _overspendStrikeCount.value = newCount
        _lastStrikeDate.value = today
        return newCount
    }

    fun resetOverspendStrikes() {
        prefs.edit().putInt(KEY_OVERSPEND_STRIKES, 0).apply()
        _overspendStrikeCount.value = 0
    }

    fun setAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALERTS_ENABLED, enabled).apply()
        _alertsEnabled.value = enabled
    }

    fun setAlertMethod(method: String) {
        prefs.edit().putString(KEY_ALERT_METHOD, method).apply()
        _alertMethod.value = method
    }

    fun toggleAccountVisibility(accountId: Int, isHidden: Boolean) {
        val currentSet = _hiddenAccountIds.value.toMutableSet()
        if (isHidden) {
            currentSet.add(accountId)
        } else {
            currentSet.remove(accountId)
        }
        prefs.edit().putStringSet(KEY_HIDDEN_ACCOUNT_IDS, currentSet.map { it.toString() }.toSet()).apply()
        _hiddenAccountIds.value = currentSet
    }

    fun setBusinessMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUSINESS_MODE, enabled).apply()
        _businessMode.value = enabled
    }

    fun setIncomeCycleDay(day: Int) {
        val clamped = day.coerceIn(1, 31)
        prefs.edit().putInt(KEY_INCOME_CYCLE_DAY, clamped).apply()
        _incomeCycleDay.value = clamped
    }

    // ── Cashout Charge Rates ──
    fun setAccountChargeRate(accountName: String, ratePercent: Double) {
        val key = "charge_rate_" + accountName.trim().lowercase()
        prefs.edit().putFloat(key, ratePercent.toFloat()).apply()
    }

    fun getAccountChargeRate(accountName: String): Double? {
        val key = "charge_rate_" + accountName.trim().lowercase()
        if (!prefs.contains(key)) return null
        return prefs.getFloat(key, 0f).toDouble()
    }

    fun removeAccountChargeRate(accountName: String) {
        val key = "charge_rate_" + accountName.trim().lowercase()
        prefs.edit().remove(key).apply()
    }

    fun getAllAccountChargeRates(): Map<String, Double> {
        val prefix = "charge_rate_"
        val result = mutableMapOf<String, Double>()
        prefs.all.forEach { (k, v) ->
            if (k.startsWith(prefix)) {
                val accName = k.removePrefix(prefix)
                val rate = when (v) {
                    is Float -> v.toDouble()
                    is Double -> v
                    is Number -> v.toDouble()
                    else -> null
                }
                if (rate != null) result[accName] = rate
            }
        }
        return result
    }

    // ── Terminal History ──
    fun getCommandHistory(): List<String> {
        val raw = prefs.getString("terminal_command_history", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun addCommandToHistory(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return
        val current = getCommandHistory().toMutableList()
        if (current.lastOrNull() == trimmed) return // Avoid duplicate consecutive
        current.add(trimmed)
        if (current.size > 100) {
            current.removeAt(0)
        }
        prefs.edit().putString("terminal_command_history", current.joinToString("\n")).apply()
    }

    private fun loadThemeMode(): ThemeMode {
        val modeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(modeStr ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    private companion object {
        const val PREFS_NAME = "kaka_prefs"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_USE_BOTTOM_NAV = "use_bottom_nav"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_APP_PIN = "app_pin"
        const val KEY_EMERGENCY_NAME = "emergency_name"
        const val KEY_EMERGENCY_NUMBER = "emergency_number"
        const val KEY_EMERGENCY_RELATION = "emergency_relation"
        const val KEY_EMERGENCY_AVATAR = "emergency_avatar"
        const val KEY_SHOW_QUICK_LOG = "show_quick_log"
        const val KEY_QL_DEBIT_ACC = "ql_debit_acc"
        const val KEY_QL_DEBIT_CAT = "ql_debit_cat"
        const val KEY_QL_CREDIT_ACC = "ql_credit_acc"
        const val KEY_QL_CREDIT_CAT = "ql_credit_cat"
        const val KEY_OVERSPEND_STRIKES = "overspend_strikes"
        const val KEY_LAST_STRIKE_DATE = "last_strike_date"
        const val KEY_USER_NAME = "user_name"
        const val KEY_ALERTS_ENABLED = "alerts_enabled"
        const val KEY_ALERT_METHOD = "alert_method"
        const val KEY_HIDDEN_ACCOUNT_IDS = "hidden_account_ids"
        const val KEY_PERM_CAMERA = "perm_camera"
        const val KEY_PERM_CONTACTS = "perm_contacts"
        const val KEY_PERM_SMS_CALLS = "perm_sms_calls"
        const val KEY_PERM_MICROPHONE = "perm_microphone"
        const val KEY_PERM_STORAGE = "perm_storage"
        const val KEY_APP_PIN_HASH = "app_pin_hash"
        const val KEY_APP_PIN_SALT = "app_pin_salt"
        const val KEY_APP_PIN_ITERATIONS = "app_pin_iterations"
        const val KEY_LOCKOUT_ATTEMPTS = "lockout_attempts"
        const val KEY_LOCKOUT_UNTIL = "lockout_until"
        const val KEY_BUSINESS_MODE = "business_mode"
        const val KEY_HAS_SEEN_DOC_MODE = "has_seen_doc_mode"
        const val KEY_INCOME_CYCLE_DAY = "income_cycle_day"
    }
}
