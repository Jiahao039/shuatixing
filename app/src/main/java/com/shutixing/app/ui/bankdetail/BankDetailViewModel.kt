package com.shutixing.app.ui.bankdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.BankRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.parser.StemHasher
import com.shutixing.app.parser.model.ParsedQuestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DetailFilter { ALL, FAVORITE, SINGLE, MULTIPLE, JUDGE }

data class BankDetailUiState(
    val loading: Boolean = true,
    val bankName: String = "",
    val questions: List<Question> = emptyList(),
    val query: String = "",
    val filter: DetailFilter = DetailFilter.ALL,
) {
    val filtered: List<Question>
        get() {
            val byType = when (filter) {
                DetailFilter.ALL -> questions
                DetailFilter.FAVORITE -> questions.filter { it.isFavorite }
                DetailFilter.SINGLE -> questions.filter { it.type == QuestionType.SINGLE }
                DetailFilter.MULTIPLE -> questions.filter { it.type == QuestionType.MULTIPLE }
                DetailFilter.JUDGE -> questions.filter { it.type == QuestionType.JUDGE }
            }
            return if (query.isBlank()) byType else byType.filter { it.stem.contains(query.trim(), ignoreCase = true) }
        }
}

class BankDetailViewModel(
    private val bankId: String,
    private val bankRepository: BankRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BankDetailUiState())
    val state: StateFlow<BankDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val bank = bankRepository.getBank(bankId)
            if (bank != null) _state.update { it.copy(bankName = bank.name) }
            bankRepository.observeQuestions(bankId).collect { questions ->
                _state.update { it.copy(loading = false, questions = questions) }
            }
        }
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun setFilter(filter: DetailFilter) = _state.update { it.copy(filter = filter) }

    fun toggleFavorite(question: Question) {
        viewModelScope.launch {
            bankRepository.setFavorite(question.id, !question.isFavorite)
        }
    }

    fun updateQuestion(question: Question) {
        val updated = question.copy(stemHash = StemHasher.hash(question.stem, question.options))
        viewModelScope.launch { bankRepository.updateQuestion(updated) }
    }

    fun addQuestion(parsed: ParsedQuestion) {
        viewModelScope.launch { bankRepository.addQuestion(bankId, parsed) }
    }

    fun deleteQuestion(id: String) {
        viewModelScope.launch { bankRepository.deleteQuestion(id) }
    }

    suspend fun exportData(): Pair<Bank, List<Question>>? = bankRepository.exportData(bankId)

    fun deleteBank(onDone: () -> Unit) {
        viewModelScope.launch {
            bankRepository.deleteBank(bankId)
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer, bankId: String) = viewModelFactory {
            initializer { BankDetailViewModel(bankId, container.bankRepository) }
        }
    }
}
