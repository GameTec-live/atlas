package org.gtlv.core.geoservice

import org.json.JSONArray
import org.json.JSONObject

object RouteResponseParser {

    fun parse(
        responseText: String,
        httpStatusCode: Int
    ): RouteResult {
        val root = try {
            JSONObject(responseText)
        } catch (_: Exception) {
            return RouteResult.MalformedJson
        }

        if (httpStatusCode == 401) {
            return RouteResult.Unauthorized
        }

        if (httpStatusCode !in 200..299) {
            return parseHttpError(root, httpStatusCode)
        }

        if (root.has("error") || root.has("error_code")) {
            return parseRouterError(root, httpStatusCode)
        }

        return try {
            parseRoute(root)
        } catch (_: SchemaException) {
            RouteResult.InvalidResponse
        } catch (_: Exception) {
            RouteResult.InvalidResponse
        }
    }

    private fun parseHttpError(
        root: JSONObject,
        httpStatusCode: Int
    ): RouteResult {
        return if (root.has("error") || root.has("error_code")) {
            parseRouterError(root, httpStatusCode)
        } else {
            RouteResult.ServerError(
                statusCode = httpStatusCode,
                message = root.stringOrNull("message")
                    ?: root.stringOrNull("status")
            )
        }
    }

    private fun parseRouterError(
        root: JSONObject,
        httpStatusCode: Int
    ): RouteResult.RouterError = RouteResult.RouterError(
        errorCode = root.intOrNull("error_code"),
        statusCode = root.intOrNull("status_code")
            ?: httpStatusCode,
        message = root.stringOrNull("error")
            ?: root.stringOrNull("message"),
        status = root.stringOrNull("status")
    )

    private fun parseRoute(root: JSONObject): RouteResult {
        val trip = root.optJSONObject("trip")
            ?: throw SchemaException()
        val legs = trip.optJSONArray("legs")
            ?: throw SchemaException()

        if (legs.length() == 0) {
            throw SchemaException()
        }

        val units = trip.stringOrNull("units")
        val lengthFactor = if (
            units.equals("miles", ignoreCase = true)
        ) {
            1.609344
        } else {
            1.0
        }

        val routePoints = mutableListOf<RoutePoint>()
        val routeManeuvers = mutableListOf<RouteManeuver>()
        val legSummaries = mutableListOf<RouteSummary>()

        for (legIndex in 0 until legs.length()) {
            val leg = legs.optJSONObject(legIndex)
                ?: throw SchemaException()
            val encodedShape = leg.stringOrNull("shape")
                ?: throw SchemaException()
            val legPoints = Polyline6Decoder.decode(encodedShape)
                ?: throw SchemaException()

            if (legPoints.isEmpty()) {
                throw SchemaException()
            }

            val duplicateBoundary =
                routePoints.isNotEmpty() &&
                        routePoints.last() == legPoints.first()
            val shapeIndexOffset = if (duplicateBoundary) {
                routePoints.lastIndex
            } else {
                routePoints.size
            }

            routePoints += if (duplicateBoundary) {
                legPoints.drop(1)
            } else {
                legPoints
            }

            legSummaries += parseSummary(
                leg.optJSONObject("summary"),
                lengthFactor
            )

            val maneuvers = leg.optJSONArray("maneuvers")
                ?: JSONArray()
            for (maneuverIndex in 0 until maneuvers.length()) {
                val maneuverJson =
                    maneuvers.optJSONObject(maneuverIndex)
                        ?: continue
                val maneuver = parseManeuver(
                    json = maneuverJson,
                    localPointCount = legPoints.size,
                    shapeIndexOffset = shapeIndexOffset,
                    lengthFactor = lengthFactor
                ) ?: continue
                routeManeuvers += maneuver
            }
        }

        if (routePoints.size < 2) {
            throw SchemaException()
        }

        val tripSummary = parseSummary(
            trip.optJSONObject("summary"),
            lengthFactor
        )
        val summary = RouteSummary(
            timeSeconds = tripSummary.timeSeconds
                ?: legSummaries.mapNotNull { it.timeSeconds }
                    .takeIf { it.isNotEmpty() }
                    ?.sum(),
            lengthKilometers = tripSummary.lengthKilometers
                ?: legSummaries.mapNotNull {
                    it.lengthKilometers
                }.takeIf { it.isNotEmpty() }
                    ?.sum()
        )

        return RouteResult.Success(
            route = Route(
                points = routePoints.toList(),
                maneuvers = routeManeuvers.toList(),
                summary = summary,
                units = units,
                language = trip.stringOrNull("language")
            )
        )
    }

    private fun parseManeuver(
        json: JSONObject,
        localPointCount: Int,
        shapeIndexOffset: Int,
        lengthFactor: Double
    ): RouteManeuver? {
        val verbalInstruction = json.stringOrNull(
            "verbal_pre_transition_instruction"
        )
        val instruction = json.stringOrNull("instruction")
            ?: verbalInstruction
            ?: return null

        val localBegin = json.intOrNull("begin_shape_index")
        val localEnd = json.intOrNull("end_shape_index")

        if (
            localBegin != null &&
            localBegin !in 0 until localPointCount
        ) {
            throw SchemaException()
        }
        if (
            localEnd != null &&
            localEnd !in 0 until localPointCount
        ) {
            throw SchemaException()
        }

        return RouteManeuver(
            type = json.intOrNull("type"),
            instruction = instruction,
            verbalPreTransitionInstruction = verbalInstruction,
            timeSeconds = json.nonNegativeDoubleOrNull("time"),
            lengthKilometers = json
                .nonNegativeDoubleOrNull("length")
                ?.times(lengthFactor),
            beginShapeIndex = localBegin?.plus(shapeIndexOffset),
            endShapeIndex = localEnd?.plus(shapeIndexOffset),
            travelMode = json.stringOrNull("travel_mode"),
            travelType = json.stringOrNull("travel_type")
        )
    }

    private fun parseSummary(
        json: JSONObject?,
        lengthFactor: Double
    ): RouteSummary = RouteSummary(
        timeSeconds = json?.nonNegativeDoubleOrNull("time"),
        lengthKilometers = json
            ?.nonNegativeDoubleOrNull("length")
            ?.times(lengthFactor)
    )

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) {
            null
        } else {
            optString(key).trim().ifBlank { null }
        }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) {
            null
        } else {
            runCatching { getInt(key) }.getOrNull()
        }

    private fun JSONObject.nonNegativeDoubleOrNull(
        key: String
    ): Double? {
        if (!has(key) || isNull(key)) {
            return null
        }

        val value = runCatching { getDouble(key) }
            .getOrNull()
            ?: return null
        return value.takeIf { it.isFinite() && it >= 0.0 }
    }

    private class SchemaException : Exception()
}
