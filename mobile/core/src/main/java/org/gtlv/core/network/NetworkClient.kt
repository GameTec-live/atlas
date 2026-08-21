package org.gtlv.core.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.gtlv.core.network.cookie.MemoryCookieJar

class NetworkClient(
    val cookieJar:
    MemoryCookieJar =
        MemoryCookieJar()
) {
    val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                15,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                15,
                TimeUnit.SECONDS
            )
            .pingInterval(
                20,
                TimeUnit.SECONDS
            )
            .build()
}