package com.rakshak.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.core.mode.AppMode
import com.rakshak.core.mode.ModeManager
import com.rakshak.core.readiness.P0PipelineStatus
import com.rakshak.core.readiness.ReadinessChecker
import com.rakshak.core.readiness.ReadinessStatus
import com.rakshak.core.ai.AIIncidentAssessment
import com.rakshak.core.sensor.SensorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.core.content.ContextCompat

enum class AiState {
    IDLE, ANALYZING, READY, FAILED
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val checker = ReadinessChecker(application.applicationContext)

    val readinessItems: StateFlow<List<ReadinessStatus>> = checker.statusFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _currentMode = MutableStateFlow(ModeManager.currentMode)
    val currentMode: StateFlow<AppMode> = _currentMode

    val isServiceRunning = P0PipelineStatus.isServiceRunning
    val detectorState = P0PipelineStatus.detectorState
    val lastAlertResult = P0PipelineStatus.lastAlertResult

    // AI States
    data class TelemetryState(val peakGForce: Float = 0f, val jerkGs: Float = 0f, val gyroRadS: Float = 0f, val locationStatus: String = "unavailable")

    private val _lastTelemetry = MutableStateFlow<TelemetryState?>(null)
    val lastTelemetry: StateFlow<TelemetryState?> = _lastTelemetry

    private val _aiState = MutableStateFlow(AiState.IDLE)
    val aiState: StateFlow<AiState> = _aiState

    private val _aiResultText = MutableStateFlow("")
    val aiResultText: StateFlow<String> = _aiResultText

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded

    init {
        refresh()
        viewModelScope.launch {
            AIIncidentAssessment.initialize(application.applicationContext)
            _isModelLoaded.value = AIIncidentAssessment.isReady
            
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

    fun triggerSimulatedCrash() {
        val intent = Intent(getApplication(), SensorService::class.java).apply {
            action = SensorService.ACTION_SIMULATE_CRASH
        }
        ContextCompat.startForegroundService(getApplication(), intent)

        // Trigger AI in parallel
        viewModelScope.launch {
            _aiState.value = AiState.ANALYZING
            _aiResultText.value = ""
            
            val tel = TelemetryState(12.5f, 850f, 12f, "available")
            _lastTelemetry.value = tel
            
            // Mock telemetry values for the hackathon simulate crash
            val result = AIIncidentAssessment.generateAssessment(
                peakGForce = tel.peakGForce,
                jerkGs = tel.jerkGs,
                gyroRadS = tel.gyroRadS,
                locationStatus = tel.locationStatus
            )
            _aiResultText.value = result
            _aiState.value = AiState.READY
        }
    }

    fun triggerInjectTrace() {
        val intent = Intent(getApplication(), SensorService::class.java).apply {
            action = SensorService.ACTION_INJECT_TRACE
        }
        ContextCompat.startForegroundService(getApplication(), intent)

        // Trigger AI in parallel for ejection profile
        viewModelScope.launch {
            _aiState.value = AiState.ANALYZING
            _aiResultText.value = ""
            
            val tel = TelemetryState(15.0f, 1200f, 28f, "unavailable")
            _lastTelemetry.value = tel
            
            // Mock telemetry values for synthetic trace (Ejection)
            val result = AIIncidentAssessment.generateAssessment(
                peakGForce = tel.peakGForce,
                jerkGs = tel.jerkGs,
                gyroRadS = tel.gyroRadS,
                locationStatus = tel.locationStatus
            )
            _aiResultText.value = result
            _aiState.value = AiState.READY
        }
    }

    fun explainAlert() {
        val tel = _lastTelemetry.value ?: return
        viewModelScope.launch {
            _aiState.value = AiState.ANALYZING
            _aiResultText.value = ""
            val explanation = AIIncidentAssessment.explainAlert(tel.peakGForce, tel.jerkGs, tel.gyroRadS)
            _aiResultText.value = explanation
            _aiState.value = AiState.READY
        }
    }

    fun verifyWithCamera(bitmap: android.graphics.Bitmap?) {
        val tel = _lastTelemetry.value ?: TelemetryState(10f, 500f, 10f)
        viewModelScope.launch {
            _aiState.value = AiState.ANALYZING
            _aiResultText.value = ""
            val assessment = AIIncidentAssessment.verifyWithCamera(tel.peakGForce, tel.jerkGs, tel.gyroRadS, bitmap)
            _aiResultText.value = assessment
            _aiState.value = AiState.READY
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 5_000L
    }
}
