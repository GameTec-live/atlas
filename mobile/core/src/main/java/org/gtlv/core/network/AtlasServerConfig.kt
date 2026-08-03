package org.gtlv.core.network

object AtlasServerConfig {
    const val HTTP_BASE_URL =
        "http://192.168.1.200:1030"

    const val LOGIN_URL =
        "$HTTP_BASE_URL/api/auth/sign-in/email"

    const val NOTIFICATION_SOCKET_URL =
        "ws://192.168.1.200:1030/realtime/notify"
}