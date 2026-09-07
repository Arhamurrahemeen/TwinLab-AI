package com.omnitex.twinlab.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnitex.twinlab.data.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface TestResult {
    data object Idle : TestResult
    data object Testing : TestResult
    data object Ok : TestResult
    data class Failed(val reason: String) : TestResult
}

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val currentUrl: StateFlow<String?> =
        settings.baseUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _test = MutableStateFlow<TestResult>(TestResult.Idle)
    val test: StateFlow<TestResult> = _test

    fun testAndSave(url: String) = viewModelScope.launch {
        _test.value = TestResult.Testing
        val clean = url.trim().trimEnd('/')
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            _test.value = TestResult.Failed("URL must start with http:// or https://")
            return@launch
        }
        val client = HttpClient(OkHttp)
        try {
            val resp: HttpResponse = withTimeout(4000) { client.get("$clean/health") }
            if (resp.status.value in 200..299) {
                settings.setBaseUrl(clean)
                _test.value = TestResult.Ok
            } else {
                _test.value = TestResult.Failed("HTTP ${resp.status.value}")
            }
        } catch (e: Exception) {
            _test.value = TestResult.Failed(e.message ?: "unreachable")
        } finally {
            client.close()
        }
    }
}
