package com.luigiscialpi.colorblindnessfilter.measure

import java.util.Locale

/** Una prova del test a soglie. */
data class TrialRecord(
    val axis: ChromaticAxis,
    val contrast: Double,
    val correct: Boolean,
    val responseTimeMs: Long,
    val screenBrightness: Double,
    val isCatch: Boolean,
)

/**
 * Registro delle prove di una sessione, esportabile in CSV: una riga per prova, nell'ordine
 * in cui sono state eseguite.
 */
class TrialLog {

    private val entries = mutableListOf<TrialRecord>()

    val records: List<TrialRecord> get() = entries.toList()

    fun add(record: TrialRecord) {
        entries.add(record)
    }

    /** Intestazione più una riga per prova; numeri sempre con il punto decimale, righe separate da `\n`. */
    fun toCsv(): String = (listOf(HEADER) + entries.map(::row)).joinToString(separator = "\n", postfix = "\n")

    private fun row(record: TrialRecord): String = listOf(
        record.axis.name,
        String.format(Locale.ROOT, "%.6f", record.contrast),
        if (record.correct) "1" else "0",
        record.responseTimeMs.toString(),
        String.format(Locale.ROOT, "%.3f", record.screenBrightness),
        if (record.isCatch) "1" else "0",
    ).joinToString(",")

    private companion object {
        const val HEADER = "axis,contrast,correct,response_time_ms,screen_brightness,is_catch"
    }
}
