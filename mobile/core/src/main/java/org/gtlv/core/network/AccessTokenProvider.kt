package org.gtlv.core.network

interface AccessTokenProvider {
    fun currentAccessToken(): String?
}