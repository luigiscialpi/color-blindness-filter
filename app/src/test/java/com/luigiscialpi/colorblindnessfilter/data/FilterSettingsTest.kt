package com.luigiscialpi.colorblindnessfilter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterSettingsTest {

    @Test
    fun ivaloriIniziali() {
        assertFalse(FilterSettings.DEFAULT.enabled)
        assertEquals(0.15, FilterSettings.DEFAULT.intensity, 0.0)
    }

    @Test
    fun accettaGliEstremiDellIntervallo() {
        FilterSettings(enabled = true, intensity = 0.0)
        FilterSettings(enabled = true, intensity = 0.5)
    }

    @Test
    fun rifiutaIntensitaFuoriIntervalloONonFinita() {
        val bad = listOf(-0.01, 0.51, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        for (value in bad) {
            assertThrows(IllegalArgumentException::class.java) { FilterSettings(true, value) }
        }
    }

    @Test
    fun copyRispettaLaStessaValidazione() {
        assertThrows(IllegalArgumentException::class.java) {
            FilterSettings.DEFAULT.copy(intensity = 0.9)
        }
        assertEquals(0.3, FilterSettings.DEFAULT.copy(intensity = 0.3).intensity, 0.0)
    }

    @Test
    fun isValidIntensityDistingueIValoriAmmessi() {
        assertTrue(FilterSettings.isValidIntensity(0.0))
        assertTrue(FilterSettings.isValidIntensity(0.15))
        assertTrue(FilterSettings.isValidIntensity(0.5))
        assertFalse(FilterSettings.isValidIntensity(-0.01))
        assertFalse(FilterSettings.isValidIntensity(0.51))
        assertFalse(FilterSettings.isValidIntensity(Double.NaN))
    }
}
