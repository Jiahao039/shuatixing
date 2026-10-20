package com.shutixing.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.PracticeRepository
import com.shutixing.app.data.repository.SettingsRepository
import com.shutixing.app.data.repository.StatsRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.stats.StatsCalculator
import com.shutixing.app.ui.practice.PracticeMode
import com.shutixing.app.ui.practice.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val loading: Boolean = true,
    val dailyTarget: Int = 30,
    val todayAnswered: Int = 0,
    val todayCorrect: Int = 0,
    val streak: Int = 0,
    val wrongCount: Int = 0,
    val mastered: Int = 0,
    val total: Int = 0,
    val hasSession: Boolean = false,
) {
    val progress: Float
        get() = if (dailyTarget <= 0) 0f else (todayAnswered.toFloat() / dailyTarget).coerceIn(0f, 1f)

    val todayAccuracy: Float
        get() = if (todayAnswered == 0) 0f else todayCorrect.toFloat() / todayAnswered
}

class HomeViewModel(
    private val practiceRepository: PracticeRepository,
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: SessionStore,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = todayProvider()
            val target = settingsRepository.dailyTarget.first()
            val answered = statsRepository.todayAnswered(today)
            val correct = statsRepository.todayCorrect(today)
            val streak = StatsCalculator.streak(statsRepository.activeDates(), today)
            val wrong = practiceRepository.wrongBook().size
            val mastered = statsRepository.totalMastered()
            val total = statsRepository.totalQuestions()
            val hasSession = sessionStore.session.first()?.mode == PracticeMode.DAILY.name
            _state.value = HomeUiState(
                loading = false,
                dailyTarget = target,
                todayAnswered = answered,
                todayCorrect = correct,
                streak = streak,
                wrongCount = wrong,
                mastered = mastered,
                total = total,
                hasSession = hasSession,
            )
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                HomeViewModel(
                    practiceRepository = container.practiceRepository,
                    statsRepository = container.statsRepository,
                    settingsRepository = container.settingsRepository,
                    sessionStore = container.sessionStore,
                )
            }
        }
    }
}
