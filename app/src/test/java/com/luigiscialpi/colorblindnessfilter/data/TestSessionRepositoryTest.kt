package com.luigiscialpi.colorblindnessfilter.data

import com.luigiscialpi.colorblindnessfilter.measure.ChromaticAxis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TestSessionRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun newRepository() = TestSessionRepository(temporaryFolder.newFolder())

    private fun sampleSnapshot(
        id: String = "session-1",
        startedAt: Long = 1000L,
        completed: Boolean = false,
        completedBlocks: List<SavedBlockResult> = emptyList(),
    ) = TestSessionSnapshot(
        id = id,
        type = TestSuiteType.SWEEP,
        title = "Scansione di β",
        startedAtEpochMs = startedAt,
        completedAtEpochMs = if (completed) startedAt + 5000L else null,
        isCompleted = completed,
        completedBlocks = completedBlocks,
        remainingBlocks = listOf(
            SavedPendingBlock(
                label = "scansione beta 0.15",
                displayLabel = "blocco 1",
                beta = 0.15,
                axes = listOf(ChromaticAxis.M_ISOLATING),
                isSweep = true,
            ),
        ),
    )

    @Test
    fun statoInizialeNonHaSessioniAttiveONellaCronologia() = runBlocking {
        val repo = newRepository()

        assertNull(repo.getActiveSession())
        assertTrue(repo.getHistory().isEmpty())
    }

    @Test
    fun salvaERecuperaSessioneAttiva() = runBlocking {
        val repo = newRepository()
        val snapshot = sampleSnapshot()

        repo.saveActiveSession(snapshot)
        val loaded = repo.getActiveSession()

        assertNotNull(loaded)
        assertEquals(snapshot.id, loaded?.id)
        assertEquals(snapshot.title, loaded?.title)
        assertEquals(snapshot.remainingBlocks.size, loaded?.remainingBlocks?.size)
    }

    @Test
    fun cancellazioneRimuoveLaSessioneAttiva() = runBlocking {
        val repo = newRepository()
        repo.saveActiveSession(sampleSnapshot())

        repo.clearActiveSession()

        assertNull(repo.getActiveSession())
    }

    @Test
    fun salvaSessioneCompletataLaArchiviaNellaCronologiaERimuoveQuellaAttiva() = runBlocking {
        val repo = newRepository()
        val snapshot = sampleSnapshot(completed = false)
        repo.saveActiveSession(snapshot)

        val completed = snapshot.copy(isCompleted = true, completedAtEpochMs = 2000L)
        repo.saveCompletedSession(completed)

        assertNull(repo.getActiveSession())
        val history = repo.getHistory()
        assertEquals(1, history.size)
        assertEquals(snapshot.id, history.single().id)
        assertTrue(history.single().isCompleted)
    }

    @Test
    fun archiviaSessioneAttivaComeAbbandonataSeAvevaBlocchiCompletati() = runBlocking {
        val repo = newRepository()
        val block = SavedBlockResult(
            label = "b1",
            displayLabel = "blocco 1",
            beta = 0.1,
            isSweep = true,
            thresholds = mapOf(ChromaticAxis.M_ISOLATING to 0.05),
            catchAccuracy = 1.0,
            csv = "csv",
        )
        val snapshot = sampleSnapshot(completed = false, completedBlocks = listOf(block))
        repo.saveActiveSession(snapshot)

        repo.archiveActiveSessionAsAbandoned()

        assertNull(repo.getActiveSession())
        val history = repo.getHistory()
        assertEquals(1, history.size)
        assertFalse(history.single().isCompleted)
        assertEquals(1, history.single().completedBlocks.size)
    }

    @Test
    fun cronologiaRestituisceElementiOrdinatiPerDataDecrescente() = runBlocking {
        val repo = newRepository()
        val s1 = sampleSnapshot(id = "old", startedAt = 1000L, completed = true)
        val s2 = sampleSnapshot(id = "recent", startedAt = 5000L, completed = true)
        val s3 = sampleSnapshot(id = "middle", startedAt = 3000L, completed = true)

        repo.saveCompletedSession(s1)
        repo.saveCompletedSession(s2)
        repo.saveCompletedSession(s3)

        val history = repo.getHistory()
        assertEquals(listOf("recent", "middle", "old"), history.map { it.id })
    }

    @Test
    fun eliminaSingolaSessioneDallaCronologia() = runBlocking {
        val repo = newRepository()
        repo.saveCompletedSession(sampleSnapshot(id = "s1", completed = true))
        repo.saveCompletedSession(sampleSnapshot(id = "s2", completed = true))

        val deleted = repo.deleteSession("s1")

        assertTrue(deleted)
        val history = repo.getHistory()
        assertEquals(listOf("s2"), history.map { it.id })
    }
}
