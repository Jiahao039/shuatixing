package com.shutixing.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.PracticeRepository
import com.shutixing.app.data.repository.SettingsRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.AttemptMode
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.session.SessionEngine
import com.shutixing.app.domain.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class PracticeMode { DAILY, WRONG_BOOK }

data class PracticeUiState(
    val loading: Boolean = true,
    val mode: PracticeMode = PracticeMode.DAILY,
    val current: Question? = null,
    val selected: Set<String> = emptySet(),
    val submitted: Boolean = false,
    val lastCorrect: Boolean? = null,
    val inRetest: Boolean = false,
    val retestRemaining: Int = 0,
    val progress: Float = 0f,
    val answeredMain: Int = 0,
    val mainTotal: Int = 0,
    val totalAnswered: Int = 0,
    val correctCount: Int = 0,
    val finished: Boolean = false,
    val empty: Boolean = false,
    val error: String? = null,
    val dailyTarget: Int = 30,
)

class PracticeViewModel(
    private val practiceRepository: PracticeRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: SessionStore,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _state = MutableStateFlow(PracticeUiState())
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    private var session: SessionState? = null
    private var questions: Map<String, Question> = emptyMap()
    private var currentMode: PracticeMode = PracticeMode.DAILY
    private var questionStartedAt: Long = 0L

    fun start(mode: PracticeMode) {
        currentMode = mode
        viewModelScope.launch {
            _state.value = PracticeUiState(loading = true, mode = mode)
            try {
                val today = todayProvider()
                val target = settingsRepository.dailyTarget.first()
                val saved = sessionStore.session.first()
                if (saved != null && saved.mode == mode.name && saved.date == today.toString()) {
                    restore(saved, target)
                } else {
                    sessionStore.clear()
                    val ids = when (mode) {
                        PracticeMode.DAILY ->
                            practiceRepository.buildDailyQueue(target, today).map { it.id }
                        PracticeMode.WRONG_BOOK ->
                            practiceRepository.wrongBook().map { it.question.id }
                    }
                    if (ids.isEmpty()) {
                        _state.value = PracticeUiState(
                            loading = false,
                            mode = mode,
                            empty = true,
                            dailyTarget = target,
                        )
                        return@launch
                    }
                    questions = practiceRepository.getQuestions(ids).associateBy { it.id }
                    val fresh = SessionEngine.start(ids)
                    session = fresh
                    questionStartedAt = nowProvider()
                    publish(fresh, mode, target, loading = false)
                }
            } catch (e: Exception) {
                _state.value = PracticeUiState(
                    loading = false,
                    mode = mode,
                    error = e.message ?: "加载失败",
                )
            }
        }
    }

    fun select(key: String) {
        val current = _state.value
        if (current.submitted) return
        val question = current.current ?: return
        val next = if (question.type == QuestionType.MULTIPLE) {
            if (key in current.selected) current.selected - key else current.selected + key
        } else {
            setOf(key)
        }
        _state.update { it.copy(selected = next) }
    }

    fun submit() {
        val current = _state.value
        val question = current.current ?: return
        if (current.submitted || current.selected.isEmpty()) return
        viewModelScope.launch {
            try {
                val snapshot = session ?: return@launch
                val today = todayProvider()
                val now = nowProvider()
                val mode = when {
                    snapshot.inRetest -> AttemptMode.RETEST
                    current.mode == PracticeMode.WRONG_BOOK -> AttemptMode.WRONG_BOOK
                    else -> AttemptMode.DAILY
                }
                val outcome = practiceRepository.answer(
                    question = question,
                    selected = current.selected.sorted().joinToString(""),
                    mode = mode,
                    today = today,
                    now = now,
                    durationMs = (now - questionStartedAt).coerceAtLeast(0L),
                )
                val updated = SessionEngine.onAnswered(snapshot, outcome.isCorrect)
                session = updated
                _state.update {
                    it.copy(
                        submitted = true,
                        lastCorrect = outcome.isCorrect,
                        totalAnswered = updated.totalAnswered,
                        correctCount = updated.correctCount,
                    )
                }
                persist()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "提交失败") }
            }
        }
    }

    fun next() {
        val current = _state.value
        if (!current.submitted) return
        val snapshot = session ?: return
        val updated = SessionEngine.advance(snapshot)
        session = updated
        questionStartedAt = nowProvider()

        if (updated.finished) {
            viewModelScope.launch { sessionStore.clear() }
            _state.update {
                it.copy(
                    current = null,
                    finished = true,
                    submitted = false,
                    selected = emptySet(),
                    lastCorrect = null,
                    inRetest = false,
                    retestRemaining = 0,
                    progress = updated.progress,
                    answeredMain = updated.answeredMain,
                    mainTotal = updated.mainQueue.size,
                    totalAnswered = updated.totalAnswered,
                    correctCount = updated.correctCount,
                )
            }
        } else {
            _state.update {
                it.copy(
                    current = updated.currentId?.let { id -> questions[id] },
                    submitted = false,
                    selected = emptySet(),
                    lastCorrect = null,
                    inRetest = updated.inRetest,
                    retestRemaining = updated.retestQueue.size,
                    progress = updated.progress,
                    answeredMain = updated.answeredMain,
                    mainTotal = updated.mainQueue.size,
                )
            }
            persist()
        }
    }

    private suspend fun restore(saved: SavedSession, target: Int) {
        val loaded = practiceRepository.getQuestions(saved.queue)
        if (loaded.isEmpty()) {
            sessionStore.clear()
            _state.value = PracticeUiState(loading = false, mode = currentMode, empty = true, dailyTarget = target)
            return
        }
        questions = loaded.associateBy { it.id }
        val restored = SessionState(
            mainQueue = saved.queue,
            index = saved.index.coerceIn(0, saved.queue.size),
            retestQueue = saved.retestQueue,
            inRetest = saved.inRetest,
            answeredMain = saved.answeredMain,
            totalAnswered = saved.totalAnswered,
            correctCount = saved.correctCount,
            finished = false,
        )
        session = restored
        questionStartedAt = nowProvider()
        publish(restored, currentMode, target)
    }

    private fun publish(sessionState: SessionState, mode: PracticeMode, target: Int, loading: Boolean = false) {
        _state.value = PracticeUiState(
            loading = loading,
            mode = mode,
            current = sessionState.currentId?.let { questions[it] },
            submitted = false,
            selected = emptySet(),
            lastCorrect = null,
            inRetest = sessionState.inRetest,
            retestRemaining = sessionState.retestQueue.size,
            progress = sessionState.progress,
            answeredMain = sessionState.answeredMain,
            mainTotal = sessionState.mainQueue.size,
            totalAnswered = sessionState.totalAnswered,
            correctCount = sessionState.correctCount,
            finished = sessionState.finished || sessionState.currentId == null,
            empty = sessionState.mainQueue.isEmpty(),
            dailyTarget = target,
        )
    }

    private fun persist() {
        val snapshot = session ?: return
        viewModelScope.launch {
            sessionStore.save(
                SavedSession(
                    date = todayProvider().toString(),
                    mode = currentMode.name,
                    queue = snapshot.mainQueue,
                    retestQueue = snapshot.retestQueue,
                    index = snapshot.index,
                    inRetest = snapshot.inRetest,
                    answeredMain = snapshot.answeredMain,
                    totalAnswered = snapshot.totalAnswered,
                    correctCount = snapshot.correctCount,
                ),
            )
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                PracticeViewModel(
                    practiceRepository = container.practiceRepository,
                    settingsRepository = container.settingsRepository,
                    sessionStore = container.sessionStore,
                )
            }
        }
    }
}
