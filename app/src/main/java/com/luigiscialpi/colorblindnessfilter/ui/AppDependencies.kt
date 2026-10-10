package com.luigiscialpi.colorblindnessfilter.ui

import android.content.Context
import com.luigiscialpi.colorblindnessfilter.FilterController
import com.luigiscialpi.colorblindnessfilter.data.SettingsRepository
import com.luigiscialpi.colorblindnessfilter.data.filterSettingsDataStore
import com.luigiscialpi.colorblindnessfilter.system.BootScriptWriter
import com.luigiscialpi.colorblindnessfilter.system.LibsuRootShell
import com.luigiscialpi.colorblindnessfilter.system.ScreenColorApplier
import com.luigiscialpi.colorblindnessfilter.system.SurfaceFlingerColorApplier

/**
 * Cablaggio manuale delle dipendenze, condiviso dalle schermate: pochi oggetti non
 * giustificano un framework di DI. DataStore e shell principale di libsu sono già unici
 * per processo, quindi creare più istanze di questa classe è innocuo.
 */
class AppDependencies(context: Context) {
    private val shell = LibsuRootShell()

    val repository = SettingsRepository(context.applicationContext.filterSettingsDataStore)
    val applier: ScreenColorApplier = SurfaceFlingerColorApplier(shell)
    val controller = FilterController(applier, BootScriptWriter(shell))
}
