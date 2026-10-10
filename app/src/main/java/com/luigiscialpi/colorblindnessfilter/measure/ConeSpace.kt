package com.luigiscialpi.colorblindnessfilter.measure

import kotlin.math.abs
import kotlin.math.pow

/** Colore in RGB lineare (senza gamma), con canali nominalmente tra 0 e 1. */
data class LinearRgb(val r: Double, val g: Double, val b: Double) {

    operator fun times(factor: Double): LinearRgb = LinearRgb(r * factor, g * factor, b * factor)

    /** Vero se il colore è rappresentabile sullo schermo (canali tra 0 e 1, con una tolleranza minima). */
    fun isInGamut(tolerance: Double = 1e-9): Boolean =
        r in -tolerance..1 + tolerance && g in -tolerance..1 + tolerance && b in -tolerance..1 + tolerance
}

/** Eccitazione dei tre tipi di coni (lunghi, medi, corti). */
data class ConeExcitation(val l: Double, val m: Double, val s: Double)

/** Direzione cromatica di uno stimolo, espressa come cono che varia mentre gli altri restano fissi. */
enum class ChromaticAxis {
    /** Varia solo il cono M: è la linea di confusione dei deutan, invisibile a chi non lo ha. */
    M_ISOLATING,

    /** Varia solo il cono S: direzione di controllo (tritan), che un deutan vede normalmente. */
    S_ISOLATING,
}

/**
 * Conversione tra RGB dello schermo ed eccitazione dei coni, e costruzione di stimoli a
 * contrasto di cono.
 *
 * ponytail: i fondamentali sono quelli di Smith-Pokorny (usati nel diagramma di MacLeod-Boynton),
 * applicati a XYZ CIE 1931 con primari sRGB. Non esiste una matrice esatta da XYZ a LMS: per
 * soglie relative sullo stesso telefono basta; i fondamentali di Stockman-Sharpe e i primari
 * misurati del display sono il passo successivo se serve più precisione.
 */
object ConeSpace {

    private val SRGB_TO_XYZ = arrayOf(
        doubleArrayOf(0.4124564, 0.3575761, 0.1804375),
        doubleArrayOf(0.2126729, 0.7151522, 0.0721750),
        doubleArrayOf(0.0193339, 0.1191920, 0.9503041),
    )

    private val XYZ_TO_LMS = arrayOf(
        doubleArrayOf(0.15514, 0.54312, -0.03286),
        doubleArrayOf(-0.15514, 0.45684, 0.03286),
        doubleArrayOf(0.0, 0.0, 0.01608),
    )

    private val RGB_TO_LMS = multiply(XYZ_TO_LMS, SRGB_TO_XYZ)
    private val LMS_TO_RGB = invert(RGB_TO_LMS)

    /** Limite della ricerca del contrasto massimo: oltre questo valore lo stimolo non ha più senso. */
    private const val CONTRAST_SEARCH_LIMIT = 10.0

    fun toCones(rgb: LinearRgb): ConeExcitation {
        val v = apply(RGB_TO_LMS, doubleArrayOf(rgb.r, rgb.g, rgb.b))
        return ConeExcitation(v[0], v[1], v[2])
    }

    fun toLinearRgb(cones: ConeExcitation): LinearRgb {
        val v = apply(LMS_TO_RGB, doubleArrayOf(cones.l, cones.m, cones.s))
        return LinearRgb(v[0], v[1], v[2])
    }

    /**
     * Colore che si ottiene da [background] variando di [contrast] (positivo o negativo) il cono
     * dell'asse scelto, in proporzione alla sua eccitazione sullo sfondo; gli altri coni restano fissi.
     */
    fun stimulusColor(background: LinearRgb, axis: ChromaticAxis, contrast: Double): LinearRgb {
        val bg = toCones(background)
        val target = when (axis) {
            ChromaticAxis.M_ISOLATING -> bg.copy(m = bg.m * (1 + contrast))
            ChromaticAxis.S_ISOLATING -> bg.copy(s = bg.s * (1 + contrast))
        }
        return toLinearRgb(target)
    }

    /** Contrasto massimo per cui sia +contrasto sia −contrasto restano rappresentabili sullo schermo. */
    fun maxContrast(background: LinearRgb, axis: ChromaticAxis): Double {
        require(background.isInGamut()) { "Lo sfondo deve essere rappresentabile: $background" }
        return minOf(
            largestInGamut(background, axis, direction = 1.0),
            largestInGamut(background, axis, direction = -1.0),
        )
    }

    // Le regioni rappresentabili sono convesse (immagine lineare di un cubo) e contengono lo
    // sfondo, quindi lungo la retta dello stimolo basta una bisezione.
    private fun largestInGamut(background: LinearRgb, axis: ChromaticAxis, direction: Double): Double {
        var low = 0.0
        var high = CONTRAST_SEARCH_LIMIT
        if (stimulusColor(background, axis, direction * high).isInGamut()) return high
        repeat(60) {
            val mid = (low + high) / 2
            if (stimulusColor(background, axis, direction * mid).isInGamut()) low = mid else high = mid
        }
        return low
    }

    /** Da valore sRGB codificato (0..1) a lineare. */
    fun decodeSrgb(value: Double): Double =
        if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    /** Da lineare a valore sRGB codificato (0..1). */
    fun encodeSrgb(value: Double): Double =
        if (value <= 0.0031308) value * 12.92 else 1.055 * value.pow(1 / 2.4) - 0.055

    private fun apply(m: Array<DoubleArray>, v: DoubleArray): DoubleArray =
        DoubleArray(3) { i -> m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] }

    private fun multiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
        Array(3) { i -> DoubleArray(3) { j -> (0..2).sumOf { k -> a[i][k] * b[k][j] } } }

    private fun invert(m: Array<DoubleArray>): Array<DoubleArray> {
        val det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
            m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
            m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
        require(abs(det) > 1e-12) { "Matrice non invertibile" }
        fun cofactor(r: Int, c: Int): Double {
            val rows = (0..2).filter { it != r }
            val cols = (0..2).filter { it != c }
            val minor = m[rows[0]][cols[0]] * m[rows[1]][cols[1]] - m[rows[0]][cols[1]] * m[rows[1]][cols[0]]
            return if ((r + c) % 2 == 0) minor else -minor
        }
        // inversa = trasposta della matrice dei cofattori divisa per il determinante
        return Array(3) { i -> DoubleArray(3) { j -> cofactor(j, i) / det } }
    }
}
