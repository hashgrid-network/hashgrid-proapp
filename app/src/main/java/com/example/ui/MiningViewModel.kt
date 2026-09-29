package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.MiningRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
}

class MiningViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MiningRepository(application)

    val userState: StateFlow<UserMiningState> = repository.userState
    val cryptoPrices: StateFlow<List<CryptoTickerPrice>> = repository.cryptoPrices

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

    fun selectTab(tab: AppNavTab) {
        _currentTab.value = tab
    }

    fun startFreeMining() {
        repository.startFreeMiningSession()
        emitToast("Core Online! 24-Hour Free GRID Mining Session Initiated.")
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

    private fun emitToast(msg: String) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast(msg))
        }
    }
}
