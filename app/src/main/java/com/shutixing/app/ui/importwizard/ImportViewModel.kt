package com.shutixing.app.ui.importwizard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.BankRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.parser.Normalizer
import com.shutixing.app.parser.PdfImportException
import com.shutixing.app.parser.PdfTextExtractor
import com.shutixing.app.parser.QuestionParser
import com.shutixing.app.parser.StemHasher
import com.shutixing.app.parser.model.ParsedQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImportPhase { IDLE, PARSING, REVIEW, IMPORTING, DONE, ERROR }

data class ImportItem(
    val question: ParsedQuestion,
    val hash: String,
    val include: Boolean = true,
    val duplicate: Boolean = false,
)

data class ImportUiState(
    val phase: ImportPhase = ImportPhase.IDLE,
    val fileName: String = "",
    val progress: Float = 0f,
    val items: List<ImportItem> = emptyList(),
    val banks: List<Bank> = emptyList(),
    val targetBankId: String? = null,
    val bankName: String = "",
    val skipDuplicates: Boolean = true,
    val error: String? = null,
    val importedBankId: String? = null,
    val importedCount: Int = 0,
) {
    val duplicateCount: Int get() = items.count { it.duplicate }
    val selectedCount: Int get() = items.count { it.include && !(skipDuplicates && it.duplicate) }
}

class ImportViewModel(
    private val extractor: PdfTextExtractor,
    private val bankRepository: BankRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var knownHashes: Set<String> = emptySet()

    fun parse(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(phase = ImportPhase.PARSING, fileName = fileName, progress = 0f, error = null)
            }
            try {
                val lines = extractor.extract(uri) { done, total ->
                    _state.update {
                        it.copy(progress = if (total == 0) 0f else done.toFloat() / total)
                    }
                }
                val parsed = withContext(Dispatchers.Default) {
                    val normalized = Normalizer.normalize(lines)
                    QuestionParser.parse(normalized).questions
                }
                if (parsed.isEmpty()) {
                    _state.update {
                        it.copy(
                            phase = ImportPhase.ERROR,
                            error = "未识别到题目。可能是扫描版 PDF 或排版特殊，暂不支持。",
                        )
                    }
                    return@launch
                }
                knownHashes = bankRepository.allHashes()
                val items = parsed.map { question ->
                    val hash = StemHasher.hash(question.stem, question.options)
                    ImportItem(
                        question = question,
                        hash = hash,
                        include = hash !in knownHashes,
                        duplicate = hash in knownHashes,
                    )
                }
                _state.update {
                    it.copy(
                        phase = ImportPhase.REVIEW,
                        progress = 1f,
                        items = items,
                        banks = bankRepository.allBanks(),
                        bankName = fileName.substringBeforeLast('.', fileName).ifBlank { "导入题库" },
                    )
                }
            } catch (e: PdfImportException) {
                _state.update {
                    it.copy(phase = ImportPhase.ERROR, error = e.message ?: "PDF 读取失败")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = ImportPhase.ERROR, error = e.message ?: "解析失败")
                }
            }
        }
    }

    fun toggleInclude(index: Int) {
        _state.update { current ->
            current.copy(
                items = current.items.mapIndexed { i, item ->
                    if (i == index) item.copy(include = !item.include) else item
                },
            )
        }
    }

    fun editItem(index: Int, parsed: ParsedQuestion) {
        val hash = StemHasher.hash(parsed.stem, parsed.options)
        _state.update { current ->
            current.copy(
                items = current.items.mapIndexed { i, item ->
                    if (i == index) {
                        item.copy(
                            question = parsed,
                            hash = hash,
                            duplicate = hash in knownHashes,
                            include = hash !in knownHashes,
                        )
                    } else {
                        item
                    }
                },
            )
        }
    }

    fun setBankName(value: String) = _state.update { it.copy(bankName = value) }

    fun selectTarget(bankId: String?) = _state.update { it.copy(targetBankId = bankId) }

    fun setSkipDuplicates(value: Boolean) = _state.update { it.copy(skipDuplicates = value) }

    fun importQuestions() {
        val current = _state.value
        val chosen = current.items
            .filter { it.include && !(current.skipDuplicates && it.duplicate) }
            .map { it.question }
        if (chosen.isEmpty()) {
            _state.update { it.copy(error = "没有可导入的题目") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(phase = ImportPhase.IMPORTING, error = null) }
            try {
                val targetId = current.targetBankId
                val bankId = if (targetId != null) {
                    bankRepository.appendQuestions(targetId, chosen)
                    targetId
                } else {
                    bankRepository.createBank(
                        name = current.bankName.ifBlank { "导入题库" },
                        sourceFileName = current.fileName,
                        questions = chosen,
                    )
                }
                _state.update {
                    it.copy(
                        phase = ImportPhase.DONE,
                        importedBankId = bankId,
                        importedCount = chosen.size,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = ImportPhase.REVIEW, error = e.message ?: "导入失败")
                }
            }
        }
    }

    fun reset() {
        _state.value = ImportUiState(phase = ImportPhase.IDLE)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ImportViewModel(container.pdfTextExtractor, container.bankRepository)
            }
        }
    }
}
