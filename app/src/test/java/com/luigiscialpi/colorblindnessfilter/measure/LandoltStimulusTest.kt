package com.luigiscialpi.colorblindnessfilter.measure

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LandoltStimulusTest {

    private val background = LinearRgb(0.18, 0.18, 0.18)

    private fun generator(seed: Int = 1, jitter: Double = 0.10) =
        LandoltStimulusGenerator(Random(seed), gridSize = 36, luminanceJitter = jitter)

    /** Con jitter zero i punti della C hanno esattamente il colore dello stimolo. */
    private fun stimulusDots(orientation: Orientation, contrast: Double = 0.2): List<Dot> {
        val dots = generator(jitter = 0.0).generate(background, ChromaticAxis.M_ISOLATING, contrast, orientation)
        val stimulusColor = ConeSpace.stimulusColor(background, ChromaticAxis.M_ISOLATING, contrast)
        return dots.filter { it.color == stimulusColor }
    }

    /** Punti della C in una fascia radiale larga quanto il tratto, nella direzione indicata. */
    private fun countInStrip(dots: List<Dot>, direction: Orientation): Int = dots.count { dot ->
        val rx = dot.x - LandoltStimulusGenerator.RING_CENTER
        val ry = dot.y - LandoltStimulusGenerator.RING_CENTER
        val along = rx * direction.dx + ry * direction.dy
        val across = rx * -direction.dy + ry * direction.dx
        along > 0 && abs(across) < LandoltStimulusGenerator.RING_STROKE / 2
    }

    /** Punti della C ai lati dell'apertura: fascia tra metà tratto e un tratto intero dall'asse. */
    private fun countOnGapShoulders(dots: List<Dot>, direction: Orientation): Int = dots.count { dot ->
        val rx = dot.x - LandoltStimulusGenerator.RING_CENTER
        val ry = dot.y - LandoltStimulusGenerator.RING_CENTER
        val along = rx * direction.dx + ry * direction.dy
        val across = abs(rx * -direction.dy + ry * direction.dx)
        along > 0 && across >= LandoltStimulusGenerator.RING_STROKE / 2 && across < LandoltStimulusGenerator.RING_STROKE
    }

    @Test
    fun ilCampoHaUnPuntoPerCellaTuttiDentroIlQuadrato() {
        val dots = generator().generate(background, ChromaticAxis.M_ISOLATING, 0.1, Orientation.UP)

        assertEquals(36 * 36, dots.size)
        assertTrue(dots.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
        assertTrue(dots.all { it.radius > 0 })
    }

    @Test
    fun conLoStessoSemeLoStimoloEIdentico() {
        val a = generator(seed = 7).generate(background, ChromaticAxis.S_ISOLATING, 0.05, Orientation.LEFT)
        val b = generator(seed = 7).generate(background, ChromaticAxis.S_ISOLATING, 0.05, Orientation.LEFT)

        assertEquals(a, b)
    }

    @Test
    fun conLoStessoSemeOrientamentoEContrastoCambianoSoloIColori() {
        val up = generator(seed = 3).generate(background, ChromaticAxis.M_ISOLATING, 0.1, Orientation.UP)
        val down = generator(seed = 3).generate(background, ChromaticAxis.M_ISOLATING, 0.4, Orientation.DOWN)

        assertEquals(up.map { it.x to it.y }, down.map { it.x to it.y })
        assertNotEquals(up.map { it.color }, down.map { it.color })
    }

    @Test
    fun laVariazioneDiLuminanzaRestaNeiLimitiEVaria() {
        val dots = generator(jitter = 0.10).generate(background, ChromaticAxis.M_ISOLATING, 0.0, Orientation.UP)

        val ratios = dots.map { it.color.r / background.r }
        assertTrue(ratios.all { it in 0.90 - 1e-9..1.10 + 1e-9 })
        assertTrue("la luminanza deve variare davvero", ratios.max() - ratios.min() > 0.15)
    }

    @Test
    fun laSegnalazioneDelContrastoCambiaIlColoreDellaCNelVersoGiusto() {
        val bg = ConeSpace.toCones(background)
        val up = ConeSpace.toCones(stimulusDots(Orientation.UP, contrast = 0.2).first().color)
        val down = ConeSpace.toCones(stimulusDots(Orientation.UP, contrast = -0.2).first().color)

        assertTrue(up.m > bg.m)
        assertTrue(down.m < bg.m)
    }

    @Test
    fun ilCerchioHaUnaAperturaSoloNellaDirezioneIndicata() {
        for (orientation in Orientation.entries) {
            val dots = stimulusDots(orientation)
            assertEquals("apertura $orientation", 0, countInStrip(dots, orientation))
            for (other in Orientation.entries.filter { it != orientation }) {
                assertTrue("tratto $other con apertura $orientation", countInStrip(dots, other) > 0)
            }
        }
    }

    @Test
    fun laAperturaNonEPiuLargaDelTrattoEISuoiLatiSonoDisegnati() {
        for (orientation in Orientation.entries) {
            val dots = stimulusDots(orientation)
            assertTrue("lati dell'apertura $orientation", countOnGapShoulders(dots, orientation) > 0)
        }
    }

    @Test
    fun iPuntiDellaCStannoNellAnelloELeProporzioniSonoQuelleDiLandolt() {
        val dots = stimulusDots(Orientation.UP)

        assertTrue(dots.size > 100)
        assertTrue(
            dots.all {
                val d = hypot(it.x - LandoltStimulusGenerator.RING_CENTER, it.y - LandoltStimulusGenerator.RING_CENTER)
                d in LandoltStimulusGenerator.RING_INNER..LandoltStimulusGenerator.RING_OUTER
            },
        )
        // tratto e apertura valgono un quinto del diametro esterno
        assertEquals(LandoltStimulusGenerator.RING_OUTER * 2 / 5, LandoltStimulusGenerator.RING_STROKE, 1e-12)
    }

    @Test
    fun alContrastoMassimoTuttiIPuntiRestanoNelGamut() {
        val bright = background * (1 + 0.10)
        for (axis in ChromaticAxis.entries) {
            val max = ConeSpace.maxContrast(bright, axis)
            for (sign in listOf(1.0, -1.0)) {
                val dots = generator().generate(background, axis, sign * max, Orientation.RIGHT)
                assertTrue("$axis segno $sign", dots.all { it.color.isInGamut() })
            }
        }
    }

    @Test
    fun parametriNonValidiVengonoRifiutati() {
        assertThrows(IllegalArgumentException::class.java) { LandoltStimulusGenerator(Random(1), gridSize = 4) }
        assertThrows(IllegalArgumentException::class.java) { LandoltStimulusGenerator(Random(1), luminanceJitter = -0.1) }
        assertThrows(IllegalArgumentException::class.java) { LandoltStimulusGenerator(Random(1), luminanceJitter = 0.6) }
    }
}
