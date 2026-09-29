package com.ginsengo.steward.research

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's model API keys, encrypted with an AES-GCM key that lives in the Android
 * Keystore and never leaves it.
 *
 * Replaces `BuildConfig.GEMINI_API_KEY`, which compiled the BUILD MACHINE's key into every
 * APK: anyone given the APK could unzip it and read the key. Bring-your-own-key at runtime
 * means each user's calls are billed to, and limited by, their own key.
 *
 * androidx.security-crypto was the obvious library and was rejected: it is deprecated, and
 * this is twenty lines of platform API with no dependency.
 */
open class KeyVault(context: Context) {

    private val prefs = context.getSharedPreferences("gensingo_keys", Context.MODE_PRIVATE)

    open fun get(provider: Provider): String? {
        val blob = prefs.getString(provider.name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, IV_LEN))
            String(cipher.doFinal(bytes, IV_LEN, bytes.size - IV_LEN), Charsets.UTF_8)
        }.getOrNull()
    }

    open fun has(provider: Provider): Boolean = prefs.contains(provider.name)

    fun set(provider: Provider, key: String?) {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(provider.name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val out = cipher.iv + cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(provider.name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "gensingo.model-keys"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
