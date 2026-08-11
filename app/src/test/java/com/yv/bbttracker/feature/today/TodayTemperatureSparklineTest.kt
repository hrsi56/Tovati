package com.yv.bbttracker.feature.today

import com.yv.bbttracker.domain.model.DisturbanceFlag
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TodayTemperatureSparklineTest {
    private val today = LocalDate.of(2026, 8, 10)

    @Test
    fun `recent temperatures follow thermal-analysis eligibility rules`() {
        val measurements = listOf(
            measurement(today.minusDays(7), 3_600),
            measurement(today.minusDays(5), 3_610),
            measurement(today.minusDays(4), 3_620, site = MeasurementSite.VAGINAL),
            measurement(today.minusDays(3), 3_630, selectedForAnalysis = false),
            measurement(
                today.minusDays(2),
                3_640,
                disturbanceMask = DisturbanceFlag.ILLNESS_OR_FEVER,
            ),
            measurement(today.minusDays(1), 3_650, id = 10, updatedAt = 10),
            measurement(today.minusDays(1), 3_655, id = 11, updatedAt = 11),
            measurement(today, 3_660),
        )

        val selected = selectRecentThermalMeasurements(
            measurements = measurements,
            today = today,
            measurementSite = MeasurementSite.ORAL,
        )

        assertEquals(
            listOf(today.minusDays(5), today.minusDays(1), today),
            selected.map { it.date },
        )
        assertEquals(listOf(3_610, 3_655, 3_660), selected.map { it.temperatureCentiC })
    }

    @Test
    fun `sparkline samples preserve calendar gaps inside the seven-day window`() {
        val samples = temperatureSparklineSamples(
            measurements = listOf(
                measurement(today, 3_660),
                measurement(today.minusDays(3), 3_630),
                measurement(today.minusDays(4), 3_620),
                measurement(today.minusDays(6), 3_600),
                measurement(today.minusDays(7), 3_590),
            ),
            endDate = today,
        )

        assertEquals(listOf(0, 2, 3, 6), samples.map { it.dayOffset })
        assertEquals(listOf(3_600, 3_620, 3_630, 3_660), samples.map { it.temperatureCentiC })
        assertEquals(listOf(false, false, true, false), samples.map { it.connectToPrevious })
    }

    private fun measurement(
        date: LocalDate,
        temperatureCentiC: Int,
        id: Long = date.toEpochDay(),
        site: MeasurementSite = MeasurementSite.ORAL,
        selectedForAnalysis: Boolean = true,
        disturbanceMask: Long = 0,
        updatedAt: Long = date.toEpochDay(),
    ): TemperatureMeasurement {
        val measuredAt = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return TemperatureMeasurement(
            id = id,
            measurementEpochDay = date.toEpochDay(),
            measuredAtEpochMillis = measuredAt,
            timezoneId = "UTC",
            temperatureCentiC = temperatureCentiC,
            site = site,
            disturbanceMask = disturbanceMask,
            selectedForAnalysis = selectedForAnalysis,
            createdAtEpochMillis = measuredAt,
            updatedAtEpochMillis = updatedAt,
        )
    }
}
