package com.shutixing.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.StatsRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.BankMastery
import com.shutixing.app.domain.model.DailyStat
import com.shutixing.app.domain.stats.StatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class StatsUiState(
    val loading: Boolean = true,
    val daily: List<DailyStat> = emptyList(),
    val streak: Int = 0,
    val banks: List<BankMastery> = emptyList(),
    val totalQuestions: Int = 0,
    val totalMastered: Int = 0,
) {
    val totalAnswered: Int get() = daily.sumOf { it.answered }
    val overallAccuracy: Float get() = StatsCalculator.accuracy(daily)
}

class StatsViewModel(
    private val statsRepository: StatsRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = todayProvider()
            val daily = statsRepository.observeDailyStats().first()
            _state.value = StatsUiState(
                loading = false,
                daily = daily,
                streak = StatsCalculator.streak(statsRepository.activeDates(), today),
                banks = statsRepository.bankMastery(),
                totalQuestions = statsRepository.totalQuestions(),
                totalMastered = statsRepository.totalMastered(),
            )
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { StatsViewModel(container.statsRepository) }
        }
    }
}
