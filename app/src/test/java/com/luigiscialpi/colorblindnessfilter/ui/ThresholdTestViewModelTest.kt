package com.luigiscialpi.colorblindnessfilter.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.luigiscialpi.colorblindnessfilter.FilterController
import com.luigiscialpi.colorblindnessfilter.data.SavedBlockResult
import com.luigiscialpi.colorblindnessfilter.data.SavedPendingBlock
import com.luigiscialpi.colorblindnessfilter.data.SettingsRepository
import com.luigiscialpi.colorblindnessfilter.data.TestSessionRepository
import com.luigiscialpi.colorblindnessfilter.data.TestSessionSnapshot
import com.luigiscialpi.colorblindnessfilter.data.TestSuiteType
import com.luigiscialpi.colorblindnessfilter.domain.ColorTransform
import com.luigiscialpi.colorblindnessfilter.measure.ChromaticAxis
import com.luigiscialpi.colorblindnessfilter.system.ApplyResult
import com.luigiscialpi.colorblindnessfilter.system.BootScriptWriter
import com.luigiscialpi.colorblindnessfilter.system.FakeRootShell
import com.luigiscialpi.colorblindnessfilter.system.ScreenColorApplier
import com.luigiscialpi.colorblindnessfilter.system.ShellResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThresholdTestViewModelTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = Dispatchers.Unconfined
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private class FakeApplier : ScreenColorApplier {
        val applied = mutableListOf<ColorTransform>()
        var resets = 0

        override fun apply(transform: ColorTransform): ApplyResult {
            applied.add(transform)
            return ApplyResult.Success
        }

        override fun reset(): ApplyResult {
            resets++
            return ApplyResult.Success
        }
    }

    private fun createDependencies(): Triple<SettingsRepository, TestSessionRepository, FilterController> {
        val storeFile = File(temporaryFolder.newFolder("datastore"), "settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { storeFile },
        )
        val repository = SettingsRepository(dataStore)
        val sessionRepo = TestSessionRepository(
            directory = temporaryFolder.newFolder("sessions"),
            ioDispatcher = dispatcher,
        )
        val shell = FakeRootShell(result = ShellResult(isSuccess = true, output = emptyList()))
        val controller = FilterController(FakeApplier(), BootScriptWriter(shell))
        return Triple(repository, sessionRepo, controller)
    }

    private fun createViewModel(
        settingsRepo: SettingsRepository,
        sessionRepo: TestSessionRepository,
        controller: FilterController,
        applier: ScreenColorApplier = FakeApplier(),
    ) = ThresholdTestViewModel(
        applier = applier,
        controller = controller,
        repository = settingsRepo,
        testSessionRepository = sessionRepo,
        ioDispatcher = dispatcher,
        coroutineScope = scope,
        timeProvider = { 1000L },
    )

    @Test
    fun statoInizialeSenzaSessioniMostraIntroVuoto() = runBlocking {
        val (settingsRepo, sessionRepo, controller) = createDependencies()
        val viewModel = createViewModel(settingsRepo, sessionRepo, controller)

        val state = viewModel.state.value as ThresholdTestState.Intro
        assertNull(state.activeSession)
        assertEquals(0, state.historyCount)
    }

    @Test
    fun avvioScansioneSalvaLaSessioneAttivaNelRepository() = runBlocking {
        val (settingsRepo, sessionRepo, controller) = createDependencies()
        val viewModel = createViewModel(settingsRepo, sessionRepo, controller)

        viewModel.startSweep()

        val active = sessionRepo.getActiveSession()
        assertNotNull(active)
        assertEquals(TestSuiteType.SWEEP, active?.type)
        assertEquals(6, active?.remainingBlocks?.size)
        assertTrue(viewModel.state.value is ThresholdTestState.Running)
    }

    @Test
    fun presenzaDiSessioneInterrottaVieneRilevataNellIntro() = runBlocking {
        val (settingsRepo, sessionRepo, controller) = createDependencies()
        val interrupted = TestSessionSnapshot(
            id = "interrupted-1",
            type = TestSuiteType.SWEEP,
            title = "Scansione di β",
            startedAtEpochMs = 1000L,
            completedBlocks = listOf(
                SavedBlockResult(
                    label = "blocco 1",
                    displayLabel = "blocco 1",
                    beta = 0.1,
                    isSweep = true,
                    thresholds = mapOf(ChromaticAxis.M_ISOLATING to 0.05),
                    catchAccuracy = 1.0,
                    csv = "csv",
                ),
            ),
            remainingBlocks = listOf(
                SavedPendingBlock(
                    label = "blocco 2",
                    displayLabel = "blocco 2",
                    beta = 0.2,
                    axes = listOf(ChromaticAxis.M_ISOLATING),
                    isSweep = true,
                ),
            ),
        )
        sessionRepo.saveActiveSession(interrupted)

        val viewModel = createViewModel(settingsRepo, sessionRepo, controller)

        val state = viewModel.state.value as ThresholdTestState.Intro
        assertNotNull(state.activeSession)
        assertEquals("interrupted-1", state.activeSession?.id)
        assertEquals(1, state.activeSession?.completedBlocks?.size)
        assertEquals(1, state.activeSession?.remainingBlocks?.size)
    }

    @Test
    fun abbandonoSessioneAttivaArchiviaComeAbbandonataEAggiornaLIntro() = runBlocking {
        val (settingsRepo, sessionRepo, controller) = createDependencies()
        val snapshot = TestSessionSnapshot(
            id = "to-discard",
            type = TestSuiteType.SWEEP,
            title = "Scansione di β",
            startedAtEpochMs = 1000L,
            completedBlocks = listOf(
                SavedBlockResult(
                    label = "blocco 1",
                    displayLabel = "blocco 1",
                    beta = 0.1,
                    isSweep = true,
                    thresholds = mapOf(ChromaticAxis.M_ISOLATING to 0.05),
                    catchAccuracy = 1.0,
                    csv = "csv",
                ),
            ),
            remainingBlocks = listOf(
                SavedPendingBlock(
                    label = "blocco 2",
                    displayLabel = "blocco 2",
                    beta = 0.2,
                    axes = listOf(ChromaticAxis.M_ISOLATING),
                    isSweep = true,
                ),
            ),
        )
        sessionRepo.saveActiveSession(snapshot)

        val viewModel = createViewModel(settingsRepo, sessionRepo, controller)

        viewModel.discardActiveSession()

        assertNull(sessionRepo.getActiveSession())
        val history = sessionRepo.getHistory()
        assertEquals(1, history.size)
        assertEquals("to-discard", history.single().id)

        val state = viewModel.state.value as ThresholdTestState.Intro
        assertNull(state.activeSession)
        assertEquals(1, state.historyCount)
    }

    @Test
    fun ripresaSessioneAttivaRicaricaIBlocchiPrecedentiEAvviaQuelloRimanente() = runBlocking {
        val (settingsRepo, sessionRepo, controller) = createDependencies()
        val block1 = SavedBlockResult(
            label = "scansione beta 0.10",
            displayLabel = "blocco 1",
            beta = 0.10,
            isSweep = true,
            thresholds = mapOf(ChromaticAxis.M_ISOLATING to 0.04),
            catchAccuracy = 1.0,
            csv = "csv1",
        )
        val remainingBlock = SavedPendingBlock(
            label = "scansione beta 0.20",
            displayLabel = "blocco 2",
            beta = 0.20,
            axes = listOf(ChromaticAxis.M_ISOLATING),
            isSweep = true,
        )
        val snapshot = TestSessionSnapshot(
            id = "resume-test",
            type = TestSuiteType.SWEEP,
            title = "Scansione di β",
            startedAtEpochMs = 1000L,
            completedBlocks = listOf(block1),
            remainingBlocks = listOf(remainingBlock),
        )
        sessionRepo.saveActiveSession(snapshot)

        val viewModel = createViewModel(settingsRepo, sessionRepo, controller)

        viewModel.resumeActiveSession()

        val running = viewModel.state.value as ThresholdTestState.Running
        assertEquals("blocco 2", running.blockLabel)
    }

    @Test
    fun eliminazioneDellaSessioneDallaCronologia() = runBlocking {
        val (settingsRepo, sessionRepo, controller) = createDependencies()
        val s = TestSessionSnapshot(
            id = "s-del",
            type = TestSuiteType.SINGLE_NO_FILTER,
            title = "Filtro spento",
            startedAtEpochMs = 2000L,
            completedAtEpochMs = 3000L,
            isCompleted = true,
        )
        sessionRepo.saveCompletedSession(s)

        val viewModel = createViewModel(settingsRepo, sessionRepo, controller)

        viewModel.openHistory()

        assertTrue(viewModel.state.value is ThresholdTestState.History)
        val historyState = viewModel.state.value as ThresholdTestState.History
        assertEquals(1, historyState.sessions.size)

        viewModel.deleteHistorySession("s-del")

        val updatedHistory = viewModel.state.value as ThresholdTestState.History
        assertTrue(updatedHistory.sessions.isEmpty())
    }
}
