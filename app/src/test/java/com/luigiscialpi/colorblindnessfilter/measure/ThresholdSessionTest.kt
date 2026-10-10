package com.luigiscialpi.colorblindnessfilter.measure

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdSessionTest {

    private companion object {
        const val TIMEOUT_MS = 30_000L
        val BACKGROUND = LinearRgb(0.18, 0.18, 0.18)
    }

    /** Osservatore a quattro scelte: Weibull con probabilità di indovinare 0.25 e soglia per asse. */
    private class Observer(
        private val alphas: Map<ChromaticAxis, Double>,
        private val random: Random,
        private val catchesCorrect: Boolean = true,
    ) {
        fun contrastAt794(axis: ChromaticAxis): Double = alphas.getValue(axis) * (-ln((1 - 0.794) / 0.75)).pow(1 / 3.0)

        fun respond(trial: Trial): Orientation {
            val p = if (trial.isCatch) {
                if (catchesCorrect) 1.0 else 0.0
            } else {
                0.25 + 0.75 * (1 - exp(-(kotlin.math.abs(trial.signedContrast) / alphas.getValue(trial.axis)).pow(3.0)))
            }
            val wrong = Orientation.entries.filter { it != trial.orientation }
            return if (random.nextDouble() < p) trial.orientation else wrong[random.nextInt(wrong.size)]
        }
    }

    private fun session(seed: Int, axes: List<ChromaticAxis> = ChromaticAxis.entries, catchEvery: Int = 8) =
        ThresholdSession(axes, BACKGROUND, Random(seed), luminanceJitter = 0.10, catchEvery = catchEvery)

    private fun run(session: ThresholdSession, observer: Observer): List<Trial> {
        val presented = mutableListOf<Trial>()
        while (true) {
            val trial = session.nextTrial() ?: break
            presented.add(trial)
            session.answer(observer.respond(trial), responseTimeMs = 700, screenBrightness = 1.0)
        }
        return presented
    }

    private val alphas = mapOf(ChromaticAxis.M_ISOLATING to 0.15, ChromaticAxis.S_ISOLATING to 0.03)

    @Test(timeout = TIMEOUT_MS)
    fun laSessioneTerminaEProduceUnaSogliaPerOgniAsse() {
        val session = session(1)
        run(session, Observer(alphas, Random(10)))

        assertTrue(session.isFinished)
        assertNull(session.nextTrial())
        val thresholds = session.thresholds()
        assertNotNull(thresholds.getValue(ChromaticAxis.M_ISOLATING))
        assertNotNull(thresholds.getValue(ChromaticAxis.S_ISOLATING))
    }

    @Test(timeout = TIMEOUT_MS)
    fun leSoglieMedieConvergonoAlPuntoAl794DiOgniAsse() {
        val observer = Observer(alphas, Random(2024))
        val logs = ChromaticAxis.entries.associateWith { mutableListOf<Double>() }
        repeat(80) { i ->
            val session = session(seed = 100 + i)
            run(session, observer)
            for ((axis, threshold) in session.thresholds()) logs.getValue(axis).add(ln(threshold!!))
        }
        for (axis in ChromaticAxis.entries) {
            val ratio = exp(logs.getValue(axis).average()) / observer.contrastAt794(axis)
            assertTrue("asse $axis rapporto=$ratio", ratio in 0.94..1.06)
        }
    }

    @Test(timeout = TIMEOUT_MS)
    fun leProveCatchSonoUnaOgniOttoSuAsseSAdAltoContrasto() {
        val session = session(3)
        val presented = run(session, Observer(alphas, Random(11)))

        presented.forEachIndexed { index, trial ->
            assertEquals("prova ${index + 1}", (index + 1) % 8 == 0, trial.isCatch)
        }
        val catches = presented.filter { it.isCatch }
        assertTrue(catches.isNotEmpty())
        assertTrue(catches.all { it.axis == ChromaticAxis.S_ISOLATING })
        val normalS = presented.filter { !it.isCatch && it.axis == ChromaticAxis.S_ISOLATING }
        val minCatch = catches.minOf { kotlin.math.abs(it.signedContrast) }
        assertTrue("il catch è sopra il livello tipico dell'asse S", minCatch > normalS.map { kotlin.math.abs(it.signedContrast) }.average())
    }

    @Test(timeout = TIMEOUT_MS)
    fun leProveCatchNonMuovonoLeSoglie() {
        val observer = Observer(alphas, Random(5), catchesCorrect = false)
        val logs = mutableListOf<Double>()
        repeat(60) { i ->
            val session = session(seed = 500 + i)
            run(session, observer)
            logs.add(ln(session.thresholds().getValue(ChromaticAxis.S_ISOLATING)!!))
        }
        val ratio = exp(logs.average()) / observer.contrastAt794(ChromaticAxis.S_ISOLATING)
        assertTrue("rapporto=$ratio", ratio in 0.94..1.06)
    }

    @Test(timeout = TIMEOUT_MS)
    fun laQuotaDiCatchCorretteSiMisuraSoloSeCiSonoProveCatch() {
        val perfect = session(6)
        run(perfect, Observer(alphas, Random(1), catchesCorrect = true))
        assertEquals(1.0, perfect.catchAccuracy()!!, 0.0)

        val inattentive = session(7)
        run(inattentive, Observer(alphas, Random(1), catchesCorrect = false))
        assertEquals(0.0, inattentive.catchAccuracy()!!, 0.0)

        val noCatch = session(8, catchEvery = 0)
        run(noCatch, Observer(alphas, Random(1)))
        assertNull(noCatch.catchAccuracy())
        assertTrue(noCatch.log.records.none { it.isCatch })
    }

    @Test(timeout = TIMEOUT_MS)
    fun ilRegistroHaUnaRigaPerProvaConContrastiNeiLimiti() {
        val session = session(9)
        val presented = run(session, Observer(alphas, Random(2)))

        val records = session.log.records
        assertEquals(presented.size, records.size)
        assertEquals(presented.size, session.answeredTrials)
        for ((trial, record) in presented.zip(records)) {
            assertEquals(trial.axis, record.axis)
            assertEquals(kotlin.math.abs(trial.signedContrast), record.contrast, 1e-12)
            assertEquals(trial.isCatch, record.isCatch)
            assertTrue(record.contrast in 0.002..1.0)
        }
    }

    @Test(timeout = TIMEOUT_MS)
    fun orientamentiESegniSonoVariati() {
        val orientations = mutableListOf<Orientation>()
        val signs = mutableListOf<Double>()
        repeat(20) { i ->
            val session = session(seed = 900 + i)
            for (trial in run(session, Observer(alphas, Random(i)))) {
                orientations.add(trial.orientation)
                signs.add(kotlin.math.sign(trial.signedContrast))
            }
        }
        for (o in Orientation.entries) {
            val share = orientations.count { it == o }.toDouble() / orientations.size
            assertTrue("orientamento $o quota=$share", share in 0.21..0.29)
        }
        val positiveShare = signs.count { it > 0 }.toDouble() / signs.size
        assertTrue("quota segni positivi=$positiveShare", positiveShare in 0.45..0.55)
    }

    @Test(timeout = TIMEOUT_MS)
    fun conLoStessoSemeEGliStessiOsservatoriIlRegistroEIdentico() {
        val a = session(11)
        val b = session(11)
        run(a, Observer(alphas, Random(3)))
        run(b, Observer(alphas, Random(3)))

        assertEquals(a.log.toCsv(), b.log.toCsv())
    }

    @Test
    fun nonSiPuoChiedereUnaNuovaProvaSenzaRispondere() {
        val session = session(12)
        session.nextTrial()
        assertThrows(IllegalStateException::class.java) { session.nextTrial() }
    }

    @Test
    fun nonSiPuoRispondereSenzaUnaProvaInCorso() {
        val session = session(13)
        assertThrows(IllegalStateException::class.java) { session.answer(Orientation.UP, 500, 1.0) }
    }

    @Test
    fun conUnSoloAsseLaSessioneUsaSoloQuelloEIlCatch() {
        val session = session(14, axes = listOf(ChromaticAxis.M_ISOLATING))
        val presented = run(session, Observer(alphas, Random(4)))

        assertTrue(presented.all { it.axis == ChromaticAxis.M_ISOLATING || it.isCatch })
        assertEquals(setOf(ChromaticAxis.M_ISOLATING), session.thresholds().keys)
    }

    @Test
    fun parametriNonValidiVengonoRifiutati() {
        val random = Random(1)
        assertThrows(IllegalArgumentException::class.java) { ThresholdSession(emptyList(), BACKGROUND, random, 0.1) }
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdSession(listOf(ChromaticAxis.M_ISOLATING, ChromaticAxis.M_ISOLATING), BACKGROUND, random, 0.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdSession(listOf(ChromaticAxis.M_ISOLATING), BACKGROUND, random, 0.1, catchEvery = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdSession(listOf(ChromaticAxis.M_ISOLATING), LinearRgb(0.95, 0.95, 0.95), random, 0.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdSession(listOf(ChromaticAxis.M_ISOLATING), BACKGROUND, random, 0.1, minContrast = 5.0)
        }
    }
}
