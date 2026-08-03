package org.gtlv.core.network.cookie

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class MemoryCookieJar : CookieJar {

    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>
    ) {
        cookies.forEach { newCookie ->
            this.cookies.removeAll { existing ->
                existing.name == newCookie.name &&
                        existing.domain == newCookie.domain &&
                        existing.path == newCookie.path
            }

            if (newCookie.expiresAt > System.currentTimeMillis()) {
                this.cookies += newCookie
            }
        }
    }

    @Synchronized
    override fun loadForRequest(
        url: HttpUrl
    ): List<Cookie> {
        val currentTime = System.currentTimeMillis()

        cookies.removeAll { cookie ->
            cookie.expiresAt <= currentTime
        }

        return cookies.filter { cookie ->
            cookie.matches(url)
        }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
    }

    @Synchronized
    fun snapshot(): List<Cookie> {
        val now = System.currentTimeMillis()

        cookies.removeAll {
            it.expiresAt <= now
        }

        return cookies.toList()
    }

    @Synchronized
    fun restore(restoredCookies: List<Cookie>) {
        cookies.clear()

        val now = System.currentTimeMillis()

        cookies += restoredCookies.filter {
            it.expiresAt > now
        }
    }
}