package org.gtlv.atlas.map

internal object MapConfiguration {

    private const val STYLE_PATH =
        "map/style/liberty"

    fun createStyleUrl(
        serverAddress: String
    ): String {
        return buildString {
            append(serverAddress.trimEnd('/'))
            append('/')
            append(STYLE_PATH)
        }
    }

    const val INITIAL_LATITUDE = 48.500
    const val INITIAL_LONGITUDE = 14.580
    const val INITIAL_ZOOM = 13.0

    const val USER_LOCATION_ZOOM = 18.0
}