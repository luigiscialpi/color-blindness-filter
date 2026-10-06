package com.luigiscialpi.colorfilter.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTransformTest {

    private fun values(transform: ColorTransform): List<Double> =
        transform.toSurfaceFlingerArgs().split(" ").chunked(2).map { it[1].toDouble() }

    @Test
    fun identitaProduceLaMatriceIdentita() {
        val expected = "f 1.000000 f 0.000000 f 0.000000 f 0.000000 " +
            "f 0.000000 f 1.000000 f 0.000000 f 0.000000 " +
            "f 0.000000 f 0.000000 f 1.000000 f 0.000000 " +
            "f 0.000000 f 0.000000 f 0.000000 f 1.000000"
        assertEquals(expected, ColorTransform.IDENTITY.toSurfaceFlingerArgs())
    }

    @Test
    fun matriceAsimmetricaViaggiaPerColonne() {
        // R' = G, G' = G, B' = B: e' il test `asym` eseguito sul dispositivo.
        val asym = ColorTransform(
            listOf(
                listOf(0.0, 1.0, 0.0),
                listOf(0.0, 1.0, 0.0),
                listOf(0.0, 0.0, 1.0),
            ),
        )
        val expected = "f 0.000000 f 0.000000 f 0.000000 f 0.000000 " +
            "f 1.000000 f 1.000000 f 0.000000 f 0.000000 " +
            "f 0.000000 f 0.000000 f 1.000000 f 0.000000 " +
            "f 0.000000 f 0.000000 f 0.000000 f 1.000000"
        assertEquals(expected, asym.toSurfaceFlingerArgs())
    }

    @Test
    fun produceSedicivaloriConUltimaRigaZeroZeroZeroUno() {
        val transform = ColorTransform(
            listOf(
                listOf(1.3, -0.3, 0.0),
                listOf(0.3, 0.7, 0.0),
                listOf(0.3, -0.3, 1.0),
            ),
        )
        val v = values(transform)
        assertEquals(16, v.size)
        assertEquals(listOf(0.0, 0.0, 0.0, 1.0), listOf(v[3], v[7], v[11], v[15]))
    }

    @Test
    fun laFormattazioneNonDipendeDalLocalePredefinito() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ITALY)
            val transform = ColorTransform(
                listOf(
                    listOf(1.15, -0.15, 0.0),
                    listOf(0.15, 0.85, 0.0),
                    listOf(0.15, -0.15, 1.0),
                ),
            )
            val args = transform.toSurfaceFlingerArgs()
            assertFalse(args.contains(','))
            assertTrue(args.contains("f 1.150000"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun nonScriveMaiZeroNegativo() {
        val transform = ColorTransform(
            listOf(
                listOf(1.0, -0.0, -1e-9),
                listOf(0.0, 1.0, 0.0),
                listOf(0.0, 0.0, 1.0),
            ),
        )
        assertFalse(transform.toSurfaceFlingerArgs().contains("-0.000000"))
    }

    @Test
    fun menoZeroEZeroSonoUguali() {
        val withNegativeZero = ColorTransform(
            listOf(listOf(1.0, -0.0, 0.0), listOf(0.0, 1.0, 0.0), listOf(0.0, 0.0, 1.0)),
        )
        assertEquals(ColorTransform.IDENTITY, withNegativeZero)
        assertEquals(ColorTransform.IDENTITY.hashCode(), withNegativeZero.hashCode())
    }

    @Test
    fun rifiutaMatriciNonTrePerTre() {
        assertThrows(IllegalArgumentException::class.java) {
            ColorTransform(listOf(listOf(1.0, 0.0), listOf(0.0, 1.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ColorTransform(listOf(listOf(1.0, 0.0, 0.0), listOf(0.0, 1.0, 0.0)))
        }
    }

    @Test
    fun rifiutaCoefficientiNonFiniti() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                ColorTransform(
                    listOf(listOf(bad, 0.0, 0.0), listOf(0.0, 1.0, 0.0), listOf(0.0, 0.0, 1.0)),
                )
            }
        }
    }

    @Test
    fun getRifiutaIndiciFuoriIntervallo() {
        assertThrows(IllegalArgumentException::class.java) { ColorTransform.IDENTITY[3, 0] }
        assertThrows(IllegalArgumentException::class.java) { ColorTransform.IDENTITY[0, -1] }
    }
}
