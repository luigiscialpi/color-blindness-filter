package com.luigiscialpi.colorblindnessfilter.measure

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BetaSweepTest {

    private val betas = listOf(0.0, 0.05, 0.10, 0.15, 0.20, 0.30)

    @Test
    fun ilOrdineEUnaPermutazioneDeiValoriDati() {
        val order = BetaSweep(betas, Random(1)).shuffledOrder()

        assertEquals(betas.sorted(), order.sorted())
        assertEquals(betas.size, order.size)
    }

    @Test
    fun ripetizioniDiverseHannoOrdiniDiversi() {
        val sweep = BetaSweep(betas, Random(2))
        val orders = List(10) { sweep.shuffledOrder() }

        assertTrue("almeno due ordini diversi", orders.toSet().size > 1)
    }

    @Test
    fun conLoStessoSemeLOrdineEIdentico() {
        assertEquals(BetaSweep(betas, Random(5)).shuffledOrder(), BetaSweep(betas, Random(5)).shuffledOrder())
        assertNotEquals(BetaSweep(betas, Random(5)).shuffledOrder(), BetaSweep(betas, Random(6)).shuffledOrder())
    }

    @Test
    fun ognuneDeiValoriPuoAprireLaScansione() {
        val firsts = List(300) { BetaSweep(betas, Random(it)).shuffledOrder().first() }.toSet()

        assertEquals(betas.toSet(), firsts)
    }

    @Test
    fun laSintesiSiOrdinaPerBetaECalcolaLaMediaGeometrica() {
        val rows = BetaSweep.summarize(
            listOf(
                SweepEntry(0.15, 0.04),
                SweepEntry(0.0, 0.10),
                SweepEntry(0.15, 0.01),
                SweepEntry(0.0, 0.40),
            ),
        )

        assertEquals(listOf(0.0, 0.15), rows.map { it.beta })
        assertEquals(0.20, rows[0].geometricMeanThreshold!!, 1e-12)
        assertEquals(0.02, rows[1].geometricMeanThreshold!!, 1e-12)
        assertEquals(listOf(2, 2), rows.map { it.repetitions })
    }

    @Test
    fun ilMiglioramentoEIlRapportoTraLaSogliaSenzaFiltroEQuellaConFiltro() {
        val rows = BetaSweep.summarize(listOf(SweepEntry(0.0, 0.12), SweepEntry(0.10, 0.06), SweepEntry(0.20, 0.03)))

        assertEquals(1.0, rows[0].improvementOverNoFilter!!, 1e-12)
        assertEquals(2.0, rows[1].improvementOverNoFilter!!, 1e-12)
        assertEquals(4.0, rows[2].improvementOverNoFilter!!, 1e-12)
    }

    @Test
    fun leSoglieNonDeterminateSiEscludonoDallaMedia() {
        val rows = BetaSweep.summarize(
            listOf(SweepEntry(0.0, 0.10), SweepEntry(0.0, null), SweepEntry(0.1, null)),
        )

        assertEquals(1, rows[0].repetitions)
        assertEquals(0.10, rows[0].geometricMeanThreshold!!, 1e-12)
        assertEquals(0, rows[1].repetitions)
        assertNull(rows[1].geometricMeanThreshold)
        assertNull(rows[1].improvementOverNoFilter)
    }

    @Test
    fun senzaRiferimentoSenzaFiltroNonCEMiglioramento() {
        val noBaseline = BetaSweep.summarize(listOf(SweepEntry(0.10, 0.05), SweepEntry(0.20, 0.03)))
        assertTrue(noBaseline.all { it.improvementOverNoFilter == null })

        val undeterminedBaseline = BetaSweep.summarize(listOf(SweepEntry(0.0, null), SweepEntry(0.10, 0.05)))
        assertTrue(undeterminedBaseline.all { it.improvementOverNoFilter == null })
    }

    @Test
    fun unaListaVuotaDaUnaSintesiVuota() {
        assertTrue(BetaSweep.summarize(emptyList()).isEmpty())
    }

    @Test
    fun ilFormatoDiBetaUsaIlPuntoDecimale() {
        assertEquals("0.15", BetaSweep.format(0.15))
        assertEquals("0.00", BetaSweep.format(0.0))
    }

    @Test
    fun parametriNonValidiVengonoRifiutati() {
        val random = Random(1)
        assertThrows(IllegalArgumentException::class.java) { BetaSweep(emptyList(), random) }
        assertThrows(IllegalArgumentException::class.java) { BetaSweep(listOf(0.1, 0.1), random) }
        assertThrows(IllegalArgumentException::class.java) { BetaSweep(listOf(-0.1), random) }
        assertThrows(IllegalArgumentException::class.java) { BetaSweep(listOf(Double.NaN), random) }
    }
}
