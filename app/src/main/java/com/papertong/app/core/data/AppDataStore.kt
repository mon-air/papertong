package com.papertong.app.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 全进程唯一的 DataStore 实例。
 *
 * 同一个文件只能创建一个 DataStore，重复创建会在运行时抛异常，
 * 因此用扩展属性 + 顶层委托保证单例。
 */
private const val PREFERENCES_NAME = "papertong_settings"

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_NAME
)
