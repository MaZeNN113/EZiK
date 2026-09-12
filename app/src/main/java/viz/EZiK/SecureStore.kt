package viz.EZiK

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small encrypted local store for the user's personal API key. */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("ezik_secure", Context.MODE_PRIVATE)
    private val alias = "ezik_local_key_v1"

    fun putApiKey(value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(KEY).apply()
            return
        }
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(value.trim().toByteArray(StandardCharsets.UTF_8))
        val packed = cipher.iv + encrypted
        prefs.edit().putString(KEY, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun getApiKey(): String? {
        val encoded = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > GCM_IV_LENGTH_BYTES)
            val iv = packed.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val encrypted = packed.copyOfRange(GCM_IV_LENGTH_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    private fun getOrCreateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        return runCatching {
            val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.getKey(alias, null) as? SecretKey
        }.getOrNull() ?: generator.apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    companion object {
        private const val KEY = "groq_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
