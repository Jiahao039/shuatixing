package com.shutixing.app.data.export

import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Question
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.OffsetDateTime

object BankExporter {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun toJson(
        bank: Bank,
        questions: List<Question>,
        exportedAt: String = OffsetDateTime.now().toString(),
    ): String {
        val file = BankFile(
            exportedAt = exportedAt,
            bank = BankInfoDto(name = bank.name, sourceFileName = bank.sourceFileName),
            questions = questions.map { question ->
                QuestionDto(
                    type = question.type.name,
                    stem = question.stem,
                    options = question.options.map { OptionDto(it.key, it.text) },
                    answer = question.answer,
                    explanation = question.explanation,
                )
            },
        )
        return json.encodeToString(file)
    }

    fun write(stream: OutputStream, bank: Bank, questions: List<Question>) {
        stream.use { it.write(toJson(bank, questions).toByteArray(Charsets.UTF_8)) }
    }
}
