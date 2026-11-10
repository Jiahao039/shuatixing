package com.shutixing.app.ui.bankdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.BankRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionRecord
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.parser.StemHasher
import com.shutixing.app.parser.model.ParsedQuestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DetailFilter { ALL, FAVORITE, SINGLE, MULTIPLE, JUDGE }

data class BankDetailUiState(
    val loading: Boolean = true,
    val bankName: String = "",
    val items: List<BankQuestionItem> = emptyList(),
    val query: String = "",
    val filter: DetailFilter = DetailFilter.ALL,
) {
    val practiced: Int get() = items.count { it.review?.lastAnsweredAt != null }
    val mastered: Int get() = items.count { it.progress == QuestionProgress.MASTERED }
    val wrong: Int get() = items.count { it.progress == QuestionProgress.WRONG }

    val filtered: List<BankQuestionItem>
        get() {
            val byType = when (filter) {
                DetailFilter.ALL -> items
                DetailFilter.FAVORITE -> items.filter { it.question.isFavorite }
                DetailFilter.SINGLE -> items.filter { it.question.type == QuestionType.SINGLE }
                DetailFilter.MULTIPLE -> items.filter { it.question.type == QuestionType.MULTIPLE }
                DetailFilter.JUDGE -> items.filter { it.question.type == QuestionType.JUDGE }
            }
            return if (query.isBlank()) {
                byType
            } else {
                byType.filter { it.question.stem.contains(query.trim(), ignoreCase = true) }
            }
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
            combine(
                bankRepository.observeQuestions(bankId),
                bankRepository.observeReviewStates(bankId),
            ) { questions, reviews ->
                questions.map { question -> BankQuestionItem(question, reviews[question.id]) }
            }.collect { items ->
                _state.update { it.copy(loading = false, items = items) }
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

    suspend fun exportRecords(): Pair<Bank, List<QuestionRecord>>? = bankRepository.practiceRecords(bankId)

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
