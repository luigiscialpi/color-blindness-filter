package com.luigiscialpi.colorblindnessfilter

import com.luigiscialpi.colorblindnessfilter.data.FilterSettings
import com.luigiscialpi.colorblindnessfilter.domain.ColorTransform
import com.luigiscialpi.colorblindnessfilter.domain.LuminanceShiftFilter
import com.luigiscialpi.colorblindnessfilter.system.ApplyResult
import com.luigiscialpi.colorblindnessfilter.system.BootScriptWriter
import com.luigiscialpi.colorblindnessfilter.system.FakeRootShell
import com.luigiscialpi.colorblindnessfilter.system.ScreenColorApplier
import com.luigiscialpi.colorblindnessfilter.system.ShellResult
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

    private val shellOk = ShellResult(isSuccess = true, output = emptyList())
    private val removeCommand =
        "rm -f '${BootScriptWriter.DEFAULT_DIRECTORY}/${BootScriptWriter.SCRIPT_NAME}'"

    private fun controller(applier: ScreenColorApplier, shell: FakeRootShell) =
        FilterController(applier, BootScriptWriter(shell))

    @Test
    fun accesoApplicaLaMatriceEInstallaLoScriptDiAvvio() {
        val applier = FakeApplier()
        val shell = FakeRootShell(result = shellOk)
        val transform = LuminanceShiftFilter.transform(0.15)

        val outcome = controller(applier, shell).apply(FilterSettings(enabled = true, intensity = 0.15))

        assertEquals(ApplyResult.Success, outcome)
        assertEquals(listOf(transform), applier.applied)
        assertEquals(0, applier.resets)
        assertTrue(shell.commands.single().contains(transform.toSurfaceFlingerArgs()))
    }

    @Test
    fun spentoRipristinaIColoriERimuoveLoScript() {
        val applier = FakeApplier()
        val shell = FakeRootShell(result = shellOk)

        val outcome = controller(applier, shell).apply(FilterSettings(enabled = false, intensity = 0.3))

        assertEquals(ApplyResult.Success, outcome)
        assertEquals(1, applier.resets)
        assertTrue(applier.applied.isEmpty())
        assertEquals(listOf(removeCommand), shell.commands)
    }

    @Test
    fun seLApplierFallisceRestituisceLEsitoENonToccaLoScript() {
        val failures = listOf(ApplyResult.RootUnavailable, ApplyResult.Failed("errore"))
        for (failure in failures) {
            val shell = FakeRootShell(result = shellOk)
            val controller = controller(FakeApplier(failure), shell)

            assertEquals(failure, controller.apply(FilterSettings(true, 0.15)))
            assertEquals(failure, controller.apply(FilterSettings(false, 0.15)))
            assertTrue(shell.commands.isEmpty())
        }
    }

    @Test
    fun seLoScriptFallisceRestituisceLErroreDelloScript() {
        val shell = FakeRootShell(result = ShellResult(isSuccess = false, output = listOf("scrittura negata")))
        val controller = controller(FakeApplier(), shell)

        assertEquals(ApplyResult.Failed("scrittura negata"), controller.apply(FilterSettings(true, 0.15)))
        assertEquals(ApplyResult.Failed("scrittura negata"), controller.apply(FilterSettings(false, 0.15)))
    }

    @Test
    fun cambiandoIntensitaApplicaEReinstallaInOrdine() {
        val applier = FakeApplier()
        val shell = FakeRootShell(result = shellOk)
        val controller = controller(applier, shell)

        controller.apply(FilterSettings(true, 0.1))
        controller.apply(FilterSettings(true, 0.2))

        assertEquals(
            listOf(LuminanceShiftFilter.transform(0.1), LuminanceShiftFilter.transform(0.2)),
            applier.applied,
        )
        assertEquals(2, shell.commands.size)
        assertTrue(shell.commands[0].contains(LuminanceShiftFilter.transform(0.1).toSurfaceFlingerArgs()))
        assertTrue(shell.commands[1].contains(LuminanceShiftFilter.transform(0.2).toSurfaceFlingerArgs()))
    }

    @Test
    fun accesoConIntensitaZeroApplicaLaMatriceIdentita() {
        val applier = FakeApplier()

        controller(applier, FakeRootShell(result = shellOk)).apply(FilterSettings(true, 0.0))

        assertEquals(listOf(ColorTransform.IDENTITY), applier.applied)
    }
}
