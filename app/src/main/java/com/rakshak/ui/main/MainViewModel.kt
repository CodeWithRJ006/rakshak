package com.rakshak.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.core.mode.AppMode
import com.rakshak.core.mode.ModeManager
import com.rakshak.core.readiness.P0PipelineStatus
import com.rakshak.core.readiness.ReadinessChecker
import com.rakshak.core.readiness.ReadinessStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val checker = ReadinessChecker(application.applicationContext)

    val readinessItems: StateFlow<List<ReadinessStatus>> = checker.statusFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _currentMode = kotlinx.coroutines.flow.MutableStateFlow(ModeManager.currentMode)
    val currentMode: StateFlow<AppMode> = _currentMode

    val isServiceRunning = P0PipelineStatus.isServiceRunning
    val detectorState = P0PipelineStatus.detectorState
    val lastAlertResult = P0PipelineStatus.lastAlertResult

    init {
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                refresh()
            }
        }
    }

    fun refresh() {
        checker.refresh()
    }

    fun setMode(mode: AppMode) {
        ModeManager.setMode(mode)
        _currentMode.value = mode
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 5_000L
    }
}
