package com.shutixing.app.data.export

import kotlinx.serialization.Serializable

const val BANK_FORMAT = "shuatixing.bank"
const val BANK_FORMAT_VERSION = 1

@Serializable
data class BankFile(
    val format: String = BANK_FORMAT,
    val version: Int = BANK_FORMAT_VERSION,
    val exportedAt: String,
    val bank: BankInfoDto,
    val questions: List<QuestionDto>,
)

@Serializable
data class BankInfoDto(
    val name: String,
    val sourceFileName: String? = null,
)

@Serializable
data class QuestionDto(
    val type: String,
    val stem: String,
    val options: List<OptionDto> = emptyList(),
    val answer: String,
    val explanation: String? = null,
)

@Serializable
data class OptionDto(
    val key: String,
    val text: String,
)
