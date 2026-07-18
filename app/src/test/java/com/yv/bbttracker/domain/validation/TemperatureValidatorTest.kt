package com.yv.bbttracker.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureValidatorTest {
    @Test
    fun `comma and dot normalize to centi Celsius`() {
        assertEquals(TemperatureValidation.Valid(3647, false), TemperatureValidator.parse("36,47"))
        assertEquals(TemperatureValidation.Valid(3647, false), TemperatureValidator.parse("36.47"))
    }

    @Test
    fun `hard limits and precision are enforced`() {
        assertTrue(TemperatureValidator.parse("31.99") is TemperatureValidation.Invalid)
        assertTrue(TemperatureValidator.parse("43.01") is TemperatureValidation.Invalid)
        assertTrue(TemperatureValidator.parse("36.471") is TemperatureValidation.Invalid)
        assertEquals(TemperatureValidation.Valid(3200, true), TemperatureValidator.parse("32"))
        assertEquals(TemperatureValidation.Valid(4300, true), TemperatureValidator.parse("43.00"))
    }

    @Test
    fun `single decimal whitespace and fixed point formatting are handled exactly`() {
        assertEquals(TemperatureValidation.Valid(3650, false), TemperatureValidator.parse(" 36.5 "))
        assertEquals(TemperatureValidation.Valid(3600, false), TemperatureValidator.parse("36"))
        assertEquals("36.05", TemperatureValidator.format(3605))
        assertEquals("43.00", TemperatureValidator.format(4300))
    }

    @Test
    fun `soft warning boundaries are exclusive`() {
        assertEquals(TemperatureValidation.Valid(3499, true), TemperatureValidator.parse("34.99"))
        assertEquals(TemperatureValidation.Valid(3500, false), TemperatureValidator.parse("35.00"))
        assertEquals(TemperatureValidation.Valid(3850, false), TemperatureValidator.parse("38.50"))
        assertEquals(TemperatureValidation.Valid(3851, true), TemperatureValidator.parse("38.51"))
    }

    @Test
    fun `ambiguous or non decimal numeric syntax is rejected`() {
        listOf(
            "",
            "36.",
            ".5",
            "+36.5",
            "-36.5",
            "036.5",
            "3e1",
            "36,4,",
            "36 5",
        ).forEach { raw ->
            assertTrue("Expected invalid input: $raw", TemperatureValidator.parse(raw) is TemperatureValidation.Invalid)
        }
    }
}
