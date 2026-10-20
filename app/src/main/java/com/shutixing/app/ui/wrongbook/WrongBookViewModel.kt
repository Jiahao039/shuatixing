package com.shutixing.app.ui.wrongbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.PracticeRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.model.QuestionWithState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WrongBookUiState(
    val loading: Boolean = true,
    val items: List<QuestionWithState> = emptyList(),
    val filter: QuestionType? = null,
) {
    val filtered: List<QuestionWithState>
        get() = if (filter == null) items else items.filter { it.question.type == filter }
}

class WrongBookViewModel(
    private val practiceRepository: PracticeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WrongBookUiState())
    val state: StateFlow<WrongBookUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val items = practiceRepository.wrongBook()
            _state.value = _state.value.copy(loading = false, items = items)
        }
    }

    fun setFilter(type: QuestionType?) {
        _state.value = _state.value.copy(filter = type)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { WrongBookViewModel(container.practiceRepository) }
        }
    }
}
