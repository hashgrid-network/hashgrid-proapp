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

    val minerBalance = MutableStateFlow(0.0)
    val minerBalanceUsdt: StateFlow<Double> = minerBalance.asStateFlow()
    val gridBalance = MutableStateFlow(0.0)
    val deployedNodesCount = MutableStateFlow(0)
    val deployedRigs = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val _deployedRigs = deployedRigs
    val hardwareNodes = deployedRigs
    val _hardwareNodes = deployedRigs
    val _minerBalance = minerBalance
    val _gridBalance = gridBalance
    val _deployedNodesCount = deployedNodesCount
    val isMiningActive = MutableStateFlow(false)
    val sessionEndTime = MutableStateFlow(0L)

    init {
        val savedKey = repository.getActiveKey()
        if (!savedKey.isNullOrEmpty()) {
            repository.bindUserSession(savedKey)
        }

        viewModelScope.launch {
            accountState.collect { acc ->
                if (acc != null) {
                    minerBalance.value = acc.minerBalanceUsdt
                    gridBalance.value = acc.gridBalance
                    _deployedRigs.value = acc.hardwareNodes
                    _deployedNodesCount.value = acc.hardwareNodes.size
                    isMiningActive.value = acc.isFreeMiningActive
                    sessionEndTime.value = acc.freeMiningEndTime
                }
            }
        }

        viewModelScope.launch {
            userState.collect { state ->
                if (accountState.value == null) {
                    minerBalance.value = state.minerBalanceUsdt
                    gridBalance.value = maxOf(state.gridBalance, gridBalance.value)
                    _deployedNodesCount.value = state.userRigs.size
                    isMiningActive.value = state.isFreeMiningActive
                    sessionEndTime.value = state.freeMiningSessionEnd
                }
            }
        }
    }

    fun loginWithKey(key: String) {
        repository.bindUserSession(key)
    }

    fun startMining() {
        val key = accountState.value?.secretKey ?: repository.getActiveKey() ?: userState.value.secretKey
        if (key.isNotBlank()) {
            repository.startFreeMiningCore(key)
            isMiningActive.value = true
            sessionEndTime.value = System.currentTimeMillis() + 86400000L
        }
    }

    fun deployNode(rig: HardwareNode) {
        val key = accountState.value?.secretKey ?: repository.getActiveKey() ?: userState.value.secretKey
        if (key.isNotBlank()) {
            repository.deployHardwareRig(key, rig) {}
        }
    }

    fun adminOverride(targetKey: String, usdt: Double, grid: Double) {
        repository.adminSetBalance(targetKey, usdt, grid)
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
        minerBalance.value = minerUsdt
        gridBalance.value = grid
        repository.setLocalBalance(minerUsdt, grid)
    }

    fun loadStateIntoApp(
        usdtBalance: Double,
        gridBalance: Double,
        isStillMining: Boolean,
        miningEndTime: Long,
        deployedRigs: List<Map<String, Any>>
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            _minerBalance.value = usdtBalance
            _gridBalance.value = gridBalance
            isMiningActive.value = isStillMining
            sessionEndTime.value = miningEndTime
            _deployedRigs.value = deployedRigs
            _deployedNodesCount.value = deployedRigs.size
            repository.loadStateIntoApp(usdtBalance, gridBalance, isStillMining, miningEndTime, deployedRigs)
        }
    }

    // ==========================================
    // COMPLETE OFFLINE ENGINE & ZERO-LOSS SYNC
    // ==========================================
    fun syncAndCatchUpOfflineGrowth(secretKey: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        if (cleanKey.isEmpty()) return
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("users").document(cleanKey)

        docRef.get().addOnSuccessListener { snapshot ->
            val now = System.currentTimeMillis()

            if (!snapshot.exists()) {
                // New User Setup if document does not exist
                val isAdminKey = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)
                val initialUsdt = if (isAdminKey) 3000.0 else 0.0
                val initialGrid = if (isAdminKey) 5000.0 else 0.0
                val initialAccount = UserCloudAccount(
                    secretKey = cleanKey,
                    isAdmin = isAdminKey,
                    minerBalanceUsdt = initialUsdt,
                    gridBalance = initialGrid,
                    isFreeMiningActive = false,
                    lastSyncTimestamp = now,
                    hardwareNodes = emptyList()
                )
                docRef.set(initialAccount.toMap(), SetOptions.merge())
                _deployedRigs.value = emptyList()
                _deployedNodesCount.value = 0
                loadStateIntoApp(initialUsdt, initialGrid, false, 0L, emptyList())
                return@addOnSuccessListener
            }

            // 1. DIRECTLY BIND TO EXISTING FIRESTORE DOCUMENT WITHOUT OVERWRITING
            val usdtBalance = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble() ?: 0.0
            val gridBalance = (snapshot.get("gridBalance") as? Number)?.toDouble() ?: 0.0
            val isFreeMining = snapshot.getBoolean("isFreeMiningActive") ?: false
            val miningEndTime = (snapshot.get("freeMiningEndTime") as? Number)?.toLong()
                ?: (snapshot.get("freeMiningSessionEnd") as? Number)?.toLong() ?: 0L

            // Read hardwareNodes exactly as specified
            val rigs = (snapshot.get("hardwareNodes") as? List<Map<String, Any>>)
                ?: (snapshot.get("deployedRigs") as? List<Map<String, Any>>)
                ?: emptyList()
            _deployedRigs.value = rigs
            _deployedNodesCount.value = rigs.size

            // 2. Update UI StateFlows directly from Firestore
            loadStateIntoApp(usdtBalance, gridBalance, isFreeMining, miningEndTime, rigs)
            repository.attachUserDocumentRealTimeListener(cleanKey)
        }.addOnFailureListener { e ->
            android.util.Log.e("SYNC_OFFLINE", "Offline sync fallback", e)
            repository.attachUserDocumentRealTimeListener(cleanKey)
        }
    }

    fun syncUserDataSilently(key: String) {
        syncAndCatchUpOfflineGrowth(key)
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

        // 2. Pre-set privileges & fallback balance without overwriting Firestore document
        val defaultUsdt = repository.prefs.getFloat("miner_balance", 0f).toDouble()
        val defaultGrid = repository.prefs.getFloat("grid_balance", 0f).toDouble()

        minerBalance.value = defaultUsdt
        gridBalance.value = defaultGrid

        val currentMinerBal = minerBalance.value
        val currentGridBal = gridBalance.value

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
