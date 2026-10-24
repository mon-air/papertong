package com.papertong.app.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的 AES-256-GCM 二进制加解密。
 *
 * 与 [KeystoreSecretStore] 的区别：那边把 iv 与密文各自 base64 后存进
 * DataStore 的字符串；这里直接返回字节数组，供文件存储使用（无需 base64 膨胀）。
 * 两者的密文格式不同，因此**不能混用**——各自绑定自己的 key alias。
 *
 * 不提供密钥导出：Keystore 生成的密钥不可导出，这是本方案的全部价值。
 * 代价是密钥一旦丢失（清除应用数据、换机、系统恢复出厂），
 * 用它加密的数据就**永久无法恢复**。调用方必须接受这一点。
 */
class KeystoreCipher(private val keyAlias: String) {

    /**
     * 输出格式：`ivLen(1 字节) || iv || 密文`。
     *
     * 自带长度前缀而不是固定长度：GCM 的 iv 长度按规范是 12 字节，
     * 但写死会让将来换算法时无从兼容，多存 1 个字节换一个明确的边界。
     */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val iv = cipher.iv
        require(iv.size in 1..MAX_IV_LEN) { "iv 长度异常：${iv.size}" }
        val ciphertext = cipher.doFinal(plain)

        val out = ByteArray(1 + iv.size + ciphertext.size)
        out[0] = iv.size.toByte()
        iv.copyInto(out, 1)
        ciphertext.copyInto(out, 1 + iv.size)
        return out
    }

    /**
     * 解密失败时抛异常而不是返回 null：调用方需要区分
     * "格式不对"与"密钥不匹配"，两者的处理策略不同（前者可尝试旧格式兜底）。
     */
    fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size > 1) { "密文过短" }
        val ivLen = payload[0].toInt() and 0xFF
        require(ivLen in 1..MAX_IV_LEN && payload.size > 1 + ivLen) { "密文格式不正确" }

        val iv = payload.copyOfRange(1, 1 + ivLen)
        val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // 翻译与历史读写都在后台，绑定生物识别会让每次操作都弹验证
            .setUserAuthenticationRequired(false)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val MAX_IV_LEN = 16
    }
}
