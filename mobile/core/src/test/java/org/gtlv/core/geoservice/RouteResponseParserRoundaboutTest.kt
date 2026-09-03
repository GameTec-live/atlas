package org.gtlv.core.geoservice

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteResponseParserRoundaboutTest {
    @Test
    fun `roundabout exit count is preserved`() {
        val response = """
            {
              "trip": {
                "units": "kilometers",
                "legs": [{
                  "shape": "????",
                  "maneuvers": [{
                    "type": 26,
                    "instruction": "Take the 3rd exit",
                    "begin_shape_index": 0,
                    "end_shape_index": 1,
                    "roundabout_exit_count": 3,
                    "bearing_before": 360,
                    "bearing_after": 270
                  }]
                }]
              }
            }
        """.trimIndent()

        val result = RouteResponseParser.parse(response, 200)
            as RouteResult.Success

        assertEquals(3, result.route.maneuvers.single().roundaboutExitCount)
        assertEquals(0, result.route.maneuvers.single().bearingBeforeDegrees)
        assertEquals(270, result.route.maneuvers.single().bearingAfterDegrees)
    }
}
