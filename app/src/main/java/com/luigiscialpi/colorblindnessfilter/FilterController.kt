package com.luigiscialpi.colorblindnessfilter

import com.luigiscialpi.colorblindnessfilter.data.FilterSettings
import com.luigiscialpi.colorblindnessfilter.domain.LuminanceShiftFilter
import com.luigiscialpi.colorblindnessfilter.system.ApplyResult
import com.luigiscialpi.colorblindnessfilter.system.ScreenColorApplier

/**
 * Porta lo schermo nello stato descritto da un [FilterSettings].
 *
 * È volutamente sincrono e senza coroutine: l'osservazione delle impostazioni e il cambio
 * di thread (le chiamate all'applier sono bloccanti) stanno ai bordi, nel ViewModel.
 */
class FilterController(private val applier: ScreenColorApplier) {

    /** Filtro acceso: applica la matrice dell'intensità scelta. Spento: ripristina i colori. */
    fun apply(settings: FilterSettings): ApplyResult =
        if (settings.enabled) {
            applier.apply(LuminanceShiftFilter.transform(settings.intensity))
        } else {
            applier.reset()
        }
}
