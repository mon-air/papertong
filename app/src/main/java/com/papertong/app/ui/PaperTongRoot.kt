package com.papertong.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.papertong.app.ui.nav.Routes
import com.papertong.app.ui.screens.about.AboutScreen
import com.papertong.app.ui.screens.history.HistoryScreen
import com.papertong.app.ui.screens.ocr.OcrScreen
import com.papertong.app.ui.screens.settings.SettingsScreen
import com.papertong.app.ui.screens.translate.TranslateScreen

@Composable
fun PaperTongRoot(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.TRANSLATE,
        modifier = modifier
    ) {
        composable(Routes.TRANSLATE) { entry ->
            TranslateScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenOcr = { navController.navigate(Routes.OCR) },
                incomingText = entry.savedStateHandle.get<String>(Routes.KEY_OCR_RESULT),
                onIncomingTextConsumed = {
                    entry.savedStateHandle.remove<String>(Routes.KEY_OCR_RESULT)
                }
            )
        }
        composable(Routes.OCR) {
            OcrScreen(
                onBack = { navController.popBackStack() },
                onUseText = { text ->
                    // 回填到翻译页：写进上一个页面的 savedStateHandle 再返回，
                    // 这样旋转屏幕或进程重建都不会丢。
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.KEY_OCR_RESULT, text)
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
