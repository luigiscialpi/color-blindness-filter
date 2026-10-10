package com.luigiscialpi.colorblindnessfilter.measure

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** Direzione dell'apertura della C di Landolt. */
enum class Orientation(val dx: Double, val dy: Double) {
    UP(0.0, -1.0),
    RIGHT(1.0, 0.0),
    DOWN(0.0, 1.0),
    LEFT(-1.0, 0.0),
}

/** Punto del campo in coordinate normalizzate: (0, 0) in alto a sinistra, (1, 1) in basso a destra. */
data class Dot(val x: Double, val y: Double, val radius: Double, val color: LinearRgb)

/**
 * Genera il campo di punti con una C di Landolt, come nel CAD: la C è definita solo da un
 * contrasto di cono lungo [ChromaticAxis], mentre ogni punto riceve una variazione casuale di
 * luminanza che impedisce di risolvere il compito con la sola luminosità.
 *
 * Le posizioni e le luminanze si estraggono sempre nello stesso ordine: con lo stesso seme,
 * orientamento e contrasto cambiano solo i colori, non la disposizione dei punti.
 */
class LandoltStimulusGenerator(
    private val random: Random,
    private val gridSize: Int = 36,
    /** Ampiezza della variazione di luminanza: ogni punto è moltiplicato per 1 ± questo valore. */
    val luminanceJitter: Double = 0.10,
) {
    init {
        require(gridSize >= MIN_GRID) { "gridSize troppo piccolo: $gridSize" }
        require(luminanceJitter in 0.0..0.5) { "luminanceJitter fuori intervallo: $luminanceJitter" }
    }

    fun generate(
        background: LinearRgb,
        axis: ChromaticAxis,
        signedContrast: Double,
        orientation: Orientation,
    ): List<Dot> {
        val stimulus = ConeSpace.stimulusColor(background, axis, signedContrast)
        val cell = 1.0 / gridSize
        val dots = ArrayList<Dot>(gridSize * gridSize)
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val x = (col + 0.5) * cell + random.nextDouble(-POSITION_JITTER, POSITION_JITTER) * cell
                val y = (row + 0.5) * cell + random.nextDouble(-POSITION_JITTER, POSITION_JITTER) * cell
                val luminance = 1.0 + if (luminanceJitter == 0.0) 0.0 else random.nextDouble(-luminanceJitter, luminanceJitter)
                val base = if (isInLandoltC(x, y, orientation)) stimulus else background
                dots.add(Dot(x, y, DOT_RADIUS * cell, base * luminance))
            }
        }
        return dots
    }

    companion object {
        private const val MIN_GRID = 8
        private const val POSITION_JITTER = 0.1
        private const val DOT_RADIUS = 0.4

        internal const val RING_CENTER = 0.5
        internal const val RING_OUTER = 0.32

        /** Proporzioni standard: tratto e apertura valgono un quinto del diametro esterno. */
        internal const val RING_STROKE = RING_OUTER * 0.4
        internal const val RING_INNER = RING_OUTER - RING_STROKE

        internal fun isInLandoltC(x: Double, y: Double, orientation: Orientation): Boolean {
            val rx = x - RING_CENTER
            val ry = y - RING_CENTER
            val distance = hypot(rx, ry)
            if (distance < RING_INNER || distance > RING_OUTER) return false
            val along = rx * orientation.dx + ry * orientation.dy
            val across = rx * -orientation.dy + ry * orientation.dx
            val inGap = along > 0 && abs(across) < RING_STROKE / 2
            return !inGap
        }
    }
}
