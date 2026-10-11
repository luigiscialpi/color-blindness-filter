package com.luigiscialpi.colorblindnessfilter.measure

import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/** Soglia rosso-verde misurata con un dato `β` in una ripetizione della scansione (null se non determinata). */
data class SweepEntry(val beta: Double, val threshold: Double?)

/** Riga di sintesi per un valore di `β`, sulle ripetizioni disponibili. */
data class SweepRow(
    val beta: Double,
    /** Ripetizioni con una soglia determinata. */
    val repetitions: Int,
    val geometricMeanThreshold: Double?,
    /** Di quante volte la soglia con questo `β` è più bassa di quella senza filtro (β = 0); maggiore è meglio. */
    val improvementOverNoFilter: Double?,
)

/**
 * Piano della scansione di `β`: ogni ripetizione misura la soglia rosso-verde a tutti i valori,
 * in un ordine casuale diverso, così l'ordine non si confonde con l'effetto di `β`.
 */
class BetaSweep(private val betas: List<Double>, private val random: Random) {
    init {
        require(betas.isNotEmpty()) { "Serve almeno un valore di beta" }
        require(betas.toSet().size == betas.size) { "I valori di beta non possono ripetersi" }
        require(betas.all { it >= 0.0 && it.isFinite() }) { "I valori di beta devono essere finiti e non negativi" }
    }

    /** Ordine casuale dei valori per una ripetizione. */
    fun shuffledOrder(): List<Double> = betas.shuffled(random)

    companion object {
        /** Sintesi per `β` crescente: media geometrica delle soglie (scala logaritmica, come lo staircase). */
        fun summarize(entries: List<SweepEntry>): List<SweepRow> {
            val rows = entries.groupBy { it.beta }.toSortedMap().map { (beta, group) ->
                val valid = group.mapNotNull { it.threshold }
                SweepRow(
                    beta = beta,
                    repetitions = valid.size,
                    geometricMeanThreshold = if (valid.isEmpty()) null else exp(valid.map { ln(it) }.average()),
                    improvementOverNoFilter = null,
                )
            }
            val baseline = rows.firstOrNull { it.beta == 0.0 }?.geometricMeanThreshold ?: return rows
            return rows.map { row ->
                row.copy(improvementOverNoFilter = row.geometricMeanThreshold?.let { baseline / it })
            }
        }

        fun format(beta: Double): String = String.format(Locale.ROOT, "%.2f", beta)
    }
}
