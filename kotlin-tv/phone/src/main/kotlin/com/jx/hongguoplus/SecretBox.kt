// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 账号凭证加密：密钥放在 Android Keystore（不可导出），密文 AES/GCM 存在 SharedPreferences。
 * 旧版本明文会被自动识别、读出后立即改写成密文，用户不需要重新登录。
 */
object SecretBox {
    private const val PREFIX = "v1:"
    private const val ALIAS = "hongguoplus_account_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(body, Base64.NO_WRAP)
    }.getOrDefault(plain)

    fun decrypt(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return stored // 旧版本明文，直接返回，调用方会重新写入密文
        return runCatching {
            val payload = stored.removePrefix(PREFIX)
            val ivText = payload.substringBefore(":")
            val bodyText = payload.substringAfter(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            }
            String(cipher.doFinal(Base64.decode(bodyText, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun isEncrypted(stored: String?): Boolean = stored?.startsWith(PREFIX) == true
}
