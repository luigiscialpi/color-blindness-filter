package com.luigiscialpi.colorblindnessfilter.measure

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StaircaseTest {

    private companion object {
        // I cicli `while (!isFinished)` non devono poter bloccare la suite se la guardia sulle prove si rompe.
        const val TIMEOUT_MS = 20_000L
    }

    /** Osservatore simulato a quattro scelte: Weibull con probabilità di indovinare 0.25. */
    private class SimulatedObserver(private val alpha: Double, private val slope: Double = 3.0, private val random: Random) {
        fun probabilityCorrect(contrast: Double): Double =
            0.25 + 0.75 * (1 - exp(-(contrast / alpha).pow(slope)))

        fun respond(contrast: Double): Boolean = random.nextDouble() < probabilityCorrect(contrast)

        /** Contrasto con 79,4% di risposte corrette: il punto a cui converge 3-down/1-up. */
        fun contrastAt794(): Double = alpha * (-ln((1 - 0.794) / 0.75)).pow(1 / slope)
    }

    private fun newStaircase() = Staircase(startLevel = 0.2, minLevel = 0.001, maxLevel = 1.0)

    private fun runOne(observer: SimulatedObserver): Double? {
        val staircase = newStaircase()
        while (!staircase.isFinished) staircase.record(observer.respond(staircase.level))
        return staircase.threshold()
    }

    @Test
    fun treCorretteConsecutiveAbbassanoIlLivelloUnErroreLoAlza() {
        val staircase = Staircase(startLevel = 0.1, minLevel = 0.001, maxLevel = 1.0, stepFactor = 2.0)

        staircase.record(true)
        staircase.record(true)
        assertEquals(0.1, staircase.level, 1e-12)
        staircase.record(true)
        assertEquals(0.05, staircase.level, 1e-12)
        staircase.record(false)
        assertEquals(0.1, staircase.level, 1e-12)
    }

    @Test
    fun unErroreAzzeraLaSerieDiCorrette() {
        val staircase = Staircase(startLevel = 0.1, minLevel = 0.001, maxLevel = 1.0, stepFactor = 2.0)

        staircase.record(true)
        staircase.record(true)
        staircase.record(false)
        staircase.record(true)
        staircase.record(true)

        assertEquals(0.2, staircase.level, 1e-12)
    }

    @Test
    fun leInversioniSiContanoSoloAlCambioDiDirezione() {
        val staircase = Staircase(startLevel = 0.1, minLevel = 0.001, maxLevel = 1.0, stepFactor = 2.0)

        staircase.record(false)
        staircase.record(false)
        assertEquals(0, staircase.reversals)
        repeat(3) { staircase.record(true) }
        assertEquals(1, staircase.reversals)
        repeat(3) { staircase.record(true) }
        assertEquals(1, staircase.reversals)
        staircase.record(false)
        assertEquals(2, staircase.reversals)
    }

    @Test
    fun laSogliaEILaMediaGeometricaDegliUltimiLivelliDiInversione() {
        val staircase = Staircase(
            startLevel = 0.1, minLevel = 0.001, maxLevel = 1.0, stepFactor = 2.0,
            targetReversals = 4, averagedReversals = 2,
        )
        // livelli: 0.1 -f-> 0.2 (dir +) -> 3 corrette -> 0.1 (inversione a 0.2)
        // -> f -> 0.2 (inversione a 0.1) -> 3 corrette -> 0.1 (inversione a 0.2) -> f -> 0.2 (inversione a 0.1)
        staircase.record(false)
        repeat(3) { staircase.record(true) }
        staircase.record(false)
        repeat(3) { staircase.record(true) }
        staircase.record(false)

        assertTrue(staircase.isFinished)
        assertEquals(sqrt01x02(), staircase.threshold()!!, 1e-12)
    }

    private fun sqrt01x02() = kotlin.math.sqrt(0.1 * 0.2)

    @Test
    fun senzaAbbastanzaInversioniLaSogliaENull() {
        val staircase = newStaircase()
        staircase.record(true)
        assertNull(staircase.threshold())
    }

    @Test
    fun terminaDopoLeInversioniPrevisteERifiutaAltreRisposte() {
        val staircase = Staircase(
            startLevel = 0.1, minLevel = 0.001, maxLevel = 1.0, stepFactor = 2.0,
            targetReversals = 2, averagedReversals = 2,
        )
        staircase.record(false)
        repeat(3) { staircase.record(true) }
        assertFalse(staircase.isFinished)
        staircase.record(false)

        assertTrue(staircase.isFinished)
        assertThrows(IllegalStateException::class.java) { staircase.record(true) }
    }

    @Test
    fun ilLivelloRestaNeiLimiti() {
        val staircase = Staircase(startLevel = 0.5, minLevel = 0.1, maxLevel = 1.0, stepFactor = 2.0, maxTrials = 100)

        repeat(10) { staircase.record(false) }
        assertEquals(1.0, staircase.level, 1e-12)
        repeat(30) { staircase.record(true) }
        assertEquals(0.1, staircase.level, 1e-12)
    }

    @Test(timeout = TIMEOUT_MS)
    fun seLoStimoloNonSiVedeMaiTerminaPerLimiteDiProveSenzaSoglia() {
        val staircase = Staircase(startLevel = 0.5, minLevel = 0.01, maxLevel = 1.0, maxTrials = 50)

        while (!staircase.isFinished) staircase.record(false)

        assertEquals(50, staircase.trials)
        assertNull(staircase.threshold())
        assertEquals(1.0, staircase.level, 1e-12)
    }

    @Test(timeout = TIMEOUT_MS)
    fun seLoStimoloSiVedeSempreTerminaPerLimiteDiProveSenzaSoglia() {
        // con passo 1.25 servono circa 90 risposte corrette per scendere da 0.5 al minimo 0.01
        val staircase = Staircase(startLevel = 0.5, minLevel = 0.01, maxLevel = 1.0, maxTrials = 150)

        while (!staircase.isFinished) staircase.record(true)

        assertEquals(150, staircase.trials)
        assertNull(staircase.threshold())
        assertEquals(0.01, staircase.level, 1e-12)
    }

    @Test(timeout = TIMEOUT_MS)
    fun conOsservatoriSimulatiLaMediaDelleSoglieConvergeAlPuntoAl794() {
        for (alpha in listOf(0.01, 0.05, 0.2)) {
            val random = Random(12345)
            val observer = SimulatedObserver(alpha, random = random)
            val thresholds = List(300) { runOne(observer) }

            assertTrue("tutte le sessioni producono una soglia", thresholds.all { it != null })
            val geometricMean = exp(thresholds.map { ln(it!!) }.average())
            val expected = observer.contrastAt794()
            val ratio = geometricMean / expected
            // osservato circa 0.99 (errore standard della media ~0.6%): ±4% ammette il bias e rileva una regola sbagliata
            assertTrue("alpha=$alpha rapporto=$ratio", ratio in 0.96..1.04)
        }
    }

    @Test(timeout = TIMEOUT_MS)
    fun laDispersioneDellaStimaSullaSingolaSessioneEContenuta() {
        val observer = SimulatedObserver(0.05, random = Random(777))
        val logs = List(300) { ln(runOne(observer)!!) }
        val mean = logs.average()
        val sd = kotlin.math.sqrt(logs.sumOf { (it - mean) * (it - mean) } / (logs.size - 1))
        // deviazione standard del logaritmo naturale: osservata circa 0.11, cioè circa l'11% sulla soglia
        assertTrue("sd=$sd", sd < 0.16)
    }

    @Test
    fun parametriNonValidiVengonoRifiutati() {
        assertThrows(IllegalArgumentException::class.java) { Staircase(0.5, 0.0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { Staircase(0.5, 1.0, 0.5) }
        assertThrows(IllegalArgumentException::class.java) { Staircase(2.0, 0.1, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { Staircase(0.5, 0.1, 1.0, stepFactor = 1.0) }
        assertThrows(IllegalArgumentException::class.java) { Staircase(0.5, 0.1, 1.0, targetReversals = 4, averagedReversals = 5) }
        assertThrows(IllegalArgumentException::class.java) { Staircase(0.5, 0.1, 1.0, maxTrials = 0) }
        assertNotNull(Staircase(0.5, 0.1, 1.0))
    }
}
