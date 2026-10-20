package com.shutixing.app.ui.banks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.StatsRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.BankMastery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BankListUiState(
    val loading: Boolean = true,
    val banks: List<BankMastery> = emptyList(),
    val query: String = "",
) {
    val filtered: List<BankMastery>
        get() = if (query.isBlank()) banks else banks.filter { it.bankName.contains(query.trim(), ignoreCase = true) }
}

class BankListViewModel(
    private val statsRepository: StatsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BankListUiState())
    val state: StateFlow<BankListUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = false, banks = statsRepository.bankMastery())
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { BankListViewModel(container.statsRepository) }
        }
    }
}
