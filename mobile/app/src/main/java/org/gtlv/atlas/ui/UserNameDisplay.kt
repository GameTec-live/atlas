package org.gtlv.atlas.ui

internal const val MAX_DISPLAYED_USER_NAME_LENGTH = 24
internal const val MAX_PROFILE_USER_NAME_LENGTH = 32

internal fun String.truncatedUserName(
    maxLength: Int = MAX_DISPLAYED_USER_NAME_LENGTH
): String {
    require(maxLength > 1)

    val normalizedName = trim()
    val codePointCount = normalizedName.codePointCount(
        0,
        normalizedName.length
    )

    if (codePointCount <= maxLength) {
        return normalizedName
    }

    val visibleNameEnd = normalizedName.offsetByCodePoints(
        0,
        maxLength - 1
    )

    return normalizedName
        .substring(0, visibleNameEnd)
        .trimEnd() + "…"
}
