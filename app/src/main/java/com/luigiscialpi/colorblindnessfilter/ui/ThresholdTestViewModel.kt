package com.luigiscialpi.colorblindnessfilter.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luigiscialpi.colorblindnessfilter.FilterController
import com.luigiscialpi.colorblindnessfilter.data.FilterSettings
import com.luigiscialpi.colorblindnessfilter.data.SavedBlockResult
import com.luigiscialpi.colorblindnessfilter.data.SavedPendingBlock
import com.luigiscialpi.colorblindnessfilter.data.SettingsRepository
import com.luigiscialpi.colorblindnessfilter.data.TestSessionRepository
import com.luigiscialpi.colorblindnessfilter.data.TestSessionSnapshot
import com.luigiscialpi.colorblindnessfilter.data.TestSuiteType
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
import java.util.UUID
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
    data class Intro(
        val activeSession: TestSessionSnapshot? = null,
        val historyCount: Int = 0,
    ) : ThresholdTestState

    data class History(
        val sessions: List<TestSessionSnapshot>,
        val selectedSession: TestSessionSnapshot? = null,
    ) : ThresholdTestState

    data object Preparing : ThresholdTestState

    data class Running(val dots: List<Dot>, val answered: Int, val blockLabel: String) : ThresholdTestState

    data class Finished(
        val summary: String,
        val session: TestSessionSnapshot? = null,
    ) : ThresholdTestState

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
    val displayLabel: String,
    val beta: Double,
    val thresholds: Map<ChromaticAxis, Double?>,
    val catchAccuracy: Double?,
    val csv: String,
    val isSweep: Boolean,
)

/**
 * Conduce il test a soglie: porta lo schermo nella condizione di ogni blocco (filtro azzerato,
 * acceso con l'intensità salvata, oppure con un `β` della scansione), presenta le prove di
 * [ThresholdSession], raccoglie le risposte, salva lo storico e ripristina le impostazioni dell'utente.
 */
class ThresholdTestViewModel(
    private val applier: ScreenColorApplier,
    private val controller: FilterController,
    private val repository: SettingsRepository,
    private val testSessionRepository: TestSessionRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    coroutineScope: CoroutineScope? = null,
    private val timeProvider: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope
    private val _state = MutableStateFlow<ThresholdTestState>(ThresholdTestState.Intro())
    val state: StateFlow<ThresholdTestState> = _state.asStateFlow()

    private val random = Random(System.nanoTime())
    private val pending = ArrayDeque<BlockSpec>()
    private val blocks = mutableListOf<BlockResult>()
    private val sweepEntries = mutableListOf<SweepEntry>()
    private var currentBlock: BlockSpec? = null
    private var currentSnapshot: TestSessionSnapshot? = null
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

    init {
        loadIntroState()
    }

    fun loadIntroState() {
        scope.launch {
            val active = testSessionRepository.getActiveSession()
            val history = testSessionRepository.getHistory()
            currentSnapshot = active
            _state.value = ThresholdTestState.Intro(
                activeSession = if (active != null && active.remainingBlocks.isNotEmpty()) active else null,
                historyCount = history.size,
            )
        }
    }

    /** Un blocco: filtro spento (rosso-verde e giallo-blu) oppure controllo con il filtro acceso (solo rosso-verde). */
    fun start(withFilter: Boolean) {
        val type = if (withFilter) TestSuiteType.SINGLE_WITH_FILTER else TestSuiteType.SINGLE_NO_FILTER
        val title = if (withFilter) "Controllo con filtro acceso" else "Filtro spento"
        launchBlocks(type, title) { settings ->
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
        launchBlocks(TestSuiteType.SWEEP, "Scansione di β") {
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

    /**
     * Riprende la suite di test interrotta dal punto in cui era rimasta: recupera i blocchi già
     * completati con i rispettivi risultati e riprende l'esecuzione dai blocchi rimanenti.
     */
    fun resumeActiveSession() {
        val snapshot = currentSnapshot ?: return
        _state.value = ThresholdTestState.Preparing
        scope.launch {
            val settings = savedSettings ?: repository.settings.first().also { savedSettings = it }
            blocks.clear()
            sweepEntries.clear()
            for (saved in snapshot.completedBlocks) {
                blocks.add(
                    BlockResult(
                        label = saved.label,
                        displayLabel = saved.displayLabel,
                        beta = saved.beta,
                        thresholds = saved.thresholds,
                        catchAccuracy = saved.catchAccuracy,
                        csv = saved.csv,
                        isSweep = saved.isSweep,
                    ),
                )
                if (saved.isSweep) {
                    sweepEntries.add(SweepEntry(saved.beta, saved.thresholds[ChromaticAxis.M_ISOLATING]))
                }
            }
            pending.clear()
            pending.addAll(snapshot.remainingBlocks.map { it.toBlockSpec() })
            restored = false
            if (pending.isNotEmpty()) {
                runNextBlock()
            } else {
                val restore = restoreSettings()
                val finalSnapshot = snapshot.copy(
                    isCompleted = true,
                    completedAtEpochMs = System.currentTimeMillis(),
                )
                testSessionRepository.saveCompletedSession(finalSnapshot)
                _state.value = ThresholdTestState.Finished(summary(restore), session = finalSnapshot)
            }
        }
    }

    /** Abbandona la sessione attiva, archiviandola se parzialmente svolta, e torna all'introduzione. */
    fun discardActiveSession() {
        scope.launch {
            testSessionRepository.archiveActiveSessionAsAbandoned()
            currentSnapshot = null
            loadIntroState()
        }
    }

    fun openHistory() {
        scope.launch {
            val history = testSessionRepository.getHistory()
            _state.value = ThresholdTestState.History(sessions = history)
        }
    }

    fun selectHistorySession(session: TestSessionSnapshot?) {
        val current = _state.value as? ThresholdTestState.History ?: return
        _state.value = current.copy(selectedSession = session)
    }

    fun deleteHistorySession(id: String) {
        scope.launch {
            testSessionRepository.deleteSession(id)
            val history = testSessionRepository.getHistory()
            val current = _state.value as? ThresholdTestState.History
            val selected = if (current?.selectedSession?.id == id) null else current?.selectedSession
            _state.value = ThresholdTestState.History(sessions = history, selectedSession = selected)
        }
    }

    fun backToIntro() {
        loadIntroState()
    }

    fun onAnswer(chosen: Orientation) {
        val current = session ?: return
        if (trial == null) return
        current.answer(chosen, timeProvider() - presentedAtMs, SCREEN_BRIGHTNESS)
        trial = null
        showNextTrial()
    }

    /** Testo con i registri CSV di tutti i blocchi eseguiti, ciascuno preceduto dall'etichetta. */
    fun exportText(session: TestSessionSnapshot? = null): String {
        if (session != null) {
            return session.completedBlocks.joinToString(separator = "\n") { "# ${it.label}\n${it.csv}" }
        }
        return blocks.joinToString(separator = "\n") { "# ${it.label}\n${it.csv}" }
    }

    private fun launchBlocks(type: TestSuiteType, title: String, plan: (FilterSettings) -> List<BlockSpec>) {
        _state.value = ThresholdTestState.Preparing
        scope.launch {
            val settings = savedSettings ?: repository.settings.first().also { savedSettings = it }
            val specs = plan(settings)
            pending.clear()
            pending.addAll(specs)
            blocks.clear()
            sweepEntries.clear()

            val initialSnapshot = TestSessionSnapshot(
                id = UUID.randomUUID().toString(),
                type = type,
                title = title,
                startedAtEpochMs = System.currentTimeMillis(),
                isCompleted = false,
                completedBlocks = emptyList(),
                remainingBlocks = specs.map { it.toSavedPendingBlock() },
            )
            currentSnapshot = initialSnapshot
            testSessionRepository.saveActiveSession(initialSnapshot)

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
        presentedAtMs = timeProvider()
        _state.value = ThresholdTestState.Running(dots, current.answeredTrials, checkNotNull(currentBlock).displayLabel)
    }

    private fun finishBlock() {
        val spec = checkNotNull(currentBlock)
        val current = checkNotNull(session)
        val thresholds = current.thresholds()
        val blockResult = BlockResult(
            label = spec.label,
            displayLabel = spec.displayLabel,
            beta = spec.beta,
            thresholds = thresholds,
            catchAccuracy = current.catchAccuracy(),
            csv = current.log.toCsv(),
            isSweep = spec.isSweep,
        )
        blocks.add(blockResult)
        if (spec.isSweep) sweepEntries.add(SweepEntry(spec.beta, thresholds[ChromaticAxis.M_ISOLATING]))

        val savedBlock = SavedBlockResult(
            label = spec.label,
            displayLabel = spec.displayLabel,
            beta = spec.beta,
            isSweep = spec.isSweep,
            thresholds = thresholds,
            catchAccuracy = current.catchAccuracy(),
            csv = current.log.toCsv(),
        )
        val updatedSnapshot = currentSnapshot?.let { prev ->
            prev.copy(
                completedBlocks = prev.completedBlocks + savedBlock,
                remainingBlocks = pending.map { it.toSavedPendingBlock() },
            )
        }
        currentSnapshot = updatedSnapshot

        currentBlock = null
        session = null
        generator = null
        _state.value = ThresholdTestState.Preparing
        scope.launch {
            if (updatedSnapshot != null) {
                testSessionRepository.saveActiveSession(updatedSnapshot)
            }
            if (pending.isNotEmpty()) {
                runNextBlock()
                return@launch
            }
            val restore = restoreSettings()
            val finalSnapshot = updatedSnapshot?.copy(
                isCompleted = true,
                completedAtEpochMs = System.currentTimeMillis(),
                remainingBlocks = emptyList(),
            )
            if (finalSnapshot != null) {
                testSessionRepository.saveCompletedSession(finalSnapshot)
            }
            _state.value = ThresholdTestState.Finished(summary(restore), session = finalSnapshot)
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
        if (sweepEntries.isNotEmpty()) appendSweepSummary(sweepEntries)
        appendLine("Indice relativo: il display non è calibrato, quindi non è un contrasto assoluto.")
        if (restore != null && restore !is ApplyResult.Success) {
            appendLine()
            appendLine("Attenzione: il ripristino delle impostazioni non è riuscito. Riaccendi il filtro dalla schermata principale.")
        }
    }

    companion object {
        fun formatSessionSummary(session: TestSessionSnapshot): String = buildString {
            for (block in session.completedBlocks.filterNot { it.isSweep }) {
                appendLine("Blocco: ${block.label}")
                for ((axis, threshold) in block.thresholds) {
                    appendLine("  ${axisName(axis)}: ${formatThreshold(threshold)}")
                }
                val catches = block.catchAccuracy?.let { String.format(Locale.getDefault(), "%.0f%%", it * 100) } ?: "nessuna"
                appendLine("  Prove catch corrette: $catches")
                appendLine()
            }
            val sweep = session.completedBlocks.filter { it.isSweep }.map {
                SweepEntry(it.beta, it.thresholds[ChromaticAxis.M_ISOLATING])
            }
            if (sweep.isNotEmpty()) appendSweepSummary(sweep)
            appendLine("Indice relativo: il display non è calibrato.")
        }

        private fun StringBuilder.appendSweepSummary(entries: List<SweepEntry>) {
            appendLine("Scansione di beta (soglia rosso-verde, minore è meglio)")
            for (row in BetaSweep.summarize(entries)) {
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

        private fun BlockSpec.toSavedPendingBlock() = SavedPendingBlock(
            label = label,
            displayLabel = displayLabel,
            beta = beta,
            axes = axes,
            isSweep = isSweep,
        )

        private fun SavedPendingBlock.toBlockSpec() = BlockSpec(
            label = label,
            displayLabel = displayLabel,
            beta = beta,
            axes = axes,
            isSweep = isSweep,
        )

        const val LUMINANCE_JITTER = 0.10

        /** Luminosità della finestra durante il test (1.0 = massima); il valore reale dipende dal pannello. */
        const val SCREEN_BRIGHTNESS = 1.0

        /** Valori di `β` della scansione: 0 è il riferimento senza filtro. */
        val SWEEP_BETAS = listOf(0.0, 0.05, 0.10, 0.15, 0.20, 0.30)
    }
}
