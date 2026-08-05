package org.gtlv.core.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreCipher {

    private val keyStore = KeyStore
        .getInstance(ANDROID_KEYSTORE)
        .apply {
            load(null)
        }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateSecretKey()
        )

        val encryptedBytes = cipher.doFinal(
            plainText.toByteArray(Charsets.UTF_8)
        )

        val encodedIv = Base64.encodeToString(
            cipher.iv,
            Base64.NO_WRAP
        )

        val encodedData = Base64.encodeToString(
            encryptedBytes,
            Base64.NO_WRAP
        )

        return "$encodedIv:$encodedData"
    }

    fun decrypt(encryptedText: String): String {
        val parts = encryptedText.split(":", limit = 2)

        require(parts.size == 2) {
            "Invalid encrypted session format"
        }

        val iv = Base64.decode(
            parts[0],
            Base64.NO_WRAP
        )

        val encryptedBytes = Base64.decode(
            parts[1],
            Base64.NO_WRAP
        )

        val cipher = Cipher.getInstance(TRANSFORMATION)

        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )

        return cipher
            .doFinal(encryptedBytes)
            .toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val existingKey = keyStore.getKey(
            KEY_ALIAS,
            null
        ) as? SecretKey

        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(
                KeyProperties.BLOCK_MODE_GCM
            )
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setKeySize(256)
            .build()

        keyGenerator.init(specification)

        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "atlas_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}