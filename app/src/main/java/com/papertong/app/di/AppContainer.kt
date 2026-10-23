package com.papertong.app.di

import android.content.Context
import com.papertong.app.core.crypto.ApiKeyValidator
import com.papertong.app.core.crypto.KeystoreSecretStore
import com.papertong.app.core.crypto.SecretStore
import com.papertong.app.core.data.appDataStore
import com.papertong.app.core.settings.DataStoreSettingsRepository
import com.papertong.app.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手写依赖容器，生命周期与 Application 一致。
 * 只有 4 个界面，引入 Hilt 的编译开销不划算，构造注入足够。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val dataStore = appContext.appDataStore

    val secretStore: SecretStore = KeystoreSecretStore(dataStore)

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(dataStore)

    val apiKeyValidator: ApiKeyValidator = ApiKeyValidator()

    /** 用于不绑定界面生命周期的任务（例如后续的后台解析）。 */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
