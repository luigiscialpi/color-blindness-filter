package com.luigiscialpi.colorblindnessfilter.domain

import java.util.Locale
import kotlin.math.abs

/**
 * Matrice colore 3x3 immutabile.
 *
 * Ogni riga esprime un canale di uscita (R', G', B') in funzione dei canali di
 * ingresso (R, G, B). Il tipo non dipende da Android: sa solo produrre gli argomenti
 * numerici che SurfaceFlinger si aspetta (vedi [toSurfaceFlingerArgs]).
 */
class ColorTransform(rows: List<List<Double>>) {

    private val values: List<Double>

    init {
        require(rows.size == SIZE && rows.all { it.size == SIZE }) { "La matrice deve essere 3x3" }
        val flat = rows.flatten()
        require(flat.all { it.isFinite() }) { "I coefficienti devono essere numeri finiti" }
        // `+ 0.0` converte -0.0 in 0.0: uguaglianza e formattazione restano stabili.
        values = flat.map { it + 0.0 }
    }

    /** Coefficiente alla riga [row] (canale di uscita) e colonna [col] (canale di ingresso). */
    operator fun get(row: Int, col: Int): Double {
        require(row in 0 until SIZE && col in 0 until SIZE) { "Indici fuori intervallo: ($row, $col)" }
        return values[row * SIZE + col]
    }

    /**
     * Argomenti float per `service call SurfaceFlinger 1015 i32 1 <argomenti>`.
     *
     * SurfaceFlinger legge una mat4 in ordine column-major e richiede l'ultima riga
     * (0, 0, 0, 1). I numeri usano sempre il punto decimale, indipendentemente dal locale.
     */
    fun toSurfaceFlingerArgs(): String {
        val mat4 = mutableListOf<Double>()
        for (col in 0 until SIZE) {
            for (row in 0 until SIZE) mat4.add(this[row, col])
            mat4.add(0.0)
        }
        mat4.addAll(listOf(0.0, 0.0, 0.0, 1.0))
        return mat4.joinToString(" ") { "f ${format(it)}" }
    }

    override fun equals(other: Any?): Boolean = other is ColorTransform && values == other.values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "ColorTransform($values)"

    private fun format(value: Double): String {
        // Un valore che si arrotonda a zero viene scritto "0.000000", mai "-0.000000".
        val safe = if (abs(value) < HALF_LAST_DIGIT) 0.0 else value
        return String.format(Locale.ROOT, "%.6f", safe)
    }

    companion object {
        private const val SIZE = 3
        private const val HALF_LAST_DIGIT = 0.5e-6

        val IDENTITY = ColorTransform(
            listOf(
                listOf(1.0, 0.0, 0.0),
                listOf(0.0, 1.0, 0.0),
                listOf(0.0, 0.0, 1.0),
            ),
        )
    }
}
