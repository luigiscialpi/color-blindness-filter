package com.luigiscialpi.colorblindnessfilter

import com.luigiscialpi.colorblindnessfilter.data.FilterSettings
import com.luigiscialpi.colorblindnessfilter.domain.ColorTransform
import com.luigiscialpi.colorblindnessfilter.domain.LuminanceShiftFilter
import com.luigiscialpi.colorblindnessfilter.system.ApplyResult
import com.luigiscialpi.colorblindnessfilter.system.ScreenColorApplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterControllerTest {

    private class FakeApplier(private val result: ApplyResult = ApplyResult.Success) : ScreenColorApplier {
        val applied = mutableListOf<ColorTransform>()
        var resets = 0

        override fun apply(transform: ColorTransform): ApplyResult {
            applied.add(transform)
            return result
        }

        override fun reset(): ApplyResult {
            resets++
            return result
        }
    }

    @Test
    fun accesoApplicaLaMatriceDellIntensitaScelta() {
        val applier = FakeApplier()

        val outcome = FilterController(applier).apply(FilterSettings(enabled = true, intensity = 0.15))

        assertEquals(ApplyResult.Success, outcome)
        assertEquals(listOf(LuminanceShiftFilter.transform(0.15)), applier.applied)
        assertEquals(0, applier.resets)
    }

    @Test
    fun spentoRipristinaISColori() {
        val applier = FakeApplier()

        val outcome = FilterController(applier).apply(FilterSettings(enabled = false, intensity = 0.3))

        assertEquals(ApplyResult.Success, outcome)
        assertEquals(1, applier.resets)
        assertTrue(applier.applied.isEmpty())
    }

    @Test
    fun restituisceLEsitoDellApplierInvariato() {
        val results = listOf(ApplyResult.RootUnavailable, ApplyResult.Failed("errore"))
        for (result in results) {
            val controller = FilterController(FakeApplier(result))
            assertEquals(result, controller.apply(FilterSettings(true, 0.15)))
            assertEquals(result, controller.apply(FilterSettings(false, 0.15)))
        }
    }

    @Test
    fun cambiandoIntensitaApplicaLaNuovaMatriceInOrdine() {
        val applier = FakeApplier()
        val controller = FilterController(applier)

        controller.apply(FilterSettings(true, 0.1))
        controller.apply(FilterSettings(true, 0.2))

        assertEquals(
            listOf(LuminanceShiftFilter.transform(0.1), LuminanceShiftFilter.transform(0.2)),
            applier.applied,
        )
    }

    @Test
    fun accesoConIntensitaZeroApplicaLaMatriceIdentita() {
        val applier = FakeApplier()

        FilterController(applier).apply(FilterSettings(enabled = true, intensity = 0.0))

        assertEquals(listOf(ColorTransform.IDENTITY), applier.applied)
    }
}
