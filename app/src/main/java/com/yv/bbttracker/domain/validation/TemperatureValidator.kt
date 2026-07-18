package com.yv.bbttracker.domain.validation

import java.math.BigDecimal
import java.math.RoundingMode

sealed interface TemperatureValidation {
    data class Valid(val centiCelsius: Int, val hasSoftWarning: Boolean) : TemperatureValidation
    data object Invalid : TemperatureValidation
}

object TemperatureValidator {
    private val pattern = Regex("^\\d{2}(?:[.,]\\d{1,2})?$")

    fun parse(raw: String): TemperatureValidation {
        val normalized = raw.trim().replace(',', '.')
        if (!pattern.matches(normalized)) return TemperatureValidation.Invalid
        val value = runCatching { BigDecimal(normalized) }.getOrNull() ?: return TemperatureValidation.Invalid
        if (value < BigDecimal("32.00") || value > BigDecimal("43.00")) {
            return TemperatureValidation.Invalid
        }
        val centi = value
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .intValueExact()
        return TemperatureValidation.Valid(
            centiCelsius = centi,
            hasSoftWarning = value < BigDecimal("35.00") || value > BigDecimal("38.50"),
        )
    }

    fun format(centiCelsius: Int): String = "%d.%02d".format(centiCelsius / 100, centiCelsius % 100)
}

