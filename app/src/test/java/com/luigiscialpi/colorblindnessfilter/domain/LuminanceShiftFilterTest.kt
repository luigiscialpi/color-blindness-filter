package com.luigiscialpi.colorblindnessfilter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LuminanceShiftFilterTest {

    @Test
    fun conBetaZeroLaMatriceEIdentita() {
        assertEquals(ColorTransform.IDENTITY, LuminanceShiftFilter.transform(0.0))
        assertEquals(
            ColorTransform.IDENTITY.toSurfaceFlingerArgs(),
            LuminanceShiftFilter.transform(0.0).toSurfaceFlingerArgs(),
        )
    }

    @Test
    fun ogniRigaSommaAUnoQuindiINeutriRestanoInvariati() {
        for (beta in listOf(0.0, 0.15, 0.3, 0.5)) {
            val t = LuminanceShiftFilter.transform(beta)
            for (row in 0..2) {
                val sum = (0..2).sumOf { col -> t[row, col] }
                assertEquals("beta=$beta riga=$row", 1.0, sum, 1e-12)
            }
        }
    }

    @Test
    fun conBeta015LaSequenzaCoincideConQuellaProvataSulDispositivo() {
        val expected = "f 1.150000 f 0.150000 f 0.150000 f 0.000000 " +
            "f -0.150000 f 0.850000 f -0.150000 f 0.000000 " +
            "f 0.000000 f 0.000000 f 1.000000 f 0.000000 " +
            "f 0.000000 f 0.000000 f 0.000000 f 1.000000"
        assertEquals(expected, LuminanceShiftFilter.transform(0.15).toSurfaceFlingerArgs())
    }

    @Test
    fun ilRossoPuroSiSchiaraEIlVerdePuroSiScurisce() {
        val t = LuminanceShiftFilter.transform(0.3)
        // Colonna 0 = effetto di R in ingresso: R' = 1.3, G' = 0.3, B' = 0.3.
        assertEquals(1.3, t[0, 0], 1e-12)
        assertEquals(0.3, t[1, 0], 1e-12)
        assertEquals(0.3, t[2, 0], 1e-12)
        // Colonna 1 = effetto di G in ingresso: R' = -0.3, G' = 0.7, B' = -0.3.
        assertEquals(-0.3, t[0, 1], 1e-12)
        assertEquals(0.7, t[1, 1], 1e-12)
        assertEquals(-0.3, t[2, 1], 1e-12)
    }

    @Test
    fun accettaGliEstremiDellIntervallo() {
        LuminanceShiftFilter.transform(LuminanceShiftFilter.MIN_BETA)
        LuminanceShiftFilter.transform(LuminanceShiftFilter.MAX_BETA)
    }

    @Test
    fun rifiutaBetaFuoriIntervalloONonFinito() {
        val bad = listOf(-0.01, 0.51, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        for (beta in bad) {
            assertThrows(IllegalArgumentException::class.java) { LuminanceShiftFilter.transform(beta) }
        }
    }
}
