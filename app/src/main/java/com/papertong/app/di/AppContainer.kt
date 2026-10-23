package com.papertong.app.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手写依赖容器。应用只有 4 个界面，引入 Hilt/Dagger 带来的
 * 编译期开销与配置复杂度不划算，构造注入足够。
 *
 * 生命周期与 Application 一致；后续里程碑会在这里挂载
 * SecretStore / SettingsRepository / TranslationRepository。
 */
class AppContainer(context: Context) {

    /** 后续里程碑的 Repository 都从这里取 Context。 */
    internal val appContext: Context = context.applicationContext

    /** 用于不绑定任何界面生命周期的长任务（如后台解析）。 */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
