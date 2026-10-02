package com.mqttmobile.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores broker passwords with an Android Keystore backed AES-GCM key. */
class CredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun put(profileId: String, password: String) {
        if (password.isEmpty()) {
            delete(profileId)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
        preferences.edit().putString(profileId, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun get(profileId: String): String? {
        val value = preferences.getString(profileId, null) ?: return null
        return runCatching {
            val allBytes = Base64.decode(value, Base64.NO_WRAP)
            val iv = allBytes.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = allBytes.copyOfRange(GCM_IV_LENGTH, allBytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun delete(profileId: String) {
        preferences.edit().remove(profileId).apply()
    }

    fun putPrivateKey(profileId: String, privateKey: String) {
        if (privateKey.isBlank()) return
        putEncrypted(PRIVATE_KEY_PREFIX + profileId, privateKey)
    }

    fun getPrivateKey(profileId: String): String? = getEncrypted(PRIVATE_KEY_PREFIX + profileId)

    fun hasPrivateKey(profileId: String): Boolean = preferences.contains(PRIVATE_KEY_PREFIX + profileId)

    fun deletePrivateKey(profileId: String) {
        preferences.edit().remove(PRIVATE_KEY_PREFIX + profileId).apply()
    }

    private fun putEncrypted(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        preferences.edit().putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun getEncrypted(key: String): String? {
        val value = preferences.getString(key, null) ?: return null
        return runCatching {
            val allBytes = Base64.decode(value, Base64.NO_WRAP)
            val iv = allBytes.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = allBytes.copyOfRange(GCM_IV_LENGTH, allBytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "mqtt_credentials"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mqtt-mobile-credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val PRIVATE_KEY_PREFIX = "private_key_"
    }
}
