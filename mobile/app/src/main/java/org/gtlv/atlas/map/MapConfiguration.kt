package org.gtlv.atlas.map

internal object MapConfiguration {

    /*
     * Temporary development address.
     * Replace only the base URL when the reverse proxy is ready.
     */
    private const val MAP_SERVER_BASE_URL =
        "http://192.168.1.200:1026"

    const val STYLE_URL =
        "$MAP_SERVER_BASE_URL/style/liberty"

    const val INITIAL_LATITUDE = 48.500
    const val INITIAL_LONGITUDE = 14.580
    const val INITIAL_ZOOM = 13.0

    const val USER_LOCATION_ZOOM = 18.0
}