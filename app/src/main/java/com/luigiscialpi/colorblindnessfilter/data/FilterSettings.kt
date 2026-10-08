package com.luigiscialpi.colorblindnessfilter.data

import com.luigiscialpi.colorblindnessfilter.domain.LuminanceShiftFilter

/**
 * Impostazioni dell'utente: filtro acceso o spento e intensità `β` della variante a luminanza.
 *
 * L'intensità deve cadere nell'intervallo accettato dal filtro: un valore fuori intervallo
 * non può esistere, quindi chi riceve un [FilterSettings] può fidarsene.
 */
data class FilterSettings(val enabled: Boolean, val intensity: Double) {

    init {
        require(isValidIntensity(intensity)) { "intensity fuori intervallo: $intensity" }
    }

    companion object {
        /** Filtro spento; 0.15 è l'intensità preferita nelle prove sul dispositivo. */
        val DEFAULT = FilterSettings(enabled = false, intensity = 0.15)

        fun isValidIntensity(value: Double): Boolean =
            value in LuminanceShiftFilter.MIN_BETA..LuminanceShiftFilter.MAX_BETA
    }
}
