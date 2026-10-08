package com.luigiscialpi.colorblindnessfilter.ui

import com.luigiscialpi.colorblindnessfilter.data.FilterSettings
import com.luigiscialpi.colorblindnessfilter.system.ApplyResult

data class FilterUiState(
    val settings: FilterSettings = FilterSettings.DEFAULT,
    /** Esito dell'ultima applicazione al sistema; null finché non è stato applicato nulla. */
    val lastResult: ApplyResult? = null,
)
