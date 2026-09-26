package com.projectkaka.inventory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.settings.ThemeMode
import com.projectkaka.inventory.ui.capture.CaptureScreen
import com.projectkaka.inventory.ui.dashboard.DashboardScreen
import com.projectkaka.inventory.ui.detail.ItemDetailScreen
import com.projectkaka.inventory.ui.drafts.DraftScreen
import com.projectkaka.inventory.ui.export.ExportScreen
import com.projectkaka.inventory.ui.settings.SettingsScreen
import com.projectkaka.inventory.ui.settings.AliasGuideScreen
import com.projectkaka.inventory.ui.splash.SplashScreen
import com.projectkaka.inventory.ui.theme.KakaTheme
import com.projectkaka.inventory.ui.dashboard.GraphScreen
import com.projectkaka.inventory.ui.liquidate.LedgerScreen

object Routes {
    const val SPLASH = "splash"
    const val DASHBOARD = "dashboard"
    const val CAPTURE = "capture"
    const val DRAFTS = "drafts"
    const val EXPORT = "export"
    const val SETTINGS = "settings"
    const val GRAPH = "graph"
    const val ALIAS = "alias"
    const val LEDGER = "ledger"
    const val ACCOUNTS_MANAGER = "accounts_manager"
    const val EMERGENCY_CONTACT = "emergency_contact"
    const val ITEM_DETAIL = "item/{itemId}"

    fun itemDetail(itemId: Int) = "item/$itemId"
}

@Composable
fun KakaApp() {
    val app = LocalContext.current.applicationContext as KakaApplication
    val preferences = remember { app.preferences }
    val themeMode by preferences.themeMode.collectAsState()
    val useBottomNav by preferences.useBottomNav.collectAsState()
    val appLockEnabled by preferences.appLockEnabled.collectAsState()
    val appPin by preferences.appPin.collectAsState()
    val appPinHash by preferences.appPinHash.collectAsState()
    val emergencyContactName by preferences.emergencyContactName.collectAsState()
    val emergencyContactNumber by preferences.emergencyContactNumber.collectAsState()
    val emergencyContactRelation by preferences.emergencyContactRelation.collectAsState()
    val emergencyContactAvatar by preferences.emergencyContactAvatar.collectAsState()
    val showQuickLog by preferences.showQuickLog.collectAsState()
    val defaultQuickLogDebitAccount by preferences.defaultQuickLogDebitAccount.collectAsState()
    val defaultQuickLogDebitCategory by preferences.defaultQuickLogDebitCategory.collectAsState()
    val defaultQuickLogCreditAccount by preferences.defaultQuickLogCreditAccount.collectAsState()
    val defaultQuickLogCreditCategory by preferences.defaultQuickLogCreditCategory.collectAsState()
    val userName by preferences.userName.collectAsState()
    val alertsEnabled by preferences.alertsEnabled.collectAsState()
    val alertMethod by preferences.alertMethod.collectAsState()
    val permCamera by preferences.permCamera.collectAsState()
    val permContacts by preferences.permContacts.collectAsState()
    val permSmsCalls by preferences.permSmsCalls.collectAsState()
    val permMicrophone by preferences.permMicrophone.collectAsState()
    val permStorage by preferences.permStorage.collectAsState()
    
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // The theme reads the persisted flag, so an appearance change repaints the whole tree
    // instantly and survives process death.
    KakaTheme(darkTheme = isDark) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.SPLASH) {

            composable(Routes.SPLASH) {
                SplashScreen(
                    onInitializationComplete = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    useBottomNav = useBottomNav,
                    userName = userName,
                    onUserNameSet = { preferences.setUserName(it) },
                    onOpenCapture = { navController.navigate(Routes.CAPTURE) },
                    onOpenDrafts = { navController.navigate(Routes.DRAFTS) },
                    onOpenExport = { navController.navigate(Routes.EXPORT) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenItem = { itemId -> navController.navigate(Routes.itemDetail(itemId)) },
                    onOpenGraph = { navController.navigate(Routes.GRAPH) },
                    onOpenAlias = { navController.navigate(Routes.ALIAS) },
                    onOpenLedger = { navController.navigate(Routes.LEDGER) }
                )
            }

            composable(Routes.CAPTURE) {
                CaptureScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.DRAFTS) {
                DraftScreen(onDone = { navController.popBackStack() })
            }

            composable(Routes.EXPORT) {
                ExportScreen(
                    permStorage = permStorage,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { preferences.setThemeMode(it) },
                    useBottomNav = useBottomNav,
                    onUseBottomNavChange = { preferences.setUseBottomNav(it) },
                    appLockEnabled = appLockEnabled,
                    onAppLockChange = { preferences.setAppLockEnabled(it) },
                    isPinSet = appPin.isNotEmpty() || appPinHash.isNotEmpty(),
                    onAppPinChange = { 
                        val salt = com.projectkaka.inventory.util.CryptoUtils.generateSalt()
                        val hash = com.projectkaka.inventory.util.CryptoUtils.hashPin(it, salt, 100000)
                        preferences.setAppPinHash(hash, salt, 100000)
                        preferences.clearPlaintextAppPin()
                    },
                    showQuickLog = showQuickLog,
                    onShowQuickLogChange = { preferences.setShowQuickLog(it) },
                    defaultDebitAcc = defaultQuickLogDebitAccount,
                    defaultDebitCat = defaultQuickLogDebitCategory,
                    onDefaultDebitChange = { acc, cat -> preferences.setDefaultQuickLogDebit(acc, cat) },
                    defaultCreditAcc = defaultQuickLogCreditAccount,
                    defaultCreditCat = defaultQuickLogCreditCategory,
                    onDefaultCreditChange = { acc, cat -> preferences.setDefaultQuickLogCredit(acc, cat) },
                    alertsEnabled = alertsEnabled,
                    onAlertsEnabledChange = { preferences.setAlertsEnabled(it) },
                    alertMethod = alertMethod,
                    onAlertMethodChange = { preferences.setAlertMethod(it) },
                    permCamera = permCamera,
                    onPermCameraChange = { preferences.setPermissionEnabled("perm_camera", it) },
                    permContacts = permContacts,
                    onPermContactsChange = { preferences.setPermissionEnabled("perm_contacts", it) },
                    permSmsCalls = permSmsCalls,
                    onPermSmsCallsChange = { preferences.setPermissionEnabled("perm_sms_calls", it) },
                    permMicrophone = permMicrophone,
                    onPermMicrophoneChange = { preferences.setPermissionEnabled("perm_microphone", it) },
                    permStorage = permStorage,
                    onPermStorageChange = { preferences.setPermissionEnabled("perm_storage", it) },
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS_MANAGER) },
                    onOpenEmergencyContact = { navController.navigate(Routes.EMERGENCY_CONTACT) },
                    onOpenExport = { navController.navigate(Routes.EXPORT) },
                    onOpenTerminal = { navController.navigate(Routes.ALIAS) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.ITEM_DETAIL,
                arguments = listOf(navArgument("itemId") { type = NavType.IntType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getInt("itemId") ?: return@composable
                ItemDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ACCOUNTS_MANAGER) {
                com.projectkaka.inventory.ui.settings.AccountsManagerScreen(onBack = { navController.popBackStack() })
            }
            
            composable(Routes.EMERGENCY_CONTACT) {
                com.projectkaka.inventory.ui.settings.EmergencyContactScreen(
                    currentName = emergencyContactName,
                    currentNumber = emergencyContactNumber,
                    currentRelation = emergencyContactRelation,
                    currentAvatar = emergencyContactAvatar,
                    permStorage = permStorage,
                    onSave = { name, number, relation, avatar -> 
                        preferences.setEmergencyContact(name, number, relation, avatar)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.GRAPH) {
                GraphScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.ALIAS) {
                AliasGuideScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.LEDGER) {
                LedgerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
