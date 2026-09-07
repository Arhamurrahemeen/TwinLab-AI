package com.omnitex.twinlab.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnitex.twinlab.data.Device
import com.omnitex.twinlab.data.TwinLabApi
import com.omnitex.twinlab.domain.Health
import com.omnitex.twinlab.domain.healthStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AssetListViewModel(
    private val apiFlow: StateFlow<TwinLabApi?>,
) : ViewModel() {

    data class Row(
        val device: Device,
        val readings: Map<String, Double>,
        val health: Health,
    )

    sealed interface UiState {
        data object Loading : UiState
        data class Content(val groups: Map<String, List<Row>>) : UiState
        data class Error(val msg: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var deviceCache: List<Device>? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                load()
                delay(5_000)
            }
        }
    }

    fun refresh() {
        deviceCache = null
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val api = apiFlow.value ?: run {
            _state.value = UiState.Error("No backend configured")
            return
        }
        try {
            val devices = deviceCache ?: api.getDevices().also { deviceCache = it }
            val rows = devices.map { d ->
                val readings = runCatching { api.getLastKnown(d.device_id) }
                    .getOrDefault(emptyMap())
                    .mapValues { it.value.value }
                Row(d, readings, healthStatus(readings, d.thresholds))
            }
            val groups = rows
                .groupBy { it.device.group }
                .toSortedMap()
            _state.value = UiState.Content(groups)
        } catch (e: Exception) {
            _state.value = UiState.Error(e.message ?: "Failed to load assets")
        }
    }
}
