package com.shutixing.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "banks")
data class BankEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceFileName: String?,
    val createdAt: Long,
    val sortOrder: Int,
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = BankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bankId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bankId"), Index("stemHash")],
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val bankId: String,
    val type: String,
    val stem: String,
    val optionsJson: String,
    val answer: String,
    val explanation: String?,
    val orderIndex: Int,
    val stemHash: String,
    val isFavorite: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "review_states",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReviewStateEntity(
    @PrimaryKey val questionId: String,
    val level: Int,
    val nextReviewDate: String,
    val consecutiveCorrect: Int,
    val wrongCount: Int,
    val lastAnsweredAt: Long?,
)

@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("localDate"), Index("questionId")],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val questionId: String,
    val localDate: String,
    val answeredAt: Long,
    val isCorrect: Boolean,
    val selectedAnswer: String,
    val durationMs: Long,
    val mode: String,
)

data class DailyAggregate(
    val localDate: String,
    val answered: Int,
    val correct: Int,
)
