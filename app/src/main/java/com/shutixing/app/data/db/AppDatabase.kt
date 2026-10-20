package com.shutixing.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BankEntity::class, QuestionEntity::class, ReviewStateEntity::class, AttemptEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bankDao(): BankDao
    abstract fun questionDao(): QuestionDao
    abstract fun reviewDao(): ReviewDao
    abstract fun attemptDao(): AttemptDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "shuatixing.db")
                .build()
    }
}
