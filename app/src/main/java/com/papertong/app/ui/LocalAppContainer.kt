package com.papertong.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.papertong.app.di.AppContainer

/**
 * 把依赖容器下发给 Compose 树。
 * 使用 staticCompositionLocalOf：容器在进程生命周期内不变，不需要订阅变化。
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 未提供，请检查 MainActivity 是否包裹了 CompositionLocalProvider")
}
