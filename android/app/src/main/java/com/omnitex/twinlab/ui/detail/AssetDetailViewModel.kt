package com.omnitex.twinlab.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnitex.twinlab.data.Alert
import com.omnitex.twinlab.data.Device
import com.omnitex.twinlab.data.DeviceSocket
import com.omnitex.twinlab.data.HistoryPoint
import com.omnitex.twinlab.data.TwinLabApi
import com.omnitex.twinlab.data.WsMessage
import com.omnitex.twinlab.domain.Health
import com.omnitex.twinlab.domain.healthStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AssetDetailViewModel(
    private val deviceId: String,
    private val apiFlow: StateFlow<TwinLabApi?>,
    private val baseUrlFlow: StateFlow<String?>,
) : ViewModel() {

    data class DetailUiState(
        val device: Device? = null,
        val readings: Map<String, Double> = emptyMap(),
        val history: Map<String, List<HistoryPoint>> = emptyMap(),
        val alerts: List<Alert> = emptyList(),
        val health: Health = Health.UNKNOWN,
        val lastMsgAgeMs: Long = Long.MAX_VALUE,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var lastMsgAt: Long = 0L

    init {
        loadStatic()
        connectSocket()
        startAgeTicker()
    }

    private fun recomputeHealth(s: DetailUiState): Health =
        healthStatus(s.readings, s.device?.thresholds ?: emptyMap())

    private fun loadStatic() = viewModelScope.launch {
        val api = apiFlow.value ?: run {
            _state.update { it.copy(error = "No backend configured") }
            return@launch
        }
        try {
            val device = api.getDevices().find { it.device_id == deviceId }
            val alerts = runCatching { api.getDeviceAlerts(deviceId, 50) }.getOrDefault(emptyList())
            val history = device?.sensors.orEmpty().associateWith { sensor ->
                runCatching { api.getReadings(deviceId, sensor, limit = 60) }.getOrDefault(emptyList())
            }
            _state.update { st ->
                val next = st.copy(device = device, alerts = alerts, history = history, error = null)
                next.copy(health = recomputeHealth(next))
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Failed to load device") }
        }
    }

    private fun connectSocket() = viewModelScope.launch {
        val base = baseUrlFlow.value ?: return@launch
        val socket = DeviceSocket(base, deviceId)
        try {
            socket.messages().collect { msg ->
                when (msg) {
                    is WsMessage.Live -> {
                        lastMsgAt = System.currentTimeMillis()
                        _state.update { st ->
                            val readings = st.readings + (msg.reading.sensor to msg.reading.value)
                            val next = st.copy(readings = readings, lastMsgAgeMs = 0)
                            next.copy(health = recomputeHealth(next))
                        }
                    }
                    is WsMessage.AlertMsg ->
                        _state.update { it.copy(alerts = listOf(msg.alert) + it.alerts) }
                }
            }
        } finally {
            socket.close()
        }
    }

    private fun startAgeTicker() = viewModelScope.launch {
        while (isActive) {
            delay(1_000)
            if (lastMsgAt > 0) {
                val age = System.currentTimeMillis() - lastMsgAt
                _state.update { it.copy(lastMsgAgeMs = age) }
            }
        }
    }

    fun refresh() = loadStatic()
}
