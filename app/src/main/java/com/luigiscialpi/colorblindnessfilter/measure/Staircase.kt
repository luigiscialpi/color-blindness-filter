package com.luigiscialpi.colorblindnessfilter.measure

import kotlin.math.exp
import kotlin.math.ln

/**
 * Procedura adattiva a scalini: tre risposte corrette consecutive rendono lo stimolo più
 * difficile (livello più basso), un errore lo rende più facile. Con passi uguali in scala
 * logaritmica converge al livello con circa il 79,4% di risposte corrette.
 *
 * Il livello è un contrasto positivo e viene sempre tenuto tra [minLevel] e [maxLevel].
 * Le prove "catch" non passano da qui: non devono muovere la soglia.
 */
class Staircase(
    startLevel: Double,
    private val minLevel: Double,
    private val maxLevel: Double,
    private val stepFactor: Double = 1.25,
    private val targetReversals: Int = 10,
    private val averagedReversals: Int = 6,
    private val maxTrials: Int = 200,
) {
    init {
        require(minLevel > 0.0 && minLevel < maxLevel) { "Intervallo dei livelli non valido" }
        require(startLevel in minLevel..maxLevel) { "Il livello iniziale deve stare nell'intervallo" }
        require(stepFactor > 1.0) { "Il fattore di passo deve essere maggiore di 1" }
        require(averagedReversals in 1..targetReversals) { "Inversioni da mediare non valide" }
        require(maxTrials > 0) { "maxTrials deve essere positivo" }
    }

    /** Livello (contrasto) da presentare nella prossima prova. */
    var level: Double = startLevel
        private set

    var trials: Int = 0
        private set

    private var correctStreak = 0
    private var lastDirection = 0
    private val reversalLevels = mutableListOf<Double>()

    val reversals: Int get() = reversalLevels.size

    /** Finita per inversioni raggiunte oppure per limite di prove (stimolo mai visto o sempre visto). */
    val isFinished: Boolean get() = reversals >= targetReversals || trials >= maxTrials

    fun record(correct: Boolean) {
        check(!isFinished) { "La procedura è già terminata" }
        trials++
        if (correct) {
            correctStreak++
            if (correctStreak == CORRECT_TO_DESCEND) {
                correctStreak = 0
                move(direction = -1)
            }
        } else {
            correctStreak = 0
            move(direction = +1)
        }
    }

    /**
     * Media geometrica degli ultimi livelli di inversione, oppure null se non ce ne sono
     * abbastanza (stimolo mai visto anche al massimo, o sempre visto anche al minimo).
     */
    fun threshold(): Double? {
        if (reversals < averagedReversals) return null
        val last = reversalLevels.takeLast(averagedReversals)
        return exp(last.map { ln(it) }.average())
    }

    private fun move(direction: Int) {
        if (lastDirection != 0 && direction != lastDirection) reversalLevels.add(level)
        lastDirection = direction
        val next = if (direction > 0) level * stepFactor else level / stepFactor
        level = next.coerceIn(minLevel, maxLevel)
    }

    private companion object {
        const val CORRECT_TO_DESCEND = 3
    }
}
