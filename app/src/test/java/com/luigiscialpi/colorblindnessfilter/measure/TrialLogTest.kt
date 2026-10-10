package com.luigiscialpi.colorblindnessfilter.measure

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrialLogTest {

    private val first = TrialRecord(ChromaticAxis.M_ISOLATING, 0.0123456789, true, 812, 1.0, false)
    private val second = TrialRecord(ChromaticAxis.S_ISOLATING, 0.5, false, 1500, 0.75, true)

    @Test
    fun unRegistroVuotoHaSoloLIntestazione() {
        assertEquals("axis,contrast,correct,response_time_ms,screen_brightness,is_catch\n", TrialLog().toCsv())
    }

    @Test
    fun ilCsvHaUnaRigaPerProvaNellOrdineDiInserimento() {
        val log = TrialLog()
        log.add(first)
        log.add(second)

        val expected = "axis,contrast,correct,response_time_ms,screen_brightness,is_catch\n" +
            "M_ISOLATING,0.012346,1,812,1.000,0\n" +
            "S_ISOLATING,0.500000,0,1500,0.750,1\n"
        assertEquals(expected, log.toCsv())
    }

    @Test
    fun ilCsvNonDipendeDalLocalePredefinito() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ITALY)
            val log = TrialLog()
            log.add(first)

            val csv = log.toCsv()
            assertFalse(csv.substringAfter('\n').contains("0,012"))
            assertEquals("M_ISOLATING,0.012346,1,812,1.000,0", csv.lines()[1])
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun recordsRestituisceUnaCopiaNonModificabileDalChiamante() {
        val log = TrialLog()
        log.add(first)

        val snapshot = log.records
        log.add(second)

        assertEquals(listOf(first), snapshot)
        assertEquals(listOf(first, second), log.records)
    }
}
