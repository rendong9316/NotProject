package com.example.locationer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class MeasurementCalculationTest {

    @Test
    fun calculationRequiresTwoDistancePointsAndThreeAreaPoints() {
        assertFalse(isMeasurementReady(MapViewModel.MeasurementMode.DISTANCE, 1))
        assertTrue(isMeasurementReady(MapViewModel.MeasurementMode.DISTANCE, 2))
        assertFalse(isMeasurementReady(MapViewModel.MeasurementMode.AREA, 2))
        assertTrue(isMeasurementReady(MapViewModel.MeasurementMode.AREA, 3))
    }

    @Test
    fun polygonAreaReturnsZeroWithFewerThanThreePoints() {
        assertEquals(
            0.0,
            measurementPolygonAreaMeters(listOf(CT.Coord(116.0, 39.0), CT.Coord(116.001, 39.0))),
            0.0,
        )
    }

    @Test
    fun polygonAreaApproximatesOneHundredMeterSquare() {
        val origin = CT.Coord(116.0, 39.0)
        val latitudeDegrees = 100.0 / 111_195.0
        val longitudeDegrees = 100.0 / (111_195.0 * cos(origin.lat / 180.0 * CT.PI))
        val square = listOf(
            origin,
            CT.Coord(origin.lon + longitudeDegrees, origin.lat),
            CT.Coord(origin.lon + longitudeDegrees, origin.lat + latitudeDegrees),
            CT.Coord(origin.lon, origin.lat + latitudeDegrees),
        )

        assertEquals(10_000.0, measurementPolygonAreaMeters(square), 150.0)
    }
}
