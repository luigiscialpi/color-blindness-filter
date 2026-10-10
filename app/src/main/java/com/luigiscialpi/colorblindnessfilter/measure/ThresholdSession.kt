package com.luigiscialpi.colorblindnessfilter.measure

import kotlin.math.abs
import kotlin.random.Random

/** Una prova da presentare: asse, contrasto con segno, direzione dell'apertura e se è una prova "catch". */
data class Trial(
    val axis: ChromaticAxis,
    val signedContrast: Double,
    val orientation: Orientation,
    val isCatch: Boolean,
)

/**
 * Una sessione del test a soglie: uno staircase per ogni asse, con le prove degli assi
 * alternate a caso, e una prova "catch" ad alto contrasto ogni [catchEvery] prove per
 * rilevare la disattenzione. Le prove "catch" finiscono nel registro ma non muovono nessuna soglia.
 *
 * Le prove "catch" usano l'asse S, che un deutan vede normalmente, quindi misurano
 * l'attenzione e non il deficit.
 *
 * ponytail: il segno del contrasto è casuale a ogni prova ma non viene registrato nel CSV;
 * se servisse analizzare le due polarità separatamente basta aggiungere una colonna.
 */
class ThresholdSession(
    axes: List<ChromaticAxis>,
    private val background: LinearRgb,
    private val random: Random,
    luminanceJitter: Double,
    private val catchEvery: Int = 8,
    startContrast: Double = 0.3,
    minContrast: Double = 0.002,
) {
    private val maxContrast: Map<ChromaticAxis, Double>
    private val staircases: Map<ChromaticAxis, Staircase>

    val log = TrialLog()

    private var pending: Trial? = null
    private var answered = 0
    private var catchTrials = 0
    private var catchCorrect = 0

    init {
        require(axes.isNotEmpty()) { "Serve almeno un asse" }
        require(axes.toSet().size == axes.size) { "Gli assi non possono ripetersi" }
        require(catchEvery >= 0) { "catchEvery non può essere negativo" }
        val brightest = background * (1 + luminanceJitter)
        require(brightest.isInGamut()) { "Lo sfondo con la massima luminanza deve essere rappresentabile" }
        val limits = (axes + CATCH_AXIS).toSet().associateWith { minOf(ConeSpace.maxContrast(brightest, it), CONTRAST_CAP) }
        maxContrast = limits
        staircases = axes.associateWith { axis ->
            val max = limits.getValue(axis)
            require(minContrast < max) { "Il contrasto minimo supera il massimo rappresentabile per $axis" }
            Staircase(startLevel = minOf(startContrast, max), minLevel = minContrast, maxLevel = max)
        }
    }

    val isFinished: Boolean get() = staircases.values.all { it.isFinished }

    val answeredTrials: Int get() = answered

    /** Prossima prova, oppure null quando tutti gli staircase hanno finito. */
    fun nextTrial(): Trial? {
        check(pending == null) { "La prova precedente non ha ricevuto risposta" }
        if (isFinished) return null
        val orientation = Orientation.entries[random.nextInt(Orientation.entries.size)]
        val sign = if (random.nextBoolean()) 1.0 else -1.0
        val trial = if (isCatchTurn()) {
            Trial(CATCH_AXIS, sign * CATCH_FRACTION * maxContrast.getValue(CATCH_AXIS), orientation, isCatch = true)
        } else {
            val open = staircases.filterValues { !it.isFinished }.keys.toList()
            val axis = open[random.nextInt(open.size)]
            Trial(axis, sign * staircases.getValue(axis).level, orientation, isCatch = false)
        }
        pending = trial
        return trial
    }

    /** Registra la risposta alla prova in corso. */
    fun answer(chosen: Orientation, responseTimeMs: Long, screenBrightness: Double) {
        val trial = checkNotNull(pending) { "Nessuna prova in corso" }
        val correct = chosen == trial.orientation
        if (trial.isCatch) {
            catchTrials++
            if (correct) catchCorrect++
        } else {
            staircases.getValue(trial.axis).record(correct)
        }
        log.add(
            TrialRecord(trial.axis, abs(trial.signedContrast), correct, responseTimeMs, screenBrightness, trial.isCatch),
        )
        answered++
        pending = null
    }

    /** Soglia per asse (contrasto di cono, indice relativo); null se non determinata. */
    fun thresholds(): Map<ChromaticAxis, Double?> = staircases.mapValues { it.value.threshold() }

    /** Quota di risposte corrette alle prove "catch", oppure null se non ce ne sono state. */
    fun catchAccuracy(): Double? = if (catchTrials == 0) null else catchCorrect.toDouble() / catchTrials

    // ponytail: le prove "catch" sono a cadenza fissa (ogni N), non casuali; il partecipante
    // potrebbe prevederle. Per un solo soggetto che conosce il protocollo basta.
    private fun isCatchTurn(): Boolean = catchEvery > 0 && (answered + 1) % catchEvery == 0

    private companion object {
        val CATCH_AXIS = ChromaticAxis.S_ISOLATING
        const val CATCH_FRACTION = 0.9

        /** Oltre il 100% di contrasto di cono lo stimolo perde significato. */
        const val CONTRAST_CAP = 1.0
    }
}
