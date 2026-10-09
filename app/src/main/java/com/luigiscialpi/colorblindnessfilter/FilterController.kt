package com.luigiscialpi.colorblindnessfilter

import com.luigiscialpi.colorblindnessfilter.data.FilterSettings
import com.luigiscialpi.colorblindnessfilter.domain.LuminanceShiftFilter
import com.luigiscialpi.colorblindnessfilter.system.ApplyResult
import com.luigiscialpi.colorblindnessfilter.system.BootScriptWriter
import com.luigiscialpi.colorblindnessfilter.system.ScreenColorApplier

/**
 * Porta lo schermo, e lo script di avvio, nello stato descritto da un [FilterSettings].
 *
 * È volutamente sincrono e senza coroutine: l'osservazione delle impostazioni e il cambio
 * di thread (le chiamate root sono bloccanti) stanno ai bordi, nel ViewModel.
 */
class FilterController(
    private val applier: ScreenColorApplier,
    private val bootScript: BootScriptWriter,
) {

    /**
     * Filtro acceso: applica la matrice e la installa per il prossimo avvio. Spento: ripristina
     * i colori e rimuove lo script. Lo script viene toccato solo se l'applicazione riesce.
     */
    fun apply(settings: FilterSettings): ApplyResult {
        if (!settings.enabled) {
            val reset = applier.reset()
            return if (reset is ApplyResult.Success) bootScript.remove() else reset
        }
        val transform = LuminanceShiftFilter.transform(settings.intensity)
        val applied = applier.apply(transform)
        return if (applied is ApplyResult.Success) bootScript.install(transform) else applied
    }
}
