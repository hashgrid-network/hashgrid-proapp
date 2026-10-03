package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.payment.NowPaymentResponse
import com.example.data.repository.MiningRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.example.data.security.SecretKeyUtils

enum class AppNavTab {
    HOME,
    RIGS_STORE,
    CLOUD_MINER,
    NETWORK,
    PROFILE
}

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class SpinCompleted(val record: SpinHistoryRecord) : UiEvent()
    data class RigPurchased(val rigName: String) : UiEvent()
    data class PaymentConfirmed(val payment: NowPaymentResponse, val message: String) : UiEvent()
}

class MiningViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MiningRepository(application)

    val userState: StateFlow<UserMiningState> = repository.userState
    val cryptoPrices: StateFlow<List<CryptoTickerPrice>> = repository.cryptoPrices
    val gridPriceUsd: StateFlow<Double> = repository.gridPriceUsd
    val isCloudSynced: StateFlow<Boolean> = repository.isCloudSynced.asStateFlow()
    val connectionErrorMsg: StateFlow<String?> = repository.connectionErrorMsg.asStateFlow()
    val activeAccount: StateFlow<UserCloudAccount?> = repository.activeAccount.asStateFlow()
    val accountState = repository.activeAccount

    // Direct Reactive State Flows directly from Repository Vault
    val minerBalance = repository.minerBalance
    val minerBalanceUsdt: StateFlow<Double> = repository.minerBalance.asStateFlow()
    val gridBalance = repository.gridBalance
    val deployedNodesCount = repository.deployedNodesCount
    val deployedRigs = repository.deployedRigs
    val _deployedRigs = repository.deployedRigs
    val hardwareNodes = repository.deployedRigs
    val _hardwareNodes = repository.deployedRigs
    val _minerBalance = repository.minerBalance
    val _gridBalance = repository.gridBalance
    val _deployedNodesCount = repository.deployedNodesCount
    val isMiningActive = repository.isMiningActive
    val _isMiningActive = repository.isMiningActive
    val sessionEndTime = repository.freeMiningEndTime
    val _freeMiningEndTime = repository.freeMiningEndTime
    val freeMiningEndTime = repository.freeMiningEndTime

    init {
        val savedKey = repository.getActiveKey()
        if (!savedKey.isNullOrEmpty()) {
            repository.bindUserSession(savedKey)
        }
    }

    fun loginWithKey(key: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(key)
        if (cleanKey.isBlank()) return
        handleLoginOrRestore(cleanKey)
    }

    fun startMining() {
        startFreeMining()
    }

    fun startFreeMining() {
        repository.startFreeMiningSession()
        emitToast("24h Mining Core Activated!")
    }

    fun deployNode(rig: HardwareNode) {
        val key = repository.getActiveKey() ?: userState.value.secretKey
        if (key.isNotBlank()) {
            repository.deployHardwareRig(key, rig) {
                emitToast("Successfully deployed ${rig.name}! Monthly net yield ~15% active.")
            }
        }
    }

    fun buyRig(item: RigCatalogItem) {
        val result = repository.buyRig(item)
        if (result.isSuccess) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.RigPurchased(item.name))
                _uiEvents.emit(UiEvent.ShowToast("Successfully deployed ${item.name}! Monthly net yield ~15% active."))
            }
        } else {
            emitToast(result.exceptionOrNull()?.message ?: "Purchase failed.")
        }
    }

    fun adminOverride(targetKey: String, usdt: Double, grid: Double) {
        repository.adminSetBalance(targetKey, usdt, grid)
        emitToast("Admin: Updated balance for $targetKey")
    }

    fun updateGridPrice(newPrice: Double) {
        viewModelScope.launch {
            val success = repository.updateGridPrice(newPrice)
            if (success) {
                _uiEvents.emit(UiEvent.ShowToast("GRID Token Price updated to $${String.format("%.2f", newPrice)} USD across network!"))
            } else {
                _uiEvents.emit(UiEvent.ShowToast("Updated locally to $${String.format("%.2f", newPrice)} USD"))
            }
        }
    }

    private val _currentTab = MutableStateFlow(AppNavTab.CLOUD_MINER)
    val currentTab: StateFlow<AppNavTab> = _currentTab.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // Dialog & Sheet states
    val showDepositDialog = MutableStateFlow(false)
    val showWithdrawDialog = MutableStateFlow(false)
    val showLuckyWheelDialog = MutableStateFlow(false)
    val showCalculatorDialog = MutableStateFlow(false)
    val showMicroTaskDialog = MutableStateFlow(false)
    val showVideoPromoDialog = MutableStateFlow(false)
    val showHowItWorksDialog = MutableStateFlow(false)
    val showTaskPolicyDialog = MutableStateFlow(false)
    val showGridLockedDialog = MutableStateFlow(false)

    // Security, Secret Key & Smart Lock States
    val showSecretKeyBackupModal = MutableStateFlow(false)
    val showSecretKeyRestoreModal = MutableStateFlow(false)
    val showPinSetupModal = MutableStateFlow(false)
    val showAdminControlHubDialog = MutableStateFlow(false)
    val isRestoringAccount = MutableStateFlow(false)
    val accountLimitAlertMessage = MutableStateFlow<String?>(null)

    // NOWPayments Gateway State
    val activePaymentSession = MutableStateFlow<NowPaymentResponse?>(null)
    val isCreatingPayment = MutableStateFlow(false)
    val isCheckingPaymentStatus = MutableStateFlow(false)
    val showPaymentGatewayModal = MutableStateFlow(false)
    val showPaymentSuccessDialog = MutableStateFlow(false)
    val lastConfirmedPayment = MutableStateFlow<NowPaymentResponse?>(null)

    fun selectTab(tab: AppNavTab) {
        _currentTab.value = tab
    }

    // ==========================================
    // NOWPAYMENTS GATEWAY CALLS
    // ==========================================

    fun initiateNowPaymentsDeposit(priceAmountUsd: Double, payCurrency: String) {
        viewModelScope.launch {
            isCreatingPayment.value = true
            val result = repository.createNowPaymentsDeposit(priceAmountUsd, payCurrency)
            isCreatingPayment.value = false

            if (result.isSuccess) {
                val payment = result.getOrThrow()
                activePaymentSession.value = payment
                showDepositDialog.value = false
                showPaymentGatewayModal.value = true
                emitToast("NOWPayments Invoice Generated! Send $payCurrency to deposit address.")
                startAutoPolling(payment.paymentId)
            } else {
                emitToast(result.exceptionOrNull()?.message ?: "Failed to generate NOWPayments invoice.")
            }
        }
    }

    fun initiateNowPaymentsRigPurchase(rigItem: RigCatalogItem, payCurrency: String) {
        viewModelScope.launch {
            isCreatingPayment.value = true
            val result = repository.createNowPaymentsRigPurchase(rigItem, payCurrency)
            isCreatingPayment.value = false

            if (result.isSuccess) {
                val payment = result.getOrThrow()
                activePaymentSession.value = payment
                showPaymentGatewayModal.value = true
                emitToast("Node Payment Invoice Created! Deploying ${rigItem.name} on confirmation.")
                startAutoPolling(payment.paymentId)
            } else {
                emitToast(result.exceptionOrNull()?.message ?: "Failed to create node payment.")
            }
        }
    }

    fun checkPaymentStatus(paymentId: String) {
        viewModelScope.launch {
            isCheckingPaymentStatus.value = true
            val result = repository.verifyAndSyncPaymentStatus(paymentId)
            isCheckingPaymentStatus.value = false

            if (result.isSuccess) {
                val (payment, wasJustCompleted) = result.getOrThrow()
                activePaymentSession.value = payment

                if (wasJustCompleted || payment.isSuccessOrConfirmed) {
                    lastConfirmedPayment.value = payment
                    showPaymentGatewayModal.value = false
                    showPaymentSuccessDialog.value = true
                    _uiEvents.emit(UiEvent.PaymentConfirmed(payment, "Payment confirmed! Balance & Hashrate updated in Firestore."))
                } else {
                    emitToast("Status: ${payment.paymentStatus.uppercase()}. Awaiting blockchain confirmations...")
                }
            } else {
                emitToast(result.exceptionOrNull()?.message ?: "Status check failed.")
            }
        }
    }

    fun simulateInstantPaymentConfirm(paymentId: String) {
        val success = repository.simulateInstantPaymentConfirmation(paymentId)
        if (success) {
            val session = activePaymentSession.value
            if (session != null) {
                val confirmed = session.copy(paymentStatus = "finished")
                lastConfirmedPayment.value = confirmed
            }
            showPaymentGatewayModal.value = false
            showPaymentSuccessDialog.value = true
            emitToast("Payment confirmed! Activated mining plan and updated wallet.")
        }
    }

    private fun startAutoPolling(paymentId: String) {
        viewModelScope.launch {
            var attempts = 0
            while (showPaymentGatewayModal.value && attempts < 10) {
                delay(8000)
                attempts++
                if (!showPaymentGatewayModal.value) break
                val res = repository.verifyAndSyncPaymentStatus(paymentId)
                if (res.isSuccess) {
                    val (payment, wasJustCompleted) = res.getOrThrow()
                    activePaymentSession.value = payment
                    if (wasJustCompleted || payment.isSuccessOrConfirmed) {
                        lastConfirmedPayment.value = payment
                        showPaymentGatewayModal.value = false
                        showPaymentSuccessDialog.value = true
                        _uiEvents.emit(UiEvent.PaymentConfirmed(payment, "Payment confirmed on blockchain!"))
                        break
                    }
                }
            }
        }
    }

    // ==========================================
    // OTHER PROTOCOL ACTIONS
    // ==========================================

    fun depositFunds(amount: Double, network: String) {
        repository.depositFunds(amount, network)
        showDepositDialog.value = false
        emitToast("Deposit of $${String.format("%.2f", amount)} USDT ($network) confirmed into Miner Balance!")
    }

    fun requestWithdrawal(amount: Double, address: String, network: String) {
        val res = repository.requestWithdrawal(amount, address, network)
        if (res.isSuccess) {
            showWithdrawDialog.value = false
            emitToast("Withdrawal requested! Audited within the 24-hour review window.")
        } else {
            emitToast(res.exceptionOrNull()?.message ?: "Withdrawal failed.")
        }
    }

    fun claimWheelReward(key: String, rewardGrid: Double) {
        repository.claimWheelReward(key, rewardGrid)
    }

    fun onWheelSpinResult(sector: SpinSector) {
        val record = repository.executeLuckySpin(sector)
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.SpinCompleted(record))
        }
    }

    fun submitMicroTask(platform: TaskPlatform, initialViews: Int, finalViews: Int, notes: String) {
        repository.submitMicroTask(platform, initialViews, finalViews, notes)
        showMicroTaskDialog.value = false
        emitToast("Micro-task submitted for review! Rewards up to $50 USDT upon verification.")
    }

    fun submitVideoPromo(platform: TaskPlatform, url: String, channel: String) {
        repository.submitVideoPromo(platform, url, channel)
        showVideoPromoDialog.value = false
        emitToast("Creator video submitted! Admin review every 15 days ($100-$2000 USDT).")
    }

    fun simulateDownlinePurchase() {
        repository.simulateDownlinePurchase()
        emitToast("Simulation: Downline user deployed a $100 Quantum Rig! 7% instant commission ($7.00 USDT) credited.")
    }

    fun simulateNewReferral() {
        repository.simulateNewReferral()
        emitToast("Simulation: New affiliate joined! +0.25 GH/s permanent boost added.")
    }

    fun approvePendingTasks() {
        repository.approvePendingTasksSimulation()
        emitToast("Simulation: Pending promotional bounties approved and credited!")
    }

    // ==========================================
    // SECURITY & RESTORE METHODS
    // ==========================================

    fun unlockWithPin(pin: String): Boolean {
        val valid = repository.verifyPin(pin)
        if (valid) {
            repository.setAppLocked(false)
        }
        return valid
    }

    fun unlockWithBiometric() {
        repository.setAppLocked(false)
        emitToast("Biometric authentication verified. Mining terminal unlocked.")
    }

    fun lockApp() {
        repository.setAppLocked(true)
    }

    fun setAppLocked(locked: Boolean) {
        repository.setAppLocked(locked)
    }

    fun setPin(pin: String): Boolean {
        val success = repository.setPin(pin)
        if (success) {
            showPinSetupModal.value = false
            emitToast("4-Digit Security PIN configured successfully!")
        }
        return success
    }

    fun toggleBiometric(enabled: Boolean) {
        repository.setBiometricEnabled(enabled)
        emitToast(if (enabled) "Biometric Fingerprint Unlock Enabled" else "Biometric Fingerprint Disabled")
    }

    fun setUserOnline(isOnline: Boolean) {
        repository.setUserOnline(isOnline)
    }

    fun markSecretKeyBackedUp() {
        repository.markSecretKeyBackedUp()
        showSecretKeyBackupModal.value = false
        if (!repository.isPinSet()) {
            showPinSetupModal.value = true
        }
        emitToast("Secret Key backup confirmed! Keep your key safe.")
    }

    fun createNewAccount() {
        viewModelScope.launch {
            val result = repository.createNewAccount()
            if (result.isSuccess) {
                val state = result.getOrNull()
                val key = state?.secretKey ?: repository.getActiveKey()
                if (!key.isNullOrBlank()) {
                    repository.bindUserSession(key)
                }
                _currentTab.value = AppNavTab.HOME
                showSecretKeyBackupModal.value = true
                emitToast("✨ New Account Created! Please securely backup your Secret Key.")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to create account."
                if (errorMsg.contains("Account Limit Reached", ignoreCase = true)) {
                    accountLimitAlertMessage.value = errorMsg
                }
                emitToast(errorMsg)
            }
        }
    }

    fun logout() {
        repository.clearActiveKey()
        repository.immediateLogout()
        _currentTab.value = AppNavTab.HOME
        emitToast("Logged out of HashGrid Pro.")
    }

    fun syncMinedTokens() {
        viewModelScope.launch {
            repository.syncMinedTokensToFirestore()
        }
    }

    fun setLocalBalance(minerUsdt: Double, grid: Double) {
        repository.setLocalBalance(minerUsdt, grid)
    }

    fun loadStateIntoApp(
        usdtBalance: Double,
        gridBalance: Double,
        isStillMining: Boolean,
        miningEndTime: Long,
        deployedRigs: List<Map<String, Any>>
    ) {
        repository.loadStateIntoApp(usdtBalance, gridBalance, isStillMining, miningEndTime, deployedRigs)
    }

    fun syncAndCatchUpOfflineGrowth(secretKey: String) {
        repository.bindUserSession(secretKey)
    }

    fun syncUserDataSilently(key: String) {
        repository.bindUserSession(key)
    }

    // SAFE RESUME: Re-binds to Firestore without blindly overwriting with 0.0
    fun onAppResumed() {
        val key = userState.value.secretKey.ifBlank { repository.getActiveKey() ?: "" }
        if (key.isNotBlank()) {
            repository.bindUserSession(key)
        }
    }

    // SAFE PAUSE: Safely persists current active state to cloud
    fun onAppPaused() {
        val key = userState.value.secretKey.ifBlank { repository.getActiveKey() ?: "" }
        if (key.isNotBlank()) {
            repository.onAppPaused()
        }
    }

    fun handleLoginOrRestore(inputKey: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(inputKey)
        if (cleanKey.isEmpty()) return

        repository.setActiveKey(cleanKey)
        val instantState = repository.loginWithKeyInstant(cleanKey)

        repository.setAppLocked(false)
        isRestoringAccount.value = false
        showSecretKeyRestoreModal.value = false
        _currentTab.value = AppNavTab.HOME

        if (instantState.isAdmin) {
            emitToast("Master SuperAdmin Access Granted (Cloud Synced)")
        } else {
            emitToast("Logged in successfully! Syncing cloud data...")
        }

        repository.bindUserSession(cleanKey)
    }

    fun restoreAccount(key: String, onComplete: (Boolean) -> Unit = {}) {
        handleLoginOrRestore(key)
        onComplete(true)
    }

    fun restoreAccountWithSecretKey(secretKey: String) {
        handleLoginOrRestore(secretKey)
    }

    fun adminApproveWithdrawal(txId: String) {
        repository.adminApproveWithdrawal(txId)
        emitToast("Super Admin: Withdrawal approved! Marked COMPLETED.")
    }

    fun adminRejectWithdrawal(txId: String) {
        repository.adminRejectWithdrawal(txId)
        emitToast("Super Admin: Withdrawal rejected and funds refunded.")
    }

    fun applyBalanceOverride(targetKey: String, usdt: Double, grid: Double) {
        repository.adminSetBalance(targetKey, usdt, grid)
        emitToast("Admin: Updated balance for $targetKey")
    }

    fun adminAdjustUserBalance(newGrid: Double, newUsdt: Double) {
        val currentKey = userState.value.secretKey.ifBlank { repository.getActiveKey() ?: "HG-ADM9-7788-5544-0001" }
        repository.adminAdjustUserBalance(newGrid, newUsdt)
        emitToast("Super Admin: Balances updated to $newGrid GRID / $$newUsdt USDT.")
    }

    fun adminCreateTestPendingWithdrawal() {
        repository.adminCreateTestPendingWithdrawal()
        emitToast("Super Admin: Created test pending withdrawal of 25.00 USDT.")
    }

    private fun emitToast(msg: String) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast(msg))
        }
    }
}
