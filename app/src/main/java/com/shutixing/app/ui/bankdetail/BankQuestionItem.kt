package com.shutixing.app.ui.bankdetail

import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.ReviewState

enum class QuestionProgress(val label: String) {
    NEW("新题"),
    LEARNING("复习中"),
    WRONG("错题"),
    MASTERED("已掌握"),
}

data class BankQuestionItem(
    val question: Question,
    val review: ReviewState?,
) {
    val progress: QuestionProgress
        get() = when {
            review == null -> QuestionProgress.NEW
            review.isInWrongBook -> QuestionProgress.WRONG
            review.isMastered -> QuestionProgress.MASTERED
            review.lastAnsweredAt != null -> QuestionProgress.LEARNING
            else -> QuestionProgress.NEW
        }
}
