package com.luigiscialpi.colorblindnessfilter.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luigiscialpi.colorblindnessfilter.FilterController
import com.luigiscialpi.colorblindnessfilter.data.FilterSettings
import com.luigiscialpi.colorblindnessfilter.data.SettingsRepository
import com.luigiscialpi.colorblindnessfilter.domain.LuminanceShiftFilter
import com.luigiscialpi.colorblindnessfilter.measure.BetaSweep
import com.luigiscialpi.colorblindnessfilter.measure.ChromaticAxis
import com.luigiscialpi.colorblindnessfilter.measure.ConeSpace
import com.luigiscialpi.colorblindnessfilter.measure.Dot
import com.luigiscialpi.colorblindnessfilter.measure.LandoltStimulusGenerator
import com.luigiscialpi.colorblindnessfilter.measure.LinearRgb
import com.luigiscialpi.colorblindnessfilter.measure.Orientation
import com.luigiscialpi.colorblindnessfilter.measure.SweepEntry
import com.luigiscialpi.colorblindnessfilter.measure.ThresholdSession
import com.luigiscialpi.colorblindnessfilter.measure.Trial
import com.luigiscialpi.colorblindnessfilter.system.ApplyResult
import com.luigiscialpi.colorblindnessfilter.system.ScreenColorApplier
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sfondo grigio medio (sRGB al 50%) del campo di punti. */
internal val TEST_BACKGROUND = LinearRgb(
    ConeSpace.decodeSrgb(0.5),
    ConeSpace.decodeSrgb(0.5),
    ConeSpace.decodeSrgb(0.5),
)

sealed interface ThresholdTestState {
    data object Intro : ThresholdTestState

    data object Preparing : ThresholdTestState

    data class Running(val dots: List<Dot>, val answered: Int, val blockLabel: String) : ThresholdTestState

    data class Finished(val summary: String) : ThresholdTestState

    data class Failed(val message: String) : ThresholdTestState
}

/**
 * Un blocco di prove con una condizione di filtro. [beta] = 0 significa filtro azzerato;
 * [displayLabel] è il testo mostrato durante le prove (nella scansione non rivela `β`).
 */
private data class BlockSpec(
    val label: String,
    val displayLabel: String,
    val beta: Double,
    val axes: List<ChromaticAxis>,
    val isSweep: Boolean = false,
)

private data class BlockResult(
    val label: String,
    val thresholds: Map<ChromaticAxis, Double?>,
    val catchAccuracy: Double?,
    val csv: String,
    val isSweep: Boolean,
)

/**
 * Conduce il test a soglie: porta lo schermo nella condizione di ogni blocco (filtro azzerato,
 * acceso con l'intensità salvata, oppure con un `β` della scansione), presenta le prove di
 * [ThresholdSession], raccoglie le risposte e a fine lavoro ripristina le impostazioni dell'utente.
 */
class ThresholdTestViewModel(
    private val applier: ScreenColorApplier,
    private val controller: FilterController,
    private val repository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<ThresholdTestState>(ThresholdTestState.Intro)
    val state: StateFlow<ThresholdTestState> = _state.asStateFlow()

    private val random = Random(System.nanoTime())
    private val pending = ArrayDeque<BlockSpec>()
    private val blocks = mutableListOf<BlockResult>()
    private val sweepEntries = mutableListOf<SweepEntry>()
    private var currentBlock: BlockSpec? = null
    private var session: ThresholdSession? = null
    private var generator: LandoltStimulusGenerator? = null
    private var trial: Trial? = null
    private var presentedAtMs = 0L
    private var savedSettings: FilterSettings? = null
    private var restored = true

    // ponytail: ambito separato e mai annullato, perché il ripristino in onCleared deve
    // concludersi anche quando viewModelScope è già stato annullato. Se il processo viene
    // ucciso a metà test, il filtro resta nella condizione dell'ultimo blocco finché non lo si
    // riaccende o spegne dalla schermata principale.
    private val restoreScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** Un blocco: filtro spento (rosso-verde e giallo-blu) oppure controllo con il filtro acceso (solo rosso-verde). */
    fun start(withFilter: Boolean) {
        launchBlocks { settings ->
            if (withFilter) {
                val label = "controllo con filtro acceso (beta ${BetaSweep.format(settings.intensity)})"
                listOf(BlockSpec(label, "controllo con filtro acceso", settings.intensity, listOf(ChromaticAxis.M_ISOLATING)))
            } else {
                listOf(BlockSpec("filtro spento", "filtro spento", 0.0, ChromaticAxis.entries))
            }
        }
    }

    /**
     * Una ripetizione della scansione: la soglia rosso-verde a ogni `β`, in ordine casuale. Durante le
     * prove non si vede quale `β` è attivo; i valori si rivelano nei risultati.
     */
    fun startSweep() {
        launchBlocks {
            val order = BetaSweep(SWEEP_BETAS, random).shuffledOrder()
            order.mapIndexed { index, beta ->
                BlockSpec(
                    label = "scansione beta ${BetaSweep.format(beta)}",
                    displayLabel = "scansione: blocco ${index + 1} di ${order.size}",
                    beta = beta,
                    axes = listOf(ChromaticAxis.M_ISOLATING),
                    isSweep = true,
                )
            }
        }
    }

    fun onAnswer(chosen: Orientation) {
        val current = session ?: return
        if (trial == null) return
        current.answer(chosen, SystemClock.elapsedRealtime() - presentedAtMs, SCREEN_BRIGHTNESS)
        trial = null
        showNextTrial()
    }

    /** Testo con i registri CSV di tutti i blocchi eseguiti, ciascuno preceduto dall'etichetta. */
    fun exportText(): String = blocks.joinToString(separator = "\n") { "# ${it.label}\n${it.csv}" }

    private fun launchBlocks(plan: (FilterSettings) -> List<BlockSpec>) {
        _state.value = ThresholdTestState.Preparing
        viewModelScope.launch {
            val settings = savedSettings ?: repository.settings.first().also { savedSettings = it }
            pending.clear()
            pending.addAll(plan(settings))
            restored = false
            runNextBlock()
        }
    }

    private suspend fun runNextBlock() {
        val spec = pending.removeFirst()
        val result = withContext(ioDispatcher) {
            if (spec.beta == 0.0) applier.reset() else applier.apply(LuminanceShiftFilter.transform(spec.beta))
        }
        if (result !is ApplyResult.Success) {
            pending.clear()
            restoreSettings()
            _state.value = ThresholdTestState.Failed(describeStartFailure(result))
            return
        }
        currentBlock = spec
        session = ThresholdSession(spec.axes, TEST_BACKGROUND, random, LUMINANCE_JITTER)
        generator = LandoltStimulusGenerator(random, luminanceJitter = LUMINANCE_JITTER)
        showNextTrial()
    }

    private fun showNextTrial() {
        val current = checkNotNull(session)
        val next = current.nextTrial()
        if (next == null) {
            finishBlock()
            return
        }
        trial = next
        val dots = checkNotNull(generator).generate(TEST_BACKGROUND, next.axis, next.signedContrast, next.orientation)
        presentedAtMs = SystemClock.elapsedRealtime()
        _state.value = ThresholdTestState.Running(dots, current.answeredTrials, checkNotNull(currentBlock).displayLabel)
    }

    private fun finishBlock() {
        val spec = checkNotNull(currentBlock)
        val current = checkNotNull(session)
        val thresholds = current.thresholds()
        blocks.add(BlockResult(spec.label, thresholds, current.catchAccuracy(), current.log.toCsv(), spec.isSweep))
        if (spec.isSweep) sweepEntries.add(SweepEntry(spec.beta, thresholds[ChromaticAxis.M_ISOLATING]))
        currentBlock = null
        session = null
        generator = null
        _state.value = ThresholdTestState.Preparing
        viewModelScope.launch {
            if (pending.isNotEmpty()) {
                runNextBlock()
                return@launch
            }
            val restore = restoreSettings()
            _state.value = ThresholdTestState.Finished(summary(restore))
        }
    }

    private suspend fun restoreSettings(): ApplyResult? {
        val settings = savedSettings ?: return null
        val result = withContext(ioDispatcher) { controller.apply(settings) }
        restored = true
        return result
    }

    override fun onCleared() {
        val settings = savedSettings
        if (!restored && settings != null) {
            restoreScope.launch { controller.apply(settings) }
        }
    }

    private fun describeStartFailure(result: ApplyResult): String = when (result) {
        ApplyResult.RootUnavailable -> "Root non disponibile: non si può impostare il filtro, quindi il test non parte."
        is ApplyResult.Failed -> "Comando non riuscito: ${result.message}"
        ApplyResult.Success -> "Errore inatteso"
    }

    private fun summary(restore: ApplyResult?): String = buildString {
        for (block in blocks.filterNot { it.isSweep }) {
            appendLine("Blocco: ${block.label}")
            for ((axis, threshold) in block.thresholds) {
                appendLine("  ${axisName(axis)}: ${formatThreshold(threshold)}")
            }
            val catches = block.catchAccuracy?.let { String.format(Locale.getDefault(), "%.0f%%", it * 100) } ?: "nessuna"
            appendLine("  Prove catch corrette: $catches")
            appendLine()
        }
        if (sweepEntries.isNotEmpty()) appendSweepSummary()
        appendLine("Indice relativo: il display non è calibrato, quindi non è un contrasto assoluto.")
        if (restore != null && restore !is ApplyResult.Success) {
            appendLine()
            appendLine("Attenzione: il ripristino delle impostazioni non è riuscito. Riaccendi il filtro dalla schermata principale.")
        }
    }

    private fun StringBuilder.appendSweepSummary() {
        appendLine("Scansione di beta (soglia rosso-verde, minore è meglio)")
        for (row in BetaSweep.summarize(sweepEntries)) {
            val mean = formatThreshold(row.geometricMeanThreshold)
            val gain = row.improvementOverNoFilter?.let { String.format(Locale.getDefault(), ", %.1f volte meglio del riferimento", it) } ?: ""
            appendLine("  beta ${BetaSweep.format(row.beta)}: $mean (${row.repetitions} ripetizioni)$gain")
        }
        appendLine()
    }

    private fun axisName(axis: ChromaticAxis): String = when (axis) {
        ChromaticAxis.M_ISOLATING -> "Rosso-verde (cono M)"
        ChromaticAxis.S_ISOLATING -> "Giallo-blu (controllo, cono S)"
    }

    private fun formatThreshold(threshold: Double?): String =
        if (threshold == null) {
            "non determinata"
        } else {
            String.format(Locale.getDefault(), "%.1f%% di contrasto di cono", threshold * 100)
        }

    private companion object {
        const val LUMINANCE_JITTER = 0.10

        /** Luminosità della finestra durante il test (1.0 = massima); il valore reale dipende dal pannello. */
        const val SCREEN_BRIGHTNESS = 1.0

        /** Valori di `β` della scansione: 0 è il riferimento senza filtro. */
        val SWEEP_BETAS = listOf(0.0, 0.05, 0.10, 0.15, 0.20, 0.30)
    }
}
