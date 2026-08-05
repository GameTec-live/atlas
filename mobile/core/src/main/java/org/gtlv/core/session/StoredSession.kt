package org.gtlv.core.session

import okhttp3.Cookie

data class StoredSession(
    val token: String,
    val cookies: List<Cookie>
)