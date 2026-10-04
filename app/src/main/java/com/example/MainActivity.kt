package com.example

import android.Manifest
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.notification.NotificationHelper
import com.example.ui.AppNavTab
import com.example.ui.MiningViewModel
import com.example.ui.UiEvent
import com.example.ui.components.*
import com.example.ui.components.security.*
import com.example.ui.screens.*
import com.example.ui.theme.HashGridTheme
import com.example.ui.theme.ObsidianBg
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.flow.collectLatest

class MainActivity : AppCompatActivity() {

    private val viewModel: MiningViewModel by viewModels()

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    viewModel.lockApp()
                }
            } catch (e: Throwable) {
                Log.w("MainActivity", "Error in screenOffReceiver: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Google Live Firebase Initialization (hashgrid-b850b)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey("AIzaSyBCR8ab9HAOEYRtQ1HY94fxm7FFweqsx3M")
                    .setApplicationId("1:621346408367:android:c0c569afddabd2695a915a")
                    .setProjectId("hashgrid-b850b")
                    .setDatabaseUrl("https://hashgrid-b850b-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .setStorageBucket("hashgrid-b850b.firebasestorage.app")
                    .setGcmSenderId("621346408367")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.i("FIREBASE_INIT", "Connected directly to Google Live Project: hashgrid-b850b")
            }
        } catch (e: Exception) {
            Log.e("FIREBASE_INIT", "Firebase explicit initialization note: ${e.message}", e)
        }

        // 2. Configure Firestore Offline Persistence & Server Cache
        try {
            val db = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
            db.firestoreSettings = settings
            Log.i("FIREBASE_INIT", "Firestore Persistent Cache configured.")
        } catch (e: Exception) {
            Log.w("FIREBASE_INIT", "Firestore settings configuration note: ${e.message}")
        }

        super.onCreate(savedInstanceState)

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
        viewModel.setUserOnline(true)
        viewModel.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        viewModel.setUserOnline(false)
        viewModel.onAppPaused()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppPaused()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.setUserOnline(false)
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

    // Real-Time Network Connectivity Listener
    var isOnline by remember {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        mutableStateOf(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
                viewModel.onAppResumed()
            }

            override fun onLost(network: Network) {
                isOnline = false
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                isOnline = hasInternet
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm?.registerNetworkCallback(request, callback)
        } catch (e: Throwable) {
            Log.w("MainActivity", "Network callback registration note: ${e.message}")
        }

        onDispose {
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (_: Throwable) {}
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        NotificationHelper.createNotificationChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.onAppResumed()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE, androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.onAppPaused()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
    val showGridLocked by viewModel.showGridLockedDialog.collectAsStateWithLifecycle()

    val showSecretKeyBackup by viewModel.showSecretKeyBackupModal.collectAsStateWithLifecycle()
    val showSecretKeyRestore by viewModel.showSecretKeyRestoreModal.collectAsStateWithLifecycle()
    val showPinSetup by viewModel.showPinSetupModal.collectAsStateWithLifecycle()
    val showAdminControlHub by viewModel.showAdminControlHubDialog.collectAsStateWithLifecycle()
    val isRestoringAccount by viewModel.isRestoringAccount.collectAsStateWithLifecycle()

    val activePayment by viewModel.activePaymentSession.collectAsStateWithLifecycle()
    val showGatewayModal by viewModel.showPaymentGatewayModal.collectAsStateWithLifecycle()
    val showSuccessDialog by viewModel.showPaymentSuccessDialog.collectAsStateWithLifecycle()
    val lastConfirmedPayment by viewModel.lastConfirmedPayment.collectAsStateWithLifecycle()
    val isCheckingStatus by viewModel.isCheckingPaymentStatus.collectAsStateWithLifecycle()
    val isCloudSynced by viewModel.isCloudSynced.collectAsStateWithLifecycle()
    val connectionErrorMsg by viewModel.connectionErrorMsg.collectAsStateWithLifecycle()

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

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            snackbarHostState.showSnackbar("⚠️ No Internet Connection. Cloud sync is paused.")
        } else {
            viewModel.onAppResumed()
        }
    }

    // AUTH GATEWAY
    if (!userState.isAuthenticated || userState.secretKey.isBlank()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = !isOnline) {
                Surface(
                    color = Color(0xFFDC2626),
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "No Internet",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NO INTERNET CONNECTION (AIRPLANE MODE)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            WelcomeAuthScreen(
                isLoading = false,
                onCreateAccount = { refCode -> viewModel.createNewAccount(refCode) },
                onRestoreAccount = { key -> viewModel.loginWithKey(key) }
            )
        }
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
            Column(modifier = Modifier.statusBarsPadding()) {
                TopHeaderBar(
                    nodeId = userState.nodeId,
                    isColdStorageSynced = userState.isColdStorageSynced,
                    preLaunchPriceUsd = gridPriceUsd,
                    isCloudSynced = isOnline && isCloudSynced,
                    connectionErrorMsg = if (!isOnline) "No Internet (Airplane Mode)" else connectionErrorMsg,
                    modifier = Modifier.fillMaxWidth()
                )

                AnimatedVisibility(
                    visible = !isOnline,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Surface(
                        color = Color(0xFFDC2626),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = "No Internet",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NO INTERNET CONNECTION • Cloud Sync Paused",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab,
                onTabSelected = { tab -> viewModel.selectTab(tab) },
                isMiningActive = userState.isFreeMiningActive
            )
        },
        containerColor = ObsidianBg,
        contentColor = Color.White
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
                        viewModel = viewModel,
                        gridPriceUsd = gridPriceUsd,
                        onNavigateToTab = { viewModel.selectTab(it) },
                        onOpenDeposit = { viewModel.showDepositDialog.value = true },
                        onOpenWithdraw = { viewModel.showWithdrawDialog.value = true },
                        onOpenLuckyWheel = { viewModel.showLuckyWheelDialog.value = true },
                        onOpenCalculator = { viewModel.showCalculatorDialog.value = true },
                        onOpenHowItWorks = { viewModel.showHowItWorksDialog.value = true },
                        onGridBalanceClick = { viewModel.showGridLockedDialog.value = true }
                    )
                    AppNavTab.RIGS_STORE -> RigsStoreScreen(
                        userState = userState,
                        viewModel = viewModel,
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
                        onLogout = { viewModel.logout() },
                        onGridBalanceClick = { viewModel.showGridLockedDialog.value = true },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

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

    if (showGridLocked) {
        GridPreLaunchLockedDialog(
            onDismiss = { viewModel.showGridLockedDialog.value = false }
        )
    }

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

    if (showGatewayModal && activePayment != null) {
        NowPaymentsGatewayDialog(
            payment = activePayment!!,
            isCheckingStatus = isCheckingStatus,
            onCheckStatus = { paymentId -> viewModel.checkPaymentStatus(paymentId) },
            onSimulateConfirm = { paymentId -> viewModel.simulateInstantPaymentConfirm(paymentId) },
            onDismiss = { viewModel.showPaymentGatewayModal.value = false }
        )
    }

    if (showSuccessDialog && lastConfirmedPayment != null) {
        PaymentSuccessDialog(
            payment = lastConfirmedPayment,
            onDismiss = { viewModel.showPaymentSuccessDialog.value = false }
        )
    }

    if (showSecretKeyBackup) {
        SecretKeyBackupModal(
            secretKey = userState.secretKey,
            onDismiss = { viewModel.showSecretKeyBackupModal.value = false },
            onConfirmBackedUp = { viewModel.markSecretKeyBackedUp() }
        )
    }

    if (showSecretKeyRestore) {
        SecretKeyRestoreModal(
            isLoading = isRestoringAccount,
            onDismiss = { viewModel.showSecretKeyRestoreModal.value = false },
            onRestore = { key -> viewModel.restoreAccountWithSecretKey(key) }
        )
    }

    if (showPinSetup) {
        PinSetupModal(
            onDismiss = { viewModel.showPinSetupModal.value = false },
            onPinSet = { pin, bio ->
                viewModel.setPin(pin)
                viewModel.toggleBiometric(bio)
            }
        )
    }

    if (userState.isAppLocked) {
        LockScreenOverlay(
            isBiometricEnabled = userState.isBiometricEnabled,
            onUnlockWithPin = { pin -> viewModel.unlockWithPin(pin) },
            onUnlockWithBiometric = { viewModel.unlockWithBiometric() },
            onForgotPinClick = { viewModel.showSecretKeyRestoreModal.value = true }
        )
    }
}
