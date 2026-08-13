package org.gtlv.core.session

import okhttp3.Cookie

data class StoredSession(
    val cookies: List<Cookie>
)