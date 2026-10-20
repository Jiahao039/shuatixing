package com.shutixing.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shutixing.app.di.AppContainer
import com.shutixing.app.ui.bankdetail.BankDetailScreen
import com.shutixing.app.ui.bankdetail.BankDetailViewModel
import com.shutixing.app.ui.banks.BankListViewModel
import com.shutixing.app.ui.banks.BanksScreen
import com.shutixing.app.ui.home.HomeScreen
import com.shutixing.app.ui.home.HomeViewModel
import com.shutixing.app.ui.importwizard.ImportScreen
import com.shutixing.app.ui.importwizard.ImportViewModel
import com.shutixing.app.ui.practice.PracticeMode
import com.shutixing.app.ui.practice.PracticeScreen
import com.shutixing.app.ui.practice.PracticeViewModel
import com.shutixing.app.ui.settings.SettingsScreen
import com.shutixing.app.ui.settings.SettingsViewModel
import com.shutixing.app.ui.stats.StatsScreen
import com.shutixing.app.ui.stats.StatsViewModel
import com.shutixing.app.ui.wrongbook.WrongBookScreen
import com.shutixing.app.ui.wrongbook.WrongBookViewModel

@Composable
fun AppNav(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in setOf(
        Routes.HOME,
        Routes.BANKS,
        Routes.WRONG_BOOK,
        Routes.STATS,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                HomeScreen(
                    viewModel = viewModel,
                    onStartDaily = { navController.navigate(Routes.PRACTICE_DAILY) },
                    onContinueSession = { navController.navigate(Routes.PRACTICE_DAILY) },
                    onOpenWrongBook = { navController.navigate(Routes.WRONG_BOOK) },
                    onOpenBanks = { navController.navigate(Routes.BANKS) },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.BANKS) {
                val viewModel: BankListViewModel = viewModel(factory = BankListViewModel.factory(container))
                BanksScreen(
                    viewModel = viewModel,
                    onOpenBank = { id -> navController.navigate(Routes.bankDetail(id)) },
                    onImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(Routes.WRONG_BOOK) {
                val viewModel: WrongBookViewModel = viewModel(factory = WrongBookViewModel.factory(container))
                WrongBookScreen(
                    viewModel = viewModel,
                    onStartSession = { navController.navigate(Routes.PRACTICE_WRONG) },
                )
            }
            composable(Routes.STATS) {
                val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(container))
                StatsScreen(viewModel = viewModel)
            }
            composable(Routes.SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.IMPORT) {
                val viewModel: ImportViewModel = viewModel(factory = ImportViewModel.factory(container))
                ImportScreen(
                    viewModel = viewModel,
                    onDone = { bankId ->
                        navController.popBackStack()
                        if (bankId != null) navController.navigate(Routes.bankDetail(bankId))
                    },
                )
            }
            composable(Routes.PRACTICE_DAILY) {
                val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.factory(container))
                PracticeScreen(
                    viewModel = viewModel,
                    mode = PracticeMode.DAILY,
                    onExit = { navController.popBackStack() },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(Routes.PRACTICE_WRONG) {
                val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.factory(container))
                PracticeScreen(
                    viewModel = viewModel,
                    mode = PracticeMode.WRONG_BOOK,
                    onExit = { navController.popBackStack() },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(
                route = Routes.BANK_DETAIL,
                arguments = listOf(navArgument("bankId") { type = NavType.StringType }),
            ) { entry ->
                val bankId = entry.arguments?.getString("bankId").orEmpty()
                val viewModel: BankDetailViewModel = viewModel(
                    key = bankId,
                    factory = BankDetailViewModel.factory(container, bankId),
                )
                BankDetailScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun AppBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            BottomItem("今日", Icons.Rounded.Today, currentRoute == Routes.HOME) { onNavigate(Routes.HOME) }
            BottomItem("题库", Icons.AutoMirrored.Rounded.MenuBook, currentRoute == Routes.BANKS) { onNavigate(Routes.BANKS) }
            BottomItem("错题", Icons.Rounded.ErrorOutline, currentRoute == Routes.WRONG_BOOK) { onNavigate(Routes.WRONG_BOOK) }
            BottomItem("统计", Icons.Rounded.Insights, currentRoute == Routes.STATS) { onNavigate(Routes.STATS) }
        }
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = tween(220),
        label = "bottomBackground",
    )
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
