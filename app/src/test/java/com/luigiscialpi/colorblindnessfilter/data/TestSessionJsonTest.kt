package com.luigiscialpi.colorblindnessfilter.data

import com.luigiscialpi.colorblindnessfilter.measure.ChromaticAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSessionJsonTest {

    @Test
    fun roundtripSessioneCompleta() {
        val snapshot = TestSessionSnapshot(
            id = "test-session-123",
            type = TestSuiteType.SWEEP,
            title = "Scansione di β",
            startedAtEpochMs = 1740000000000L,
            completedAtEpochMs = 1740000900000L,
            isCompleted = true,
            completedBlocks = listOf(
                SavedBlockResult(
                    label = "scansione beta 0.15",
                    displayLabel = "scansione: blocco 1 di 2",
                    beta = 0.15,
                    isSweep = true,
                    thresholds = mapOf(ChromaticAxis.M_ISOLATING to 0.045123),
                    catchAccuracy = 1.0,
                    csv = "axis,contrast,correct\nM_ISOLATING,0.150000,1\n",
                ),
                SavedBlockResult(
                    label = "scansione beta 0.00",
                    displayLabel = "scansione: blocco 2 di 2",
                    beta = 0.0,
                    isSweep = true,
                    thresholds = mapOf(
                        ChromaticAxis.M_ISOLATING to 0.123456,
                        ChromaticAxis.S_ISOLATING to 0.034567,
                    ),
                    catchAccuracy = 0.85,
                    csv = "axis,contrast,correct\nM_ISOLATING,0.200000,0\n",
                ),
            ),
            remainingBlocks = emptyList(),
        )

        val json = TestSessionJson.toJson(snapshot)
        val deserialized = TestSessionJson.fromJson(json)

        assertEquals(snapshot.id, deserialized.id)
        assertEquals(snapshot.type, deserialized.type)
        assertEquals(snapshot.title, deserialized.title)
        assertEquals(snapshot.startedAtEpochMs, deserialized.startedAtEpochMs)
        assertEquals(snapshot.completedAtEpochMs, deserialized.completedAtEpochMs)
        assertTrue(deserialized.isCompleted)
        assertEquals(2, deserialized.completedBlocks.size)
        assertEquals(snapshot.completedBlocks[0].label, deserialized.completedBlocks[0].label)
        assertEquals(snapshot.completedBlocks[0].beta, deserialized.completedBlocks[0].beta, 1e-5)
        assertEquals(snapshot.completedBlocks[0].thresholds, deserialized.completedBlocks[0].thresholds)
        assertEquals(snapshot.completedBlocks[0].catchAccuracy, deserialized.completedBlocks[0].catchAccuracy)
        assertEquals(snapshot.completedBlocks[0].csv, deserialized.completedBlocks[0].csv)
        assertTrue(deserialized.remainingBlocks.isEmpty())
    }

    @Test
    fun roundtripSessioneInterrottaConBlocchiRimanenti() {
        val snapshot = TestSessionSnapshot(
            id = "pending-123",
            type = TestSuiteType.SWEEP,
            title = "Scansione di β",
            startedAtEpochMs = 1740000000000L,
            completedAtEpochMs = null,
            isCompleted = false,
            completedBlocks = listOf(
                SavedBlockResult(
                    label = "scansione beta 0.10",
                    displayLabel = "scansione: blocco 1 di 3",
                    beta = 0.10,
                    isSweep = true,
                    thresholds = emptyMap(),
                    catchAccuracy = null,
                    csv = "axis,contrast\n",
                ),
            ),
            remainingBlocks = listOf(
                SavedPendingBlock(
                    label = "scansione beta 0.20",
                    displayLabel = "scansione: blocco 2 di 3",
                    beta = 0.20,
                    axes = listOf(ChromaticAxis.M_ISOLATING),
                    isSweep = true,
                ),
                SavedPendingBlock(
                    label = "scansione beta 0.00",
                    displayLabel = "scansione: blocco 3 di 3",
                    beta = 0.00,
                    axes = listOf(ChromaticAxis.M_ISOLATING, ChromaticAxis.S_ISOLATING),
                    isSweep = true,
                ),
            ),
        )

        val json = TestSessionJson.toJson(snapshot)
        val deserialized = TestSessionJson.fromJson(json)

        assertEquals("pending-123", deserialized.id)
        assertFalse(deserialized.isCompleted)
        assertNull(deserialized.completedAtEpochMs)
        assertEquals(1, deserialized.completedBlocks.size)
        assertEquals(2, deserialized.remainingBlocks.size)
        assertEquals("scansione beta 0.20", deserialized.remainingBlocks[0].label)
        assertEquals(listOf(ChromaticAxis.M_ISOLATING), deserialized.remainingBlocks[0].axes)
        assertEquals(listOf(ChromaticAxis.M_ISOLATING, ChromaticAxis.S_ISOLATING), deserialized.remainingBlocks[1].axes)
    }

    @Test
    fun gestisceStringheConCaratteriSpeciali() {
        val csvWithQuotesAndNewlines = "line1,\"quoted, comma\",escaped\\backslash\nline2\twith\ttab\n"
        val snapshot = TestSessionSnapshot(
            id = "special-chars",
            type = TestSuiteType.SINGLE_NO_FILTER,
            title = "Titolo con \"virgolette\" e caratteri speciali",
            startedAtEpochMs = 1000L,
            completedAtEpochMs = 2000L,
            isCompleted = true,
            completedBlocks = listOf(
                SavedBlockResult(
                    label = "blocco con \"virgolette\"",
                    displayLabel = "display",
                    beta = 0.0,
                    isSweep = false,
                    thresholds = emptyMap(),
                    catchAccuracy = null,
                    csv = csvWithQuotesAndNewlines,
                ),
            ),
            remainingBlocks = emptyList(),
        )

        val json = TestSessionJson.toJson(snapshot)
        val deserialized = TestSessionJson.fromJson(json)

        assertEquals(snapshot.title, deserialized.title)
        assertEquals(csvWithQuotesAndNewlines, deserialized.completedBlocks.single().csv)
    }
}
