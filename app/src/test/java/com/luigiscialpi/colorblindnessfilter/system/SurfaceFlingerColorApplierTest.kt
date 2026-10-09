package com.luigiscialpi.colorblindnessfilter.system

import com.luigiscialpi.colorblindnessfilter.domain.ColorTransform
import com.luigiscialpi.colorblindnessfilter.domain.LuminanceShiftFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceFlingerColorApplierTest {

    /** Risposta osservata sul dispositivo di prova: `Parcel(NULL)` con codice di uscita 0. */
    private val deviceReply = ShellResult(isSuccess = true, output = listOf("Result: Parcel(NULL)"))

    @Test
    fun applyInviaLaTransazioneConLaMatriceAttesa() {
        val shell = FakeRootShell(result = deviceReply)
        val transform = LuminanceShiftFilter.transform(0.15)

        val outcome = SurfaceFlingerColorApplier(shell).apply(transform)

        assertEquals(ApplyResult.Success, outcome)
        assertEquals(
            listOf("service call SurfaceFlinger 1015 i32 1 ${transform.toSurfaceFlingerArgs()}"),
            shell.commands,
        )
    }

    @Test
    fun applyConBeta015ProduceIlComandoGiaProvatoSulDispositivo() {
        val shell = FakeRootShell(result = deviceReply)

        SurfaceFlingerColorApplier(shell).apply(LuminanceShiftFilter.transform(0.15))

        val expected = "service call SurfaceFlinger 1015 i32 1 " +
            "f 1.150000 f 0.150000 f 0.150000 f 0.000000 " +
            "f -0.150000 f 0.850000 f -0.150000 f 0.000000 " +
            "f 0.000000 f 0.000000 f 1.000000 f 0.000000 " +
            "f 0.000000 f 0.000000 f 0.000000 f 1.000000"
        assertEquals(listOf(expected), shell.commands)
    }

    @Test
    fun resetInviaIlComandoDiRipristino() {
        val shell = FakeRootShell(result = deviceReply)

        val outcome = SurfaceFlingerColorApplier(shell).reset()

        assertEquals(ApplyResult.Success, outcome)
        assertEquals(listOf("service call SurfaceFlinger 1015 i32 0"), shell.commands)
    }

    @Test
    fun senzaRootNonEseguePiuNessunComando() {
        val shell = FakeRootShell(rootAvailable = false, result = deviceReply)
        val applier = SurfaceFlingerColorApplier(shell)

        assertEquals(ApplyResult.RootUnavailable, applier.apply(ColorTransform.IDENTITY))
        assertEquals(ApplyResult.RootUnavailable, applier.reset())
        assertTrue(shell.commands.isEmpty())
    }

    @Test
    fun unComandoFallitoRiportaLOutputDellaShell() {
        val shell = FakeRootShell(result = ShellResult(false, listOf("errore uno", "errore due")))

        val outcome = SurfaceFlingerColorApplier(shell).apply(ColorTransform.IDENTITY)

        assertEquals(ApplyResult.Failed("errore uno\nerrore due"), outcome)
    }

    @Test
    fun unComandoFallitoSenzaOutputHaUnMessaggioDiDefault() {
        val shell = FakeRootShell(result = ShellResult(false, emptyList()))

        val outcome = SurfaceFlingerColorApplier(shell).reset()

        assertEquals(ApplyResult.Failed("comando fallito senza output"), outcome)
    }

    @Test
    fun uscitaZeroSenzaParcelNonESuccesso() {
        val shell = FakeRootShell(result = ShellResult(true, listOf("output inatteso")))

        val outcome = SurfaceFlingerColorApplier(shell).apply(ColorTransform.IDENTITY)

        assertEquals(ApplyResult.Failed("output inatteso"), outcome)
    }

    @Test
    fun codiceDiUscitaDiversoDaZeroNonESuccessoNemmenoConParcel() {
        val shell = FakeRootShell(result = ShellResult(false, listOf("Result: Parcel(NULL)")))

        val outcome = SurfaceFlingerColorApplier(shell).apply(ColorTransform.IDENTITY)

        assertEquals(ApplyResult.Failed("Result: Parcel(NULL)"), outcome)
    }

    @Test
    fun uscitaZeroSenzaOutputNonESuccesso() {
        val shell = FakeRootShell(result = ShellResult(true, emptyList()))

        val outcome = SurfaceFlingerColorApplier(shell).reset()

        assertEquals(ApplyResult.Failed("comando fallito senza output"), outcome)
    }

    @Test
    fun comandiSuccessiviVengonoInviatiInOrdine() {
        val shell = FakeRootShell(result = deviceReply)
        val applier = SurfaceFlingerColorApplier(shell)

        applier.apply(LuminanceShiftFilter.transform(0.1))
        applier.apply(LuminanceShiftFilter.transform(0.2))
        applier.reset()

        assertEquals(3, shell.commands.size)
        assertTrue(shell.commands[0].contains("f 1.100000"))
        assertTrue(shell.commands[1].contains("f 1.200000"))
        assertEquals("service call SurfaceFlinger 1015 i32 0", shell.commands[2])
    }
}
