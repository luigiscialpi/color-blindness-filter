package com.luigiscialpi.colorblindnessfilter.data

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistenza delle sessioni del test a soglie su file JSON.
 *
 * Mantiene:
 * 1. La sessione attiva ([ACTIVE_FILE_NAME]), aggiornata dopo ogni blocco completato. Se la suite
 *    viene interrotta prima del termine, la sessione attiva resta su disco e può essere ripresa.
 * 2. La cartella della cronologia ([HISTORY_DIR_NAME]), che raccoglie tutte le sessioni concluse
 *    o archiviate, consultabili ed esportabili in qualsiasi momento.
 */
class TestSessionRepository(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val activeFile: File
        get() = File(directory, ACTIVE_FILE_NAME)

    private val historyDir: File
        get() = File(directory, HISTORY_DIR_NAME)

    suspend fun getActiveSession(): TestSessionSnapshot? = withContext(ioDispatcher) {
        if (!activeFile.exists()) return@withContext null
        runCatching {
            TestSessionJson.fromJson(activeFile.readText())
        }.getOrNull()
    }

    suspend fun saveActiveSession(snapshot: TestSessionSnapshot): Unit = withContext(ioDispatcher) {
        directory.mkdirs()
        writeAtomic(activeFile, TestSessionJson.toJson(snapshot))
    }

    suspend fun clearActiveSession(): Unit = withContext(ioDispatcher) {
        if (activeFile.exists()) {
            activeFile.delete()
        }
    }

    /** Archivia la sessione completata nella cronologia e rimuove la sessione attiva. */
    suspend fun saveCompletedSession(snapshot: TestSessionSnapshot): Unit = withContext(ioDispatcher) {
        historyDir.mkdirs()
        val completed = snapshot.copy(isCompleted = true)
        val file = File(historyDir, "session_${completed.id}.json")
        writeAtomic(file, TestSessionJson.toJson(completed))
        clearActiveSession()
    }

    /**
     * Archivia l'eventuale sessione attiva come interrotta/abbandonata se conteneva blocchi già
     * completati, quindi cancella la sessione attiva per consentire di iniziarne una nuova.
     */
    suspend fun archiveActiveSessionAsAbandoned(): Unit = withContext(ioDispatcher) {
        val active = getActiveSession()
        if (active != null && active.completedBlocks.isNotEmpty()) {
            historyDir.mkdirs()
            val abandoned = active.copy(isCompleted = false)
            val file = File(historyDir, "session_${abandoned.id}.json")
            writeAtomic(file, TestSessionJson.toJson(abandoned))
        }
        clearActiveSession()
    }

    /** Cronologia di tutte le sessioni completate o archiviate, ordinate per data decrescente. */
    suspend fun getHistory(): List<TestSessionSnapshot> = withContext(ioDispatcher) {
        if (!historyDir.exists()) return@withContext emptyList()
        val files = historyDir.listFiles { f -> f.extension == "json" } ?: return@withContext emptyList()
        files.mapNotNull { file ->
            runCatching { TestSessionJson.fromJson(file.readText()) }.getOrNull()
        }.sortedByDescending { it.startedAtEpochMs }
    }

    suspend fun deleteSession(id: String): Boolean = withContext(ioDispatcher) {
        val file = File(historyDir, "session_$id.json")
        if (file.exists()) file.delete() else false
    }

    suspend fun clearHistory(): Unit = withContext(ioDispatcher) {
        if (historyDir.exists()) {
            historyDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun writeAtomic(target: File, content: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content)
        if (!temp.renameTo(target)) {
            // Se renameTo fallisce su alcuni filesystem, sovrascrivi direttamente
            target.writeText(content)
            temp.delete()
        }
    }

    companion object {
        const val ACTIVE_FILE_NAME = "active_session.json"
        const val HISTORY_DIR_NAME = "history"
    }
}
