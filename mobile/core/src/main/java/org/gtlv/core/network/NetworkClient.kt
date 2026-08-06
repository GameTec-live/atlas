package org.gtlv.core.network

import okhttp3.OkHttpClient
import org.gtlv.core.network.cookie.MemoryCookieJar
import java.util.concurrent.TimeUnit

class NetworkClient(
    val cookieJar: MemoryCookieJar = MemoryCookieJar()
) {
    val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
}