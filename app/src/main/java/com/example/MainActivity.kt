package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HashGridTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(
    viewModel: MiningViewModel = viewModel()
) {
    val userState by viewModel.userState.collectAsStateWithLifecycle()
    val cryptoPrices by viewModel.cryptoPrices.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()

    val showDeposit by viewModel.showDepositDialog.collectAsStateWithLifecycle()
    val showWithdraw by viewModel.showWithdrawDialog.collectAsStateWithLifecycle()
    val showLuckyWheel by viewModel.showLuckyWheelDialog.collectAsStateWithLifecycle()
    val showCalculator by viewModel.showCalculatorDialog.collectAsStateWithLifecycle()
    val showMicroTask by viewModel.showMicroTaskDialog.collectAsStateWithLifecycle()
    val showVideoPromo by viewModel.showVideoPromoDialog.collectAsStateWithLifecycle()
    val showHowItWorks by viewModel.showHowItWorksDialog.collectAsStateWithLifecycle()
    val showTaskPolicy by viewModel.showTaskPolicyDialog.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is UiEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                is UiEvent.RigPurchased -> snackbarHostState.showSnackbar("Deployed ${event.rigName}! Monthly yield activated.")
                is UiEvent.SpinCompleted -> snackbarHostState.showSnackbar("Won ${event.record.rewardTitle}! Credited to balance.")
            }
        }
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
                tickers = cryptoPrices,
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
                        onOpenDeposit = { viewModel.showDepositDialog.value = true }
                    )
                    AppNavTab.CLOUD_MINER -> CloudMinerScreen(
                        userState = userState,
                        onStartMining = { viewModel.startFreeMining() },
                        onOpenLuckyWheel = { viewModel.showLuckyWheelDialog.value = true },
                        onNavigateToNetwork = { viewModel.selectTab(AppNavTab.NETWORK) }
                    )
                    AppNavTab.NETWORK -> NetworkScreen(
                        userState = userState,
                        onSimulateDownlinePurchase = { viewModel.simulateDownlinePurchase() },
                        onSimulateNewReferral = { viewModel.simulateNewReferral() }
                    )
                    AppNavTab.PROFILE -> ProfileScreen(
                        userState = userState,
                        onOpenDeposit = { viewModel.showDepositDialog.value = true },
                        onOpenWithdraw = { viewModel.showWithdrawDialog.value = true },
                        onOpenLuckyWheel = { viewModel.showLuckyWheelDialog.value = true },
                        onOpenCalculator = { viewModel.showCalculatorDialog.value = true },
                        onOpenMicroTask = { viewModel.showMicroTaskDialog.value = true },
                        onOpenVideoPromo = { viewModel.showVideoPromoDialog.value = true },
                        onOpenHowItWorks = { viewModel.showHowItWorksDialog.value = true },
                        onOpenTaskPolicy = { viewModel.showTaskPolicyDialog.value = true },
                        onApprovePendingTasks = { viewModel.approvePendingTasks() }
                    )
                }
            }
        }
    }

    // Common Dialogs & Modals
    if (showDeposit) {
        DepositDialog(
            onDismiss = { viewModel.showDepositDialog.value = false },
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
            onDismiss = { viewModel.showCalculatorDialog.value = false }
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
}
