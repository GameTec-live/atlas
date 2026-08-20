package org.gtlv.atlas.map

import android.graphics.Color
import androidx.core.graphics.toColorInt
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

internal fun Style.addLiveMapUserLayers() {
    markerStyles.forEach { markerStyle ->
        addSource(
            GeoJsonSource(
                markerStyle.sourceId,
                emptyFeatureCollection()
            )
        )

        addLayer(
            CircleLayer(
                markerStyle.layerId,
                markerStyle.sourceId
            ).withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(
                    markerStyle.color
                ),
                PropertyFactory.circleStrokeColor(
                    Color.WHITE
                ),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleOpacity(1f)
            )
        )
    }

    addSource(
        GeoJsonSource(
            LABEL_SOURCE_ID,
            emptyFeatureCollection()
        )
    )

    addLayer(
        SymbolLayer(
            LABEL_LAYER_ID,
            LABEL_SOURCE_ID
        ).withProperties(
            PropertyFactory.textField(
                Expression.toString(
                    Expression.get(USER_NAME_PROPERTY)
                )
            ),
            PropertyFactory.textFont(
                arrayOf("Noto Sans Regular")
            ),
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor(Color.BLACK),
            PropertyFactory.textHaloColor(Color.WHITE),
            PropertyFactory.textHaloWidth(2f),
            PropertyFactory.textAnchor(
                Property.TEXT_ANCHOR_TOP
            ),
            PropertyFactory.textOffset(
                arrayOf(0f, 1.1f)
            ),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textOptional(true)
        )
    )
}

internal fun Style.updateLiveMapUsers(
    users: Collection<LiveMapUser>
) {
    markerStyles.forEach { markerStyle ->
        updateSource(
            sourceId = markerStyle.sourceId,
            users = users.filter {
                it.state == markerStyle.state
            }
        )
    }

    updateSource(
        sourceId = LABEL_SOURCE_ID,
        users = users
    )
}

private fun Style.updateSource(
    sourceId: String,
    users: Collection<LiveMapUser>
) {
    getSourceAs<GeoJsonSource>(sourceId)
        ?.setGeoJson(users.toFeatureCollection())
}

private fun Collection<LiveMapUser>
        .toFeatureCollection(): FeatureCollection {
    val features = map { user ->
        val properties = JsonObject().apply {
            addProperty(
                USER_NAME_PROPERTY,
                user.userName
            )
        }

        Feature.fromGeometry(
            Point.fromLngLat(
                user.longitude,
                user.latitude
            ),
            properties
        )
    }

    return FeatureCollection.fromFeatures(
        features.toTypedArray()
    )
}

private fun emptyFeatureCollection(): FeatureCollection {
    return FeatureCollection.fromFeatures(
        emptyArray<Feature>()
    )
}

private data class MarkerStyle(
    val state: TelemetryVehicleState,
    val sourceId: String,
    val layerId: String,
    val color: Int
)

private val markerStyles = listOf(
    MarkerStyle(
        state = TelemetryVehicleState.FREE,
        sourceId = "atlas-live-users-free-source",
        layerId = "atlas-live-users-free-layer",
        color = "#10B981".toColorInt()
    ),
    MarkerStyle(
        state = TelemetryVehicleState.ON_THE_WAY,
        sourceId = "atlas-live-users-on-the-way-source",
        layerId = "atlas-live-users-on-the-way-layer",
        color = "#3B82F6".toColorInt()
    ),
    MarkerStyle(
        state = TelemetryVehicleState.OCCUPIED,
        sourceId = "atlas-live-users-occupied-source",
        layerId = "atlas-live-users-occupied-layer",
        color = "#F59E0B".toColorInt()
    ),
    MarkerStyle(
        state = TelemetryVehicleState.AWAY,
        sourceId = "atlas-live-users-away-source",
        layerId = "atlas-live-users-away-layer",
        color = "#94A3B8".toColorInt()
    )
)

private const val LABEL_SOURCE_ID =
    "atlas-live-users-label-source"

private const val LABEL_LAYER_ID =
    "atlas-live-users-label-layer"

private const val USER_NAME_PROPERTY = "userName"