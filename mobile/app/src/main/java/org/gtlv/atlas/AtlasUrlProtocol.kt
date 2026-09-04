package org.gtlv.atlas

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Parses server addresses carried by the atlas:// URL protocol. */
internal object AtlasUrlProtocol {

    private const val SCHEME_PREFIX = "atlas:"

    fun serverAddressFrom(url: String?): String? {
        val rawUrl = url?.trim() ?: return null

        if (!rawUrl.startsWith(SCHEME_PREFIX, ignoreCase = true)) {
            return null
        }

        val encodedAddress = rawUrl
            .substring(SCHEME_PREFIX.length)
            .removePrefix("//")

        if (encodedAddress.isBlank()) {
            return null
        }

        val decodedAddress = runCatching {
            // URLDecoder treats '+' as a space. Preserve it because '+' is a
            // valid literal character in an HTTP URL.
            URLDecoder.decode(
                encodedAddress.replace("+", "%2B"),
                StandardCharsets.UTF_8.name()
            )
        }.getOrNull()?.trim() ?: return null

        val addressWithScheme =
            if (
                decodedAddress.startsWith("http://", ignoreCase = true) ||
                decodedAddress.startsWith("https://", ignoreCase = true)
            ) {
                decodedAddress
            } else {
                "https://$decodedAddress"
            }

        val parsedAddress =
            addressWithScheme.toHttpUrlOrNull()
                ?: return null

        if (parsedAddress.scheme !in setOf("http", "https")) {
            return null
        }

        return parsedAddress.toString().removeSuffix("/")
    }
}
