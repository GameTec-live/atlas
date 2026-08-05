package org.gtlv.core.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import okhttp3.Cookie
import org.json.JSONArray
import org.json.JSONObject

private val Context.secureSessionDataStore by preferencesDataStore(
    name = "atlas_secure_session"
)

class SecureSessionStore(
    context: Context,
    private val cipher: KeystoreCipher = KeystoreCipher()
) {
    private val dataStore =
        context.applicationContext.secureSessionDataStore

    suspend fun save(
        token: String,
        cookies: List<Cookie>
    ) {
        val plainJson = encodeSession(
            token = token,
            cookies = cookies
        )

        val encryptedSession = cipher.encrypt(plainJson)

        dataStore.edit { preferences ->
            preferences[ENCRYPTED_SESSION] = encryptedSession
        }
    }

    suspend fun restore(): StoredSession? {
        val encryptedSession = dataStore
            .data
            .first()[ENCRYPTED_SESSION]
            ?: return null

        return try {
            val plainJson = cipher.decrypt(encryptedSession)
            decodeSession(plainJson)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(ENCRYPTED_SESSION)
        }
    }

    private fun encodeSession(
        token: String,
        cookies: List<Cookie>
    ): String {
        val cookieArray = JSONArray()

        cookies.forEach { cookie ->
            cookieArray.put(
                JSONObject()
                    .put("name", cookie.name)
                    .put("value", cookie.value)
                    .put("domain", cookie.domain)
                    .put("path", cookie.path)
                    .put("expiresAt", cookie.expiresAt)
                    .put("secure", cookie.secure)
                    .put("httpOnly", cookie.httpOnly)
                    .put("hostOnly", cookie.hostOnly)
            )
        }

        return JSONObject()
            .put("token", token)
            .put("cookies", cookieArray)
            .toString()
    }

    private fun decodeSession(
        plainJson: String
    ): StoredSession {
        val root = JSONObject(plainJson)
        val token = root.getString("token")
        val cookieArray = root.getJSONArray("cookies")
        val cookies = mutableListOf<Cookie>()

        for (index in 0 until cookieArray.length()) {
            val json = cookieArray.getJSONObject(index)
            val domain = json.getString("domain")

            val builder = Cookie.Builder()
                .name(json.getString("name"))
                .value(json.getString("value"))
                .path(json.getString("path"))
                .expiresAt(json.getLong("expiresAt"))

            if (json.getBoolean("hostOnly")) {
                builder.hostOnlyDomain(domain)
            } else {
                builder.domain(domain)
            }

            if (json.getBoolean("secure")) {
                builder.secure()
            }

            if (json.getBoolean("httpOnly")) {
                builder.httpOnly()
            }

            cookies += builder.build()
        }

        return StoredSession(
            token = token,
            cookies = cookies
        )
    }


    private companion object {
        val ENCRYPTED_SESSION =
            stringPreferencesKey("encrypted_session")
    }
}