package com.luigiscialpi.colorblindnessfilter.domain

/**
 * Variante a luminanza: `C = I + β · 1 · [1, −1, 0]`.
 *
 * Aggiunge a tutti e tre i canali una quantità proporzionale a (R − G): i rossi si
 * schiariscono, i verdi si scuriscono, la tinta cambia poco. Le righe sommano a 1,
 * quindi i neutri (grigi e bianco) restano invariati.
 */
object LuminanceShiftFilter {

    const val MIN_BETA = 0.0

    /** Limite superiore provvisorio: 0.5 è l'intensità giudicata troppo forte nelle prove. */
    const val MAX_BETA = 0.5

    fun transform(beta: Double): ColorTransform {
        require(beta in MIN_BETA..MAX_BETA) { "beta deve essere in [$MIN_BETA, $MAX_BETA]: $beta" }
        return ColorTransform(
            listOf(
                listOf(1 + beta, -beta, 0.0),
                listOf(beta, 1 - beta, 0.0),
                listOf(beta, -beta, 1.0),
            ),
        )
    }
}
