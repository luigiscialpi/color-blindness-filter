package com.luigiscialpi.colorblindnessfilter.data

import com.luigiscialpi.colorblindnessfilter.measure.ChromaticAxis

/** Tipo di suite di test psicofisico. */
enum class TestSuiteType {
    SWEEP,
    SINGLE_NO_FILTER,
    SINGLE_WITH_FILTER;

    val displayName: String
        get() = when (this) {
            SWEEP -> "Scansione di β"
            SINGLE_NO_FILTER -> "Filtro spento"
            SINGLE_WITH_FILTER -> "Controllo con filtro acceso"
        }
}

/** Specifiche di un blocco non ancora completato, necessarie per riprenderlo. */
data class SavedPendingBlock(
    val label: String,
    val displayLabel: String,
    val beta: Double,
    val axes: List<ChromaticAxis>,
    val isSweep: Boolean = false,
)

/** Risultato consolidato di un blocco di prove completato. */
data class SavedBlockResult(
    val label: String,
    val displayLabel: String,
    val beta: Double,
    val isSweep: Boolean,
    val thresholds: Map<ChromaticAxis, Double?>,
    val catchAccuracy: Double?,
    val csv: String,
)

/**
 * Istantanea dello stato di una suite di test: salva i blocchi già completati e quelli ancora
 * da eseguire, permettendo di riprendere la suite se interrotta, oppure di conservarla nello storico.
 */
data class TestSessionSnapshot(
    val id: String,
    val type: TestSuiteType,
    val title: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val isCompleted: Boolean = false,
    val completedBlocks: List<SavedBlockResult> = emptyList(),
    val remainingBlocks: List<SavedPendingBlock> = emptyList(),
) {
    val totalBlocks: Int
        get() = completedBlocks.size + remainingBlocks.size

    val progressText: String
        get() = "${completedBlocks.size} di $totalBlocks blocchi"
}
