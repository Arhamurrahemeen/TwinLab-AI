package com.omnitex.twinlab.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnitex.twinlab.data.Alert
import com.omnitex.twinlab.data.TwinLabApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlertsViewModel(
    private val apiFlow: StateFlow<TwinLabApi?>,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Content(val alerts: List<Alert>) : UiState
        data class Error(val msg: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val api = apiFlow.value ?: run {
            _state.value = UiState.Error("No backend configured")
            return@launch
        }
        try {
            _state.value = UiState.Content(api.getAlerts(limit = 100))
        } catch (e: Exception) {
            _state.value = UiState.Error(e.message ?: "Failed to load alerts")
        }
    }
}
