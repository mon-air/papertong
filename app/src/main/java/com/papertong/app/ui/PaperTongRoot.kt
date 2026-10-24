package com.papertong.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.papertong.app.ui.nav.Routes
import com.papertong.app.ui.screens.about.AboutScreen
import com.papertong.app.ui.screens.file.FileImportScreen
import com.papertong.app.ui.screens.history.HistoryScreen
import com.papertong.app.ui.screens.ocr.OcrScreen
import com.papertong.app.ui.screens.reading.ReadingScreen
import com.papertong.app.ui.screens.settings.SettingsScreen
import com.papertong.app.ui.screens.translate.TranslateScreen

@Composable
fun PaperTongRoot(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    /**
     * OCR 页与文件导入页共用的回填逻辑：
     * 把结果写进翻译页的 savedStateHandle 再返回，
     * 这样旋转屏幕或进程重建都不会丢。
     */
    fun returnTextToTranslate(text: String) {
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(Routes.KEY_IMPORTED_TEXT, text)
        navController.popBackStack()
    }

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
                onOpenFileImport = { navController.navigate(Routes.FILE_IMPORT) },
                incomingText = entry.savedStateHandle.get<String>(Routes.KEY_IMPORTED_TEXT),
                onIncomingTextConsumed = {
                    entry.savedStateHandle.remove<String>(Routes.KEY_IMPORTED_TEXT)
                }
            )
        }
        composable(Routes.OCR) {
            OcrScreen(
                onBack = { navController.popBackStack() },
                onUseText = ::returnTextToTranslate
            )
        }
        composable(Routes.FILE_IMPORT) {
            FileImportScreen(
                onBack = { navController.popBackStack() },
                onUseText = ::returnTextToTranslate
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenReading = { entryId ->
                    navController.navigate(Routes.reading(entryId))
                }
            )
        }
        composable(Routes.READING_PATTERN) { backStackEntry ->
            ReadingScreen(
                entryId = backStackEntry.arguments?.getString(Routes.ARG_ENTRY_ID).orEmpty(),
                onBack = { navController.popBackStack() }
            )
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
