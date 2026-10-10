package com.luigiscialpi.colorblindnessfilter.measure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConeSpaceTest {

    private val gray = LinearRgb(0.2, 0.2, 0.2)
    private val samples = listOf(
        LinearRgb(0.0, 0.0, 0.0),
        LinearRgb(1.0, 1.0, 1.0),
        LinearRgb(0.8, 0.1, 0.3),
        LinearRgb(0.05, 0.9, 0.4),
        LinearRgb(0.3, 0.3, 0.95),
    )

    private fun assertRgbEquals(expected: LinearRgb, actual: LinearRgb, tolerance: Double) {
        assertEquals("r", expected.r, actual.r, tolerance)
        assertEquals("g", expected.g, actual.g, tolerance)
        assertEquals("b", expected.b, actual.b, tolerance)
    }

    @Test
    fun andataERitornoRgbConiRgbRestituisceLoStessoColore() {
        for (rgb in samples) {
            assertRgbEquals(rgb, ConeSpace.toLinearRgb(ConeSpace.toCones(rgb)), 1e-9)
        }
    }

    @Test
    fun ilBiancoHaLuminanzaUnitariaComeSommaDiLeM() {
        val white = ConeSpace.toCones(LinearRgb(1.0, 1.0, 1.0))
        assertEquals(1.0, white.l + white.m, 1e-3)
    }

    @Test
    fun iGrigiHannoSempreLeStesseProporzioniTraConi() {
        val a = ConeSpace.toCones(LinearRgb(0.1, 0.1, 0.1))
        val b = ConeSpace.toCones(LinearRgb(0.7, 0.7, 0.7))
        assertEquals(a.l / a.m, b.l / b.m, 1e-9)
        assertEquals(a.s / a.m, b.s / b.m, 1e-9)
    }

    @Test
    fun loStimoloSuAsseMVariaSoloIlConoM() {
        val bg = ConeSpace.toCones(gray)
        val stimulus = ConeSpace.toCones(ConeSpace.stimulusColor(gray, ChromaticAxis.M_ISOLATING, 0.1))

        assertEquals(bg.l, stimulus.l, 1e-9)
        assertEquals(bg.s, stimulus.s, 1e-9)
        assertEquals(bg.m * 1.1, stimulus.m, 1e-9)
    }

    @Test
    fun loStimoloSuAsseSVariaSoloIlConoS() {
        val bg = ConeSpace.toCones(gray)
        val stimulus = ConeSpace.toCones(ConeSpace.stimulusColor(gray, ChromaticAxis.S_ISOLATING, -0.2))

        assertEquals(bg.l, stimulus.l, 1e-9)
        assertEquals(bg.m, stimulus.m, 1e-9)
        assertEquals(bg.s * 0.8, stimulus.s, 1e-9)
    }

    @Test
    fun contrastoZeroRestituisceLoSfondo() {
        for (axis in ChromaticAxis.entries) {
            assertRgbEquals(gray, ConeSpace.stimulusColor(gray, axis, 0.0), 1e-9)
        }
    }

    @Test
    fun contrastiOppostiSiBilanciamoAttornoAlloSfondo() {
        val bg = ConeSpace.toCones(gray)
        val up = ConeSpace.toCones(ConeSpace.stimulusColor(gray, ChromaticAxis.M_ISOLATING, 0.05))
        val down = ConeSpace.toCones(ConeSpace.stimulusColor(gray, ChromaticAxis.M_ISOLATING, -0.05))

        assertEquals(bg.m, (up.m + down.m) / 2, 1e-9)
    }

    @Test
    fun ilContrastoMassimoRestaNelGamutEUnPocoDiPiuNo() {
        val backgrounds = listOf(gray, LinearRgb(ConeSpace.decodeSrgb(0.5), ConeSpace.decodeSrgb(0.5), ConeSpace.decodeSrgb(0.5)))
        for (bg in backgrounds) {
            for (axis in ChromaticAxis.entries) {
                val max = ConeSpace.maxContrast(bg, axis)
                assertTrue("max>0", max > 0.0)
                assertTrue(ConeSpace.stimulusColor(bg, axis, 0.99 * max).isInGamut())
                assertTrue(ConeSpace.stimulusColor(bg, axis, -0.99 * max).isInGamut())
                val outside = !ConeSpace.stimulusColor(bg, axis, 1.01 * max).isInGamut() ||
                    !ConeSpace.stimulusColor(bg, axis, -1.01 * max).isInGamut()
                assertTrue("oltre il massimo almeno un estremo esce dal gamut", outside)
            }
        }
    }

    @Test
    fun ilContrastoMassimoRifiutaUnoSfondoFuoriGamut() {
        assertThrows(IllegalArgumentException::class.java) {
            ConeSpace.maxContrast(LinearRgb(1.2, 0.5, 0.5), ChromaticAxis.M_ISOLATING)
        }
    }

    @Test
    fun scalareLaLuminanzaMoltiplicaTuttiIConi() {
        val bg = ConeSpace.toCones(gray)
        val scaled = ConeSpace.toCones(gray * 2.0)

        assertEquals(bg.l * 2, scaled.l, 1e-9)
        assertEquals(bg.m * 2, scaled.m, 1e-9)
        assertEquals(bg.s * 2, scaled.s, 1e-9)
    }

    @Test
    fun laCodificaSrgbHaIValoriNoti() {
        assertEquals(0.0, ConeSpace.decodeSrgb(0.0), 0.0)
        assertEquals(1.0, ConeSpace.decodeSrgb(1.0), 1e-12)
        assertEquals(0.21404114, ConeSpace.decodeSrgb(0.5), 1e-6)
        for (v in listOf(0.0, 0.02, 0.1, 0.5, 0.9, 1.0)) {
            assertEquals(v, ConeSpace.encodeSrgb(ConeSpace.decodeSrgb(v)), 1e-9)
        }
    }

    @Test
    fun isInGamutDistingueICanaliFuoriIntervallo() {
        assertTrue(LinearRgb(0.0, 0.5, 1.0).isInGamut())
        assertFalse(LinearRgb(-0.01, 0.5, 0.5).isInGamut())
        assertFalse(LinearRgb(0.5, 1.01, 0.5).isInGamut())
    }
}
