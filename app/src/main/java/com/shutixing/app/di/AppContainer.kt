package com.shutixing.app.di

import android.content.Context
import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.data.repository.BankRepository
import com.shutixing.app.data.repository.PracticeRepository
import com.shutixing.app.data.repository.SettingsRepository
import com.shutixing.app.data.repository.StatsRepository
import com.shutixing.app.parser.PdfTextExtractor
import com.shutixing.app.ui.practice.SessionStore

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val bankRepository = BankRepository(database)
    val practiceRepository = PracticeRepository(database)
    val statsRepository = StatsRepository(database)
    val settingsRepository = SettingsRepository(context)
    val sessionStore = SessionStore(context)
    val pdfTextExtractor = PdfTextExtractor(context.applicationContext)
}
