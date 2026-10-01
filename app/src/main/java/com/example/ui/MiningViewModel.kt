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

    val minerBalance = MutableStateFlow(0.0)
    val gridBalance = MutableStateFlow(0.0)
    val deployedNodesCount = MutableStateFlow(0)
    val _minerBalance = minerBalance
    val _gridBalance = gridBalance
    val _deployedNodesCount = deployedNodesCount
    val isMiningActive = MutableStateFlow(false)
    val sessionEndTime = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            userState.collect { state ->
                minerBalance.value = state.minerBalanceUsdt
                gridBalance.value = maxOf(state.gridBalance, gridBalance.value)
                deployedNodesCount.value = state.userRigs.size
                isMiningActive.value = state.isFreeMiningActive
                sessionEndTime.value = state.freeMiningSessionEnd
            }
        }
    }

    fun updateGridPrice(newPrice: Double) {
        viewModelScope.launch {
            val success = repository.updateGridPrice(newPrice)
            if (success) {
                _uiEvents.emit(UiEvent.ShowToast("GRID Token Price updated to $${String.format("%.2f", newPrice)} USD across network!"))
            } else {
                _uiEvents.emit(UiEvent.ShowToast("Updated locally to $${String.format("%.2f", newPrice)} USD (Firestore sync failed)"))
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

    fun startFreeMining() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            isMiningActive.value = true
            sessionEndTime.value = System.currentTimeMillis() + 86400000L
            try {
                repository.startFreeMiningSession()
                emitToast("24h Mining Core Activated!")
            } catch (e: Exception) {
                val fullError = "${e.javaClass.simpleName}: ${e.localizedMessage ?: e.message}"
                emitToast(fullError)
                Log.e("FIRESTORE_WRITE_ERR", "Write failed", e)
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
    }

    fun lockApp() {
        if (repository.isPinSet()) {
            repository.setAppLocked(true)
        }
    }

    fun setAppLocked(locked: Boolean) {
        if (locked && !repository.isPinSet()) {
            // Can't lock if no PIN configured
            return
        }
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
            isRestoringAccount.value = true
            val result = repository.createNewAccount()
            isRestoringAccount.value = false
            if (result.isSuccess) {
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
        minerBalance.value = minerUsdt
        gridBalance.value = grid
        repository.setLocalBalance(minerUsdt, grid)
    }

    fun syncUserDataSilently(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docRef = FirebaseFirestore.getInstance().collection("users").document(key)
                docRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val usdt = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble() ?: 3000.0
                        val cloudGrid = (snapshot.get("gridBalance") as? Number)?.toDouble() ?: 0.0
                        val cloudBaseline = (snapshot.get("baselineGridBalance") as? Number)?.toDouble() ?: cloudGrid
                        val cloudStartTime = (snapshot.get("miningStartTimeMillis") as? Number)?.toLong()
                            ?: (snapshot.get("freeMiningSessionStart") as? Number)?.toLong() ?: 0L
                        val cloudEndTime = (snapshot.get("freeMiningSessionEnd") as? Number)?.toLong()
                            ?: (snapshot.get("sessionEndTime") as? Number)?.toLong() ?: 0L
                        val isMining = (snapshot.getBoolean("isFreeMiningActive") ?: snapshot.getBoolean("isMiningActive") ?: false)
                        val nodes = (snapshot.get("deployedNodesCount") as? Number)?.toInt() ?: 0

                        val now = System.currentTimeMillis()
                        val accruedGrid = if (isMining && now < cloudEndTime && cloudStartTime > 0) {
                            val elapsedSeconds = ((now - cloudStartTime) / 1000.0).coerceAtLeast(0.0)
                            val hashrate = userState.value.aggregateFreeHashrateGh.coerceAtLeast(2.0)
                            val tokensPerSec = (hashrate / 10.0) * (MiningRepository.TARGET_DAILY_GRID / 86400.0)
                            cloudBaseline + (elapsedSeconds * tokensPerSec)
                        } else {
                            cloudGrid
                        }
                        val finalGrid = maxOf(accruedGrid, _gridBalance.value, cloudGrid)

                        viewModelScope.launch(Dispatchers.Main) {
                            _minerBalance.value = usdt
                            _gridBalance.value = finalGrid
                            _deployedNodesCount.value = nodes
                            repository.setLocalBalance(usdt, finalGrid)
                        }
                    }
                    repository.attachUserDocumentRealTimeListener(key)
                }.addOnFailureListener { e ->
                    // Log error silently. DO NOT set isAuthenticated = false!
                    android.util.Log.e("SYNC_SILENT", "Firestore offline sync fallback", e)
                    repository.attachUserDocumentRealTimeListener(key)
                }
            } catch (e: Exception) {
                android.util.Log.e("SYNC_SILENT", "Exception during silent sync", e)
            }
        }
    }

    fun onAppResumed() {
        val now = System.currentTimeMillis()
        val recomputedGrid = repository.computeAccruedGridBalance(now)
        val finalGrid = maxOf(recomputedGrid, _gridBalance.value)
        _gridBalance.value = finalGrid
        repository.setLocalBalance(_minerBalance.value, finalGrid)
    }

    fun onAppPaused() {
        val now = System.currentTimeMillis()
        val recomputedGrid = repository.computeAccruedGridBalance(now)
        val finalGrid = maxOf(recomputedGrid, _gridBalance.value)
        _gridBalance.value = finalGrid
        repository.saveGridBalanceOnPause(finalGrid)
    }

    fun handleLoginOrRestore(inputKey: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(inputKey)
        if (cleanKey.isEmpty()) return

        // 1. Immediately persist key to SharedPreferences
        repository.securityPreferences.saveActiveKey(cleanKey)
        repository.securityPreferences.setLoggedIn(true)
        repository.securityPreferences.setSecretKeyBackedUp(true)

        val isAdminKey = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

        // 2. Pre-set Admin privileges & fallback balance if Admin key
        val defaultUsdt = if (isAdminKey) 3000.0 else repository.prefs.getFloat("miner_balance", 0f).toDouble()
        val defaultGrid = if (isAdminKey) 5000.0 else repository.prefs.getFloat("grid_balance", 0f).toDouble()

        if (isAdminKey) {
            // Ensure balance never resets to zero on login
            if (minerBalance.value <= 0.0) {
                setLocalBalance(defaultUsdt, defaultGrid)
            }
        } else {
            minerBalance.value = defaultUsdt
            gridBalance.value = defaultGrid
        }

        val currentMinerBal = if (isAdminKey && minerBalance.value <= 0.0) defaultUsdt else minerBalance.value
        val currentGridBal = if (isAdminKey && gridBalance.value <= 0.0) defaultGrid else gridBalance.value

        // 3. FORCE IMMEDIATE NAVIGATION TO DASHBOARD (0.01 sec)
        val instantState = UserMiningState(
            uid = cleanKey,
            secretKey = cleanKey,
            email = if (isAdminKey) "admin@hashgrid.pro" else "miner_${cleanKey.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = if (isAdminKey) "NODE-SUPERADMIN-#0001" else "NODE-WEB3-#${cleanKey.takeLast(4)}",
            minerBalanceUsdt = currentMinerBal,
            gridBalance = currentGridBal,
            isAdmin = isAdminKey,
            role = if (isAdminKey) "superadmin" else "user",
            isAuthenticated = true,
            isKeyBackedUp = true
        )

        repository.setInstantUserState(instantState)
        repository.setAppLocked(false)
        isRestoringAccount.value = false
        showSecretKeyRestoreModal.value = false
        _currentTab.value = AppNavTab.HOME // Instantly switch to main dashboard

        if (isAdminKey) {
            emitToast("Master SuperAdmin Access Granted (Offline-First Ready)")
        } else {
            emitToast("Logged in successfully! Connecting in background...")
        }

        // 4. TRIGGER SILENT BACKGROUND FIRESTORE SYNC (NEVER BLOCK UI)
        syncUserDataSilently(cleanKey)
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
        val docRef = FirebaseFirestore.getInstance().collection("users").document(targetKey)
        docRef.set(mapOf(
            "minerBalanceUsdt" to usdt,
            "gridBalance" to grid
        ), SetOptions.merge()).addOnSuccessListener {
            minerBalance.value = usdt
            gridBalance.value = grid
            emitToast("Admin: Updated balance for $targetKey")
        }.addOnFailureListener {
            emitToast("Admin: Failed to update balance")
        }
    }

    fun adminAdjustUserBalance(newGrid: Double, newUsdt: Double) {
        applyBalanceOverride(userState.value.secretKey, newUsdt, newGrid)
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
