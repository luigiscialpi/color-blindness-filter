package com.luigiscialpi.colorblindnessfilter.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luigiscialpi.colorblindnessfilter.FilterController
import com.luigiscialpi.colorblindnessfilter.data.FilterSettings
import com.luigiscialpi.colorblindnessfilter.data.SettingsRepository
import com.luigiscialpi.colorblindnessfilter.domain.LuminanceShiftFilter
import com.luigiscialpi.colorblindnessfilter.measure.ChromaticAxis
import com.luigiscialpi.colorblindnessfilter.measure.ConeSpace
import com.luigiscialpi.colorblindnessfilter.measure.Dot
import com.luigiscialpi.colorblindnessfilter.measure.LandoltStimulusGenerator
import com.luigiscialpi.colorblindnessfilter.measure.LinearRgb
import com.luigiscialpi.colorblindnessfilter.measure.Orientation
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

private data class BlockResult(
    val label: String,
    val thresholds: Map<ChromaticAxis, Double?>,
    val catchAccuracy: Double?,
    val csv: String,
)

/**
 * Conduce il test a soglie: azzera il filtro (o lo accende, nel blocco di controllo), presenta
 * le prove di [ThresholdSession], raccoglie le risposte e a fine blocco ripristina le
 * impostazioni dell'utente.
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
    private val blocks = mutableListOf<BlockResult>()
    private var session: ThresholdSession? = null
    private var generator: LandoltStimulusGenerator? = null
    private var trial: Trial? = null
    private var presentedAtMs = 0L
    private var blockLabel = ""
    private var savedSettings: FilterSettings? = null
    private var restored = true

    // ponytail: ambito separato e mai annullato, perché il ripristino in onCleared deve
    // concludersi anche quando viewModelScope è già stato annullato. Se il processo viene
    // ucciso a metà test, il filtro resta spento finché non lo si riaccende dalla schermata principale.
    private val restoreScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    fun start(withFilter: Boolean) {
        _state.value = ThresholdTestState.Preparing
        viewModelScope.launch {
            val settings = savedSettings ?: repository.settings.first().also { savedSettings = it }
            restored = false
            val result = withContext(ioDispatcher) {
                if (withFilter) {
                    applier.apply(LuminanceShiftFilter.transform(settings.intensity))
                } else {
                    applier.reset()
                }
            }
            if (result !is ApplyResult.Success) {
                restoreSettings()
                _state.value = ThresholdTestState.Failed(describeStartFailure(result))
                return@launch
            }
            beginSession(withFilter)
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

    private fun beginSession(withFilter: Boolean) {
        val axes = if (withFilter) listOf(ChromaticAxis.M_ISOLATING) else ChromaticAxis.entries
        blockLabel = if (withFilter) "controllo con filtro acceso" else "filtro spento"
        session = ThresholdSession(axes, TEST_BACKGROUND, random, LUMINANCE_JITTER)
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
        _state.value = ThresholdTestState.Running(dots, current.answeredTrials, blockLabel)
    }

    private fun finishBlock() {
        val current = checkNotNull(session)
        blocks.add(BlockResult(blockLabel, current.thresholds(), current.catchAccuracy(), current.log.toCsv()))
        session = null
        generator = null
        _state.value = ThresholdTestState.Preparing
        viewModelScope.launch {
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
        for (block in blocks) {
            appendLine("Blocco: ${block.label}")
            for ((axis, threshold) in block.thresholds) {
                appendLine("  ${axisName(axis)}: ${formatThreshold(threshold)}")
            }
            val catches = block.catchAccuracy?.let { String.format(Locale.getDefault(), "%.0f%%", it * 100) } ?: "nessuna"
            appendLine("  Prove catch corrette: $catches")
            appendLine()
        }
        appendLine("Indice relativo: il display non è calibrato, quindi non è un contrasto assoluto.")
        if (restore != null && restore !is ApplyResult.Success) {
            appendLine()
            appendLine("Attenzione: il ripristino delle impostazioni non è riuscito. Riaccendi il filtro dalla schermata principale.")
        }
    }

    private fun axisName(axis: ChromaticAxis): String = when (axis) {
        ChromaticAxis.M_ISOLATING -> "Rosso-verde (cono M)"
        ChromaticAxis.S_ISOLATING -> "Giallo-blu (controllo, cono S)"
    }

    private fun formatThreshold(threshold: Double?): String =
        if (threshold == null) {
            "non determinata (stimolo mai visto anche al massimo, o sempre visto)"
        } else {
            String.format(Locale.getDefault(), "%.1f%% di contrasto di cono", threshold * 100)
        }

    private companion object {
        const val LUMINANCE_JITTER = 0.10

        /** Luminosità della finestra durante il test (1.0 = massima); il valore reale dipende dal pannello. */
        const val SCREEN_BRIGHTNESS = 1.0
    }
}
