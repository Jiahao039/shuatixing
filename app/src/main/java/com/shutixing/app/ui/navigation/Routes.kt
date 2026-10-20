package com.shutixing.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val BANKS = "banks"
    const val WRONG_BOOK = "wrongbook"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val PRACTICE_DAILY = "practice/daily"
    const val PRACTICE_WRONG = "practice/wrong"
    const val BANK_DETAIL = "bank/{bankId}"

    fun bankDetail(bankId: String) = "bank/$bankId"
}
