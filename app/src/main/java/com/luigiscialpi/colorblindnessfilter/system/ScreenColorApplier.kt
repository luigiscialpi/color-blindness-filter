package com.luigiscialpi.colorblindnessfilter.system

import com.luigiscialpi.colorblindnessfilter.domain.ColorTransform

/** Applica una trasformazione colore all'intero schermo. */
interface ScreenColorApplier {
    fun apply(transform: ColorTransform): ApplyResult

    /** Ripristina i colori originali. */
    fun reset(): ApplyResult
}

sealed interface ApplyResult {
    data object Success : ApplyResult

    data object RootUnavailable : ApplyResult

    data class Failed(val message: String) : ApplyResult
}
