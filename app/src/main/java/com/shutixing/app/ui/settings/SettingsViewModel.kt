package com.shutixing.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.data.export.BankImporter
import com.shutixing.app.data.export.ImportResult
import com.shutixing.app.data.repository.BankRepository
import com.shutixing.app.data.repository.SettingsRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.ThemeMode
import com.shutixing.app.ui.practice.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val dailyTarget: Int = 30,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val message: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val bankRepository: BankRepository,
    private val database: AppDatabase,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.dailyTarget.collect { value ->
                _state.update { it.copy(dailyTarget = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun setDailyTarget(value: Int) {
        viewModelScope.launch { settingsRepository.setDailyTarget(value) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun importBankJson(text: String) {
        viewModelScope.launch {
            when (val result = BankImporter.parse(text)) {
                is ImportResult.Success -> {
                    bankRepository.createBank(
                        name = result.bankName,
                        sourceFileName = result.sourceFileName,
                        questions = result.questions,
                    )
                    _state.update { it.copy(message = "已导入 ${result.questions.size} 题") }
                }
                is ImportResult.Failure -> {
                    _state.update { it.copy(message = result.message) }
                }
            }
        }
    }

    fun clearAll(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { database.clearAllTables() }
            sessionStore.clear()
            _state.update { it.copy(message = "数据已清空") }
            onDone()
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    bankRepository = container.bankRepository,
                    database = container.database,
                    sessionStore = container.sessionStore,
                )
            }
        }
    }
}
