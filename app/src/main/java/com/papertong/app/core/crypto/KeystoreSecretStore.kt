package com.papertong.app.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 基于 Android Keystore 的 AES-256-GCM 实现。
 *
 * 密钥由 Keystore 生成并保管且**不可导出**；DataStore 里只存
 * "base64(iv):base64(ciphertext)"。即使拿到应用存储目录也无法还原明文。
 *
 * 不用 EncryptedSharedPreferences 的原因：androidx.security:security-crypto
 * 已进入维护模式，且社区多次报告部分机型上因 Keystore 异常导致启动崩溃。
 * 直接用平台 API，行为更可控，也少一个依赖。
 */
class KeystoreSecretStore(
    private val dataStore: DataStore<Preferences>
) : SecretStore {

    override val hasSecret: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_CIPHERTEXT] != null }

    override suspend fun saveApiKey(raw: String) {
        val normalized = raw.trim()
        require(normalized.isNotEmpty()) { "API Key 不能为空" }
        val payload = encrypt(normalized)
        dataStore.edit { prefs -> prefs[KEY_CIPHERTEXT] = payload }
    }

    override suspend fun readApiKey(): String? {
        // 读操作用 data.first()，不能用 edit —— 那是写入路径，
        // 每次读 Key 都触发一次磁盘提交，纯属浪费。
        val stored = dataStore.data.first()[KEY_CIPHERTEXT] ?: return null
        return try {
            decrypt(stored)
        } catch (e: Exception) {
            // 典型场景：用户清除应用数据但系统保留了 Keystore 别名，
            // 或换机还原后密钥对不上。此时旧密文已无法解开，丢弃。
            Log.w(TAG, "解密失败，清除密文：${e.javaClass.simpleName}")
            clear()
            null
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_CIPHERTEXT) }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(iv) + SEPARATOR + b64(ciphertext)
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(SEPARATOR)
        require(parts.size == 2) { "密文格式不正确" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /**
     * 取得（必要时创建）Keystore 密钥。
     *
     * setUserAuthenticationRequired(false) 是有意的：翻译是后台异步任务，
     * 若绑定生物识别，每次调用都要弹验证，体验不可接受。
     */
    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "KeystoreSecretStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "papertong_api_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val SEPARATOR = ":"
        val KEY_CIPHERTEXT = stringPreferencesKey("api_key_ciphertext")
    }
}
