package org.gtlv.core.map

import android.graphics.Color
import com.google.gson.JsonObject
import org.gtlv.core.telemetry.LiveMapUser
import org.gtlv.core.telemetry.TelemetryVehicleState
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Adds the shared live-driver markers to a MapLibre style. */
fun Style.addLiveMapUserLayers() {
    liveMapMarkerStyles.forEach { markerStyle ->
        addSource(
            GeoJsonSource(
                markerStyle.sourceId,
                emptyLiveMapFeatureCollection(),
            ),
        )

        addLayer(
            CircleLayer(
                markerStyle.layerId,
                markerStyle.sourceId,
            ).withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(
                    markerStyle.state.liveMapMarkerColor,
                ),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleOpacity(1f),
            ),
        )
    }

    addSource(
        GeoJsonSource(
            LIVE_MAP_LABEL_SOURCE_ID,
            emptyLiveMapFeatureCollection(),
        ),
    )

    addLayer(
        SymbolLayer(
            LIVE_MAP_LABEL_LAYER_ID,
            LIVE_MAP_LABEL_SOURCE_ID,
        ).withProperties(
            PropertyFactory.textField(
                Expression.toString(
                    Expression.get(LIVE_MAP_USER_NAME_PROPERTY),
                ),
            ),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor(Color.BLACK),
            PropertyFactory.textHaloColor(Color.WHITE),
            PropertyFactory.textHaloWidth(2f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textOptional(true),
        ),
    )
}

/** Replaces every marker source with the latest connected-driver snapshot. */
fun Style.updateLiveMapUsers(users: Collection<LiveMapUser>) {
    liveMapMarkerStyles.forEach { markerStyle ->
        updateLiveMapSource(
            sourceId = markerStyle.sourceId,
            users = users.filter { user -> user.state == markerStyle.state },
        )
    }

    updateLiveMapSource(
        sourceId = LIVE_MAP_LABEL_SOURCE_ID,
        users = users,
    )
}

private fun Style.updateLiveMapSource(
    sourceId: String,
    users: Collection<LiveMapUser>,
) {
    getSourceAs<GeoJsonSource>(sourceId)
        ?.setGeoJson(users.toLiveMapFeatureCollection())
}

private fun Collection<LiveMapUser>.toLiveMapFeatureCollection():
    FeatureCollection {
    val features = map { user ->
        val properties = JsonObject().apply {
            addProperty(
                LIVE_MAP_USER_NAME_PROPERTY,
                user.userName.truncatedLiveMapUserName(),
            )
        }

        Feature.fromGeometry(
            Point.fromLngLat(user.longitude, user.latitude),
            properties,
        )
    }

    return FeatureCollection.fromFeatures(features.toTypedArray())
}

private fun String.truncatedLiveMapUserName(): String {
    val normalizedName = trim()
    val codePointCount = normalizedName.codePointCount(0, normalizedName.length)
    if (codePointCount <= MAX_LIVE_MAP_USER_NAME_LENGTH) return normalizedName

    val visibleNameEnd = normalizedName.offsetByCodePoints(
        0,
        MAX_LIVE_MAP_USER_NAME_LENGTH - 1,
    )
    return normalizedName.substring(0, visibleNameEnd).trimEnd() + "\u2026"
}

private fun emptyLiveMapFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(emptyArray<Feature>())

private data class LiveMapMarkerStyle(
    val state: TelemetryVehicleState,
    val sourceId: String,
    val layerId: String,
)

private val liveMapMarkerStyles = listOf(
    LiveMapMarkerStyle(
        state = TelemetryVehicleState.FREE,
        sourceId = "atlas-live-users-free-source",
        layerId = "atlas-live-users-free-layer",
    ),
    LiveMapMarkerStyle(
        state = TelemetryVehicleState.ON_THE_WAY,
        sourceId = "atlas-live-users-on-the-way-source",
        layerId = "atlas-live-users-on-the-way-layer",
    ),
    LiveMapMarkerStyle(
        state = TelemetryVehicleState.OCCUPIED,
        sourceId = "atlas-live-users-occupied-source",
        layerId = "atlas-live-users-occupied-layer",
    ),
    LiveMapMarkerStyle(
        state = TelemetryVehicleState.AWAY,
        sourceId = "atlas-live-users-away-source",
        layerId = "atlas-live-users-away-layer",
    ),
)

private const val LIVE_MAP_LABEL_SOURCE_ID =
    "atlas-live-users-label-source"
private const val LIVE_MAP_LABEL_LAYER_ID =
    "atlas-live-users-label-layer"
private const val LIVE_MAP_USER_NAME_PROPERTY = "userName"
private const val MAX_LIVE_MAP_USER_NAME_LENGTH = 24
