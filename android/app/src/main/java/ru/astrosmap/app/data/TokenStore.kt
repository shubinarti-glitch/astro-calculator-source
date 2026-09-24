package ru.astrosmap.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.authDataStore by preferencesDataStore(name = "auth")

/**
 * Хранилище Bearer-токена: DataStore + шифрование AES-GCM ключом из Android Keystore
 * (androidx security-crypto устарела — шифруем сами, это ~30 строк).
 */
class TokenStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("token")

    @Volatile
    private var cached: String? = null
    private var loaded = false

    suspend fun get(): String? {
        if (!loaded) {
            cached = context.authDataStore.data.first()[tokenKey]?.let(::decrypt)
            loaded = true
        }
        return cached
    }

    suspend fun save(token: String) {
        cached = token
        loaded = true
        context.authDataStore.edit { it[tokenKey] = encrypt(token) }
    }

    suspend fun clear() {
        cached = null
        loaded = true
        context.authDataStore.edit { it.remove(tokenKey) }
    }

    // ------------------------------------------------------------------ #

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_LEN))
        String(cipher.doFinal(bytes, IV_LEN, bytes.size - IV_LEN))
    }.getOrNull() // ключ Keystore мог «протухнуть» (сброс устройства) — просто разлогин

    private companion object {
        const val ALIAS = "astro_token_key"
        const val IV_LEN = 12
    }
}
