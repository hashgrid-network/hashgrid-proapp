package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppNavTab
import com.example.ui.MiningViewModel
import com.example.ui.UiEvent
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.HashGridTheme
import com.example.ui.theme.ObsidianBg
import kotlinx.coroutines.flow.collectLatest

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.data.notification.NotificationHelper

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ui.components.security.*

class MainActivity : AppCompatActivity() {

    private val viewModel: MiningViewModel by viewModels()

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    // Physical screen turned off / locked: Trigger smart app lock
                    viewModel.lockApp()
                }
            } catch (e: Throwable) {
                Log.w("MainActivity", "Error in screenOffReceiver: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safe FLAG_SECURE application: Only enable in Release mode (!BuildConfig.DEBUG)
        // In Debug mode, keep FLAG_SECURE disabled so streaming emulator preview displays properly without a black screen
        try {
            if (!BuildConfig.DEBUG) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }
        } catch (e: Throwable) {
            Log.w("MainActivity", "Failed to apply FLAG_SECURE: ${e.message}")
        }

        enableEdgeToEdge()

        try {
            NotificationHelper.createNotificationChannels(this)
        } catch (e: Throwable) {
            Log.w("MainActivity", "NotificationHelper init note: ${e.message}")
        }

        // Safe registration of smart screen off receiver
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenOffReceiver, filter)
            }
        } catch (e: Throwable) {
            Log.w("MainActivity", "screenOffReceiver registration error: ${e.message}")
        }

        setContent {
            HashGridTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguardManager?.isKeyguardLocked == true) {
                viewModel.lockApp()
            }
        } catch (e: Throwable) {
            Log.w("MainActivity", "Keyguard check note: ${e.message}")
        }
        // Note: If user minimized app (e.g. checked WhatsApp/browser) without locking the phone screen,
        // ACTION_SCREEN_OFF did not fire and isKeyguardLocked is false, so app remains unlocked upon return!
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Throwable) {}
    }
}

@Composable
fun MainApp(
    viewModel: MiningViewModel = viewModel()
) {
    val context = LocalContext.current
    val userState by viewModel.userState.collectAsStateWithLifecycle()

    // Request Notification Permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Notification permission state
    }

    LaunchedEffect(Unit) {
        NotificationHelper.createNotificationChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val cryptoPrices by viewModel.cryptoPrices.collectAsStateWithLifecycle()
    val gridPriceUsd by viewModel.gridPriceUsd.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()

    val showDeposit by viewModel.showDepositDialog.collectAsStateWithLifecycle()
    val showWithdraw by viewModel.showWithdrawDialog.collectAsStateWithLifecycle()
    val showLuckyWheel by viewModel.showLuckyWheelDialog.collectAsStateWithLifecycle()
    val showCalculator by viewModel.showCalculatorDialog.collectAsStateWithLifecycle()
    val showMicroTask by viewModel.showMicroTaskDialog.collectAsStateWithLifecycle()
    val showVideoPromo by viewModel.showVideoPromoDialog.collectAsStateWithLifecycle()
    val showHowItWorks by viewModel.showHowItWorksDialog.collectAsStateWithLifecycle()
    val showTaskPolicy by viewModel.showTaskPolicyDialog.collectAsStateWithLifecycle()

    // Security & Web3 States
    val showSecretKeyBackup by viewModel.showSecretKeyBackupModal.collectAsStateWithLifecycle()
    val showSecretKeyRestore by viewModel.showSecretKeyRestoreModal.collectAsStateWithLifecycle()
    val showPinSetup by viewModel.showPinSetupModal.collectAsStateWithLifecycle()
    val showAdminControlHub by viewModel.showAdminControlHubDialog.collectAsStateWithLifecycle()
    val isRestoringAccount by viewModel.isRestoringAccount.collectAsStateWithLifecycle()

    // NOWPayments State
    val activePayment by viewModel.activePaymentSession.collectAsStateWithLifecycle()
    val showGatewayModal by viewModel.showPaymentGatewayModal.collectAsStateWithLifecycle()
    val showSuccessDialog by viewModel.showPaymentSuccessDialog.collectAsStateWithLifecycle()
    val lastConfirmedPayment by viewModel.lastConfirmedPayment.collectAsStateWithLifecycle()
    val isCheckingStatus by viewModel.isCheckingPaymentStatus.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is UiEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                is UiEvent.RigPurchased -> snackbarHostState.showSnackbar("Deployed ${event.rigName}! Monthly yield activated.")
                is UiEvent.SpinCompleted -> snackbarHostState.showSnackbar("Won ${event.record.rewardTitle}! Credited to balance.")
                is UiEvent.PaymentConfirmed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (!userState.isAuthenticated || userState.secretKey.isBlank()) {
        WelcomeAuthScreen(
            isLoading = isRestoringAccount,
            onCreateAccount = { viewModel.createNewAccount() },
            onRestoreAccount = { key -> viewModel.restoreAccountWithSecretKey(key) }
        )
        return
    }

    BackHandler(enabled = currentTab != AppNavTab.HOME) {
        viewModel.selectTab(AppNavTab.HOME)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp)
            )
        },
        topBar = {
            TopHeaderBar(
                nodeId = userState.nodeId,
                isColdStorageSynced = userState.isColdStorageSynced,
                preLaunchPriceUsd = gridPriceUsd,
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab,
                onTabSelected = { tab -> viewModel.selectTab(tab) },
                isMiningActive = userState.isFreeMiningActive
            )
        },
        containerColor = ObsidianBg,
        contentColor = androidx.compose.ui.graphics.Color.White
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentTab,
                label = "TabCrossfade"
            ) { tab ->
                when (tab) {
                    AppNavTab.HOME -> HomeScreen(
                        userState = userState,
                        gridPriceUsd = gridPriceUsd,
                        onNavigateToTab = { viewModel.selectTab(it) },
                        onOpenDeposit = { viewModel.showDepositDialog.value = true },
                        onOpenWithdraw = { viewModel.showWithdrawDialog.value = true },
                        onOpenLuckyWheel = { viewModel.showLuckyWheelDialog.value = true },
                        onOpenCalculator = { viewModel.showCalculatorDialog.value = true },
                        onOpenHowItWorks = { viewModel.showHowItWorksDialog.value = true }
                    )
                    AppNavTab.RIGS_STORE -> RigsStoreScreen(
                        userState = userState,
                        onBuyRig = { rig -> viewModel.buyRig(rig) },
                        onPayWithNowPayments = { rig, payCurrency ->
                            viewModel.initiateNowPaymentsRigPurchase(rig, payCurrency)
                        },
                        onOpenDeposit = { viewModel.showDepositDialog.value = true }
                    )
                    AppNavTab.CLOUD_MINER -> CloudMinerScreen(
                        userState = userState,
                        gridPriceUsd = gridPriceUsd,
                        onStartMining = { viewModel.startFreeMining() },
                        onOpenLuckyWheel = { viewModel.showLuckyWheelDialog.value = true },
                        onNavigateToNetwork = { viewModel.selectTab(AppNavTab.NETWORK) },
                        onNavigateToRigsStore = { viewModel.selectTab(AppNavTab.RIGS_STORE) }
                    )
                    AppNavTab.NETWORK -> NetworkScreen(
                        userState = userState,
                        onSimulateDownlinePurchase = { viewModel.simulateDownlinePurchase() },
                        onSimulateNewReferral = { viewModel.simulateNewReferral() }
                    )
                    AppNavTab.PROFILE -> ProfileScreen(
                        userState = userState,
                        currentGridPrice = gridPriceUsd,
                        onUpdateGridPrice = { newPrice -> viewModel.updateGridPrice(newPrice) },
                        onOpenDeposit = { viewModel.showDepositDialog.value = true },
                        onOpenWithdraw = { viewModel.showWithdrawDialog.value = true },
                        onOpenLuckyWheel = { viewModel.showLuckyWheelDialog.value = true },
                        onOpenCalculator = { viewModel.showCalculatorDialog.value = true },
                        onOpenMicroTask = { viewModel.showMicroTaskDialog.value = true },
                        onOpenVideoPromo = { viewModel.showVideoPromoDialog.value = true },
                        onOpenHowItWorks = { viewModel.showHowItWorksDialog.value = true },
                        onOpenTaskPolicy = { viewModel.showTaskPolicyDialog.value = true },
                        onApprovePendingTasks = { viewModel.approvePendingTasks() },
                        onOpenSecretKeyBackup = { viewModel.showSecretKeyBackupModal.value = true },
                        onOpenSecretKeyRestore = { viewModel.showSecretKeyRestoreModal.value = true },
                        onOpenPinSetup = { viewModel.showPinSetupModal.value = true },
                        onToggleBiometric = { viewModel.toggleBiometric(it) },
                        onLockAppNow = { viewModel.lockApp() },
                        onOpenAdminControlHub = { viewModel.showAdminControlHubDialog.value = true },
                        onLogout = { viewModel.logout() }
                    )
                }
            }
        }
    }

    // Common Dialogs & Modals
    if (showDeposit) {
        DepositDialog(
            onDismiss = { viewModel.showDepositDialog.value = false },
            onInitiateNowPayments = { amount, payCurrency ->
                viewModel.initiateNowPaymentsDeposit(amount, payCurrency)
            },
            onConfirmDeposit = { amount, network -> viewModel.depositFunds(amount, network) }
        )
    }

    if (showWithdraw) {
        WithdrawalDialog(
            currentBalanceUsdt = userState.minerBalanceUsdt,
            onDismiss = { viewModel.showWithdrawDialog.value = false },
            onConfirmWithdraw = { amount, address, network -> viewModel.requestWithdrawal(amount, address, network) }
        )
    }

    if (showLuckyWheel) {
        LuckyWheelDialog(
            isSpinReady = userState.isSpinReady(),
            cooldownSeconds = userState.spinCooldownRemainingSeconds(),
            spinHistory = userState.spinHistory,
            onSpinWin = { sector -> viewModel.onWheelSpinResult(sector) },
            onDismiss = { viewModel.showLuckyWheelDialog.value = false }
        )
    }

    if (showCalculator) {
        ProfitCalculatorDialog(
            onDismiss = { viewModel.showCalculatorDialog.value = false },
            gridPriceUsd = gridPriceUsd,
            onDeployNode = { viewModel.selectTab(AppNavTab.RIGS_STORE) }
        )
    }

    if (showMicroTask) {
        MicroTaskSubmissionDialog(
            onDismiss = { viewModel.showMicroTaskDialog.value = false },
            onSubmit = { platform, initViews, finalViews, notes ->
                viewModel.submitMicroTask(platform, initViews, finalViews, notes)
            }
        )
    }

    if (showVideoPromo) {
        VideoPromotionDialog(
            onDismiss = { viewModel.showVideoPromoDialog.value = false },
            onSubmit = { platform, url, channel ->
                viewModel.submitVideoPromo(platform, url, channel)
            }
        )
    }

    if (showHowItWorks) {
        HowItWorksDialog(
            onDismiss = { viewModel.showHowItWorksDialog.value = false }
        )
    }

    if (showTaskPolicy) {
        TaskPolicyDialog(
            onDismiss = { viewModel.showTaskPolicyDialog.value = false }
        )
    }

    // 👑 Super Admin Control Hub Modal
    if (showAdminControlHub) {
        AdminControlHubDialog(
            userState = userState,
            currentGridPrice = gridPriceUsd,
            onUpdateGridPrice = { viewModel.updateGridPrice(it) },
            onApproveWithdrawal = { viewModel.adminApproveWithdrawal(it) },
            onRejectWithdrawal = { viewModel.adminRejectWithdrawal(it) },
            onAdjustBalance = { grid, usdt -> viewModel.adminAdjustUserBalance(grid, usdt) },
            onCreateTestWithdrawal = { viewModel.adminCreateTestPendingWithdrawal() },
            onDismiss = { viewModel.showAdminControlHubDialog.value = false }
        )
    }

    // NOWPayments In-App Gateway Modal
    if (showGatewayModal && activePayment != null) {
        NowPaymentsGatewayDialog(
            payment = activePayment!!,
            isCheckingStatus = isCheckingStatus,
            onCheckStatus = { paymentId -> viewModel.checkPaymentStatus(paymentId) },
            onSimulateConfirm = { paymentId -> viewModel.simulateInstantPaymentConfirm(paymentId) },
            onDismiss = { viewModel.showPaymentGatewayModal.value = false }
        )
    }

    // Payment Success Confirmation Modal
    if (showSuccessDialog && lastConfirmedPayment != null) {
        PaymentSuccessDialog(
            payment = lastConfirmedPayment,
            onDismiss = { viewModel.showPaymentSuccessDialog.value = false }
        )
    }

    // Web3 Secret Key Backup Modal
    if (showSecretKeyBackup) {
        SecretKeyBackupModal(
            secretKey = userState.secretKey,
            onDismiss = { viewModel.showSecretKeyBackupModal.value = false },
            onConfirmBackedUp = { viewModel.markSecretKeyBackedUp() }
        )
    }

    // Web3 Secret Key Restore Modal
    if (showSecretKeyRestore) {
        SecretKeyRestoreModal(
            isLoading = isRestoringAccount,
            onDismiss = { viewModel.showSecretKeyRestoreModal.value = false },
            onRestore = { key -> viewModel.restoreAccountWithSecretKey(key) }
        )
    }

    // 4-Digit PIN & Biometric Setup Modal
    if (showPinSetup) {
        PinSetupModal(
            onDismiss = { viewModel.showPinSetupModal.value = false },
            onPinSet = { pin, bio ->
                viewModel.setPin(pin)
                viewModel.toggleBiometric(bio)
            }
        )
    }

    // Smart Device Lock Screen Overlay
    if (userState.isAppLocked) {
        LockScreenOverlay(
            isBiometricEnabled = userState.isBiometricEnabled,
            onUnlockWithPin = { pin -> viewModel.unlockWithPin(pin) },
            onUnlockWithBiometric = { viewModel.unlockWithBiometric() },
            onForgotPinClick = { viewModel.showSecretKeyRestoreModal.value = true }
        )
    }
}
