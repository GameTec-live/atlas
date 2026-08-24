package org.gtlv.atlas.map

import androidx.core.graphics.toColorInt
import org.gtlv.core.geoservice.RoutePoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal fun Style.addRouteLayer() {
    if (getSource(ROUTE_SOURCE_ID) == null) {
        addSource(
            GeoJsonSource(
                ROUTE_SOURCE_ID,
                emptyRouteFeatureCollection()
            )
        )
    }

    if (getLayer(ROUTE_LAYER_ID) != null) {
        return
    }

    val routeLayer = LineLayer(
        ROUTE_LAYER_ID,
        ROUTE_SOURCE_ID
    ).withProperties(
        PropertyFactory.lineColor("#2563EB".toColorInt()),
        PropertyFactory.lineWidth(6f),
        PropertyFactory.lineOpacity(0.92f),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
    )

    val firstLabelLayerId = layers
        .firstOrNull { it is SymbolLayer }
        ?.id

    if (firstLabelLayerId != null) {
        addLayerBelow(routeLayer, firstLabelLayerId)
    } else {
        addLayer(routeLayer)
    }
}

internal fun Style.updateRoute(
    routePoints: List<RoutePoint>
) {
    val featureCollection = if (routePoints.size >= 2) {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    routePoints.map {
                        Point.fromLngLat(
                            it.longitude,
                            it.latitude
                        )
                    }
                )
            )
        )
    } else {
        emptyRouteFeatureCollection()
    }

    getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
        ?.setGeoJson(featureCollection)
}

internal fun canAnimateRouteTransition(
    previous: List<RoutePoint>,
    target: List<RoutePoint>
): Boolean {
    if (
        previous.size < 2 ||
        target.size < 2 ||
        previous.first() == target.first()
    ) {
        return false
    }

    return previous.indexOf(target[1]) >= 1
}

internal fun interpolateRemainingRoute(
    previous: List<RoutePoint>,
    target: List<RoutePoint>,
    fraction: Double
): List<RoutePoint> {
    val progress = fraction.coerceIn(0.0, 1.0)
    if (progress >= 1.0) {
        return target
    }

    val anchorIndex = target.getOrNull(1)?.let(previous::indexOf)
        ?: return target
    if (anchorIndex < 1) {
        return target
    }

    val transitionPath = buildList {
        addAll(previous.take(anchorIndex))
        add(target.first())
    }.removeAdjacentDuplicates()
    if (transitionPath.size < 2) {
        return target
    }

    val segmentLengths = transitionPath
        .zipWithNext(::displayDistance)
    val totalDistance = segmentLengths.sum()
    if (totalDistance <= 0.0) {
        return target
    }

    val targetDistance = totalDistance * progress
    var completedDistance = 0.0
    var segmentIndex = 0
    var segmentFraction = 0.0

    for (index in segmentLengths.indices) {
        val segmentLength = segmentLengths[index]
        if (
            completedDistance + segmentLength >= targetDistance ||
            index == segmentLengths.lastIndex
        ) {
            segmentIndex = index
            segmentFraction = if (segmentLength > 0.0) {
                (targetDistance - completedDistance) /
                        segmentLength
            } else {
                1.0
            }.coerceIn(0.0, 1.0)
            break
        }
        completedDistance += segmentLength
    }

    val start = transitionPath[segmentIndex]
    val end = transitionPath[segmentIndex + 1]
    val animatedHead = RoutePoint(
        latitude = start.latitude +
                (end.latitude - start.latitude) * segmentFraction,
        longitude = start.longitude +
                (end.longitude - start.longitude) * segmentFraction
    )

    return buildList {
        add(animatedHead)
        addAll(transitionPath.drop(segmentIndex + 1))
        addAll(target.drop(1))
    }.removeAdjacentDuplicates()
}

private fun List<RoutePoint>.removeAdjacentDuplicates():
        List<RoutePoint> = filterIndexed { index, point ->
    index == 0 || point != this[index - 1]
}

private fun displayDistance(
    first: RoutePoint,
    second: RoutePoint
): Double {
    val latitudeScale = kotlin.math.cos(
        Math.toRadians(
            (first.latitude + second.latitude) / 2.0
        )
    )
    val longitudeDelta =
        (second.longitude - first.longitude) * latitudeScale
    val latitudeDelta = second.latitude - first.latitude
    return kotlin.math.sqrt(
        longitudeDelta * longitudeDelta +
                latitudeDelta * latitudeDelta
    )
}

private fun emptyRouteFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(emptyArray<Feature>())

private const val ROUTE_SOURCE_ID =
    "atlas-navigation-route-source"
private const val ROUTE_LAYER_ID =
    "atlas-navigation-route-layer"
