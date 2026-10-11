package com.luigiscialpi.colorblindnessfilter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luigiscialpi.colorblindnessfilter.data.TestSessionSnapshot
import com.luigiscialpi.colorblindnessfilter.measure.ConeSpace
import com.luigiscialpi.colorblindnessfilter.measure.LinearRgb
import com.luigiscialpi.colorblindnessfilter.measure.Orientation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ponytail: testi in italiano direttamente nel codice, come nella schermata principale.
@Composable
fun ThresholdTestScreen(
    state: ThresholdTestState,
    onStart: (withFilter: Boolean) -> Unit,
    onStartSweep: () -> Unit,
    onResumeSession: () -> Unit,
    onDiscardSession: () -> Unit,
    onOpenHistory: () -> Unit,
    onBackToIntro: () -> Unit,
    onSelectHistorySession: (TestSessionSnapshot?) -> Unit,
    onDeleteHistorySession: (String) -> Unit,
    onAnswer: (Orientation) -> Unit,
    onCopyResults: (text: String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            is ThresholdTestState.Intro -> IntroContent(
                activeSession = state.activeSession,
                historyCount = state.historyCount,
                onStart = onStart,
                onStartSweep = onStartSweep,
                onResumeSession = onResumeSession,
                onDiscardSession = onDiscardSession,
                onOpenHistory = onOpenHistory,
                onClose = onClose,
            )
            is ThresholdTestState.History -> HistoryContent(
                sessions = state.sessions,
                selectedSession = state.selectedSession,
                onSelectSession = onSelectHistorySession,
                onDeleteSession = onDeleteHistorySession,
                onCopyResults = { onCopyResults(it) },
                onBack = onBackToIntro,
            )
            ThresholdTestState.Preparing -> Text("Preparazione…", style = MaterialTheme.typography.titleMedium)
            is ThresholdTestState.Running -> RunningContent(state = state, onAnswer = onAnswer)
            is ThresholdTestState.Finished -> FinishedContent(
                summary = state.summary,
                onStart = onStart,
                onStartSweep = onStartSweep,
                onOpenHistory = onOpenHistory,
                onCopyResults = { onCopyResults(null) },
                onClose = onClose,
            )
            is ThresholdTestState.Failed -> {
                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onClose) { Text("Chiudi") }
            }
        }
    }
}

@Composable
private fun IntroContent(
    activeSession: TestSessionSnapshot?,
    historyCount: Int,
    onStart: (Boolean) -> Unit,
    onStartSweep: () -> Unit,
    onResumeSession: () -> Unit,
    onDiscardSession: () -> Unit,
    onOpenHistory: () -> Unit,
    onClose: () -> Unit,
) {
    Text("Test a soglie", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Prima di iniziare disattiva Night Light e la modalità lettura di MIUI. Durante il test lo " +
            "schermo resta alla luminosità massima e il filtro viene azzerato; alla fine torna come prima.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "A ogni schermata indica dove si apre la C. Se non la vedi, scegli comunque la direzione che ti sembra più probabile. " +
            "Nella scansione di β il valore del filtro cambia a ogni blocco senza dirtelo: i valori compaiono solo nei risultati.",
        style = MaterialTheme.typography.bodyLarge,
    )

    if (activeSession != null) {
        InterruptedSessionCard(
            session = activeSession,
            onResume = onResumeSession,
            onDiscard = onDiscardSession,
        )
    }

    Button(onClick = { onStart(false) }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        Text("Avvia il test (filtro spento)")
    }
    OutlinedButton(onClick = { onStart(true) }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        Text("Controllo con filtro acceso")
    }
    OutlinedButton(onClick = onStartSweep, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        Text("Scansione di β (10-15 minuti)")
    }
    if (historyCount > 0) {
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
            Text("Cronologia test ($historyCount)")
        }
    }
    TextButton(onClick = onClose) { Text("Chiudi") }
}

@Composable
private fun InterruptedSessionCard(
    session: TestSessionSnapshot,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Sessione interrotta",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "${session.title} · ${session.completedBlocks.size} di ${session.totalBlocks} blocchi completati.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                ) {
                    val nextIndex = session.completedBlocks.size + 1
                    Text("Riprendi (blocco $nextIndex)")
                }
                OutlinedButton(onClick = onDiscard) {
                    Text("Elimina")
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(
    sessions: List<TestSessionSnapshot>,
    selectedSession: TestSessionSnapshot?,
    onSelectSession: (TestSessionSnapshot?) -> Unit,
    onDeleteSession: (String) -> Unit,
    onCopyResults: (String) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Cronologia dei test", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onBack) { Text("Indietro") }
    }

    if (sessions.isEmpty()) {
        Text("Nessuna sessione presente nella cronologia.", style = MaterialTheme.typography.bodyLarge)
    } else {
        Text(
            "Tocca un test per visualizzare le soglie calcolate o copiare i dati CSV.",
            style = MaterialTheme.typography.bodyMedium,
        )

        for (session in sessions) {
            val isSelected = selectedSession?.id == session.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSession(if (isSelected) null else session) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(session.title, style = MaterialTheme.typography.titleMedium)
                        val statusLabel = if (session.isCompleted) {
                            "Completato"
                        } else {
                            "Interrotto (${session.completedBlocks.size}/${session.totalBlocks})"
                        }
                        Text(
                            statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (session.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }

                    Text(
                        formatDateTime(session.startedAtEpochMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )

                    if (isSelected) {
                        val summaryText = ThresholdTestViewModel.formatSessionSummary(session)
                        Text(summaryText, style = MaterialTheme.typography.bodySmall)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val csvExport = session.completedBlocks.joinToString(separator = "\n") {
                                "# ${it.label}\n${it.csv}"
                            }
                            Button(
                                onClick = { onCopyResults(csvExport) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Copia CSV")
                            }
                            OutlinedButton(onClick = { onDeleteSession(session.id) }) {
                                Text("Elimina")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDateTime(epochMs: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMs))
}

@Composable
private fun RunningContent(state: ThresholdTestState.Running, onAnswer: (Orientation) -> Unit) {
    Text(
        "${state.blockLabel} · prova ${state.answered + 1}",
        style = MaterialTheme.typography.titleMedium,
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(TEST_BACKGROUND.toComposeColor()),
    ) {
        for (dot in state.dots) {
            drawCircle(
                color = dot.color.toComposeColor(),
                radius = (dot.radius * size.width).toFloat(),
                center = Offset((dot.x * size.width).toFloat(), (dot.y * size.height).toFloat()),
            )
        }
    }
    Text("Dove si apre la C?", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnswerButton("Su", Orientation.UP, onAnswer, Modifier.weight(1f))
        AnswerButton("Giù", Orientation.DOWN, onAnswer, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnswerButton("Sinistra", Orientation.LEFT, onAnswer, Modifier.weight(1f))
        AnswerButton("Destra", Orientation.RIGHT, onAnswer, Modifier.weight(1f))
    }
}

@Composable
private fun AnswerButton(
    label: String,
    orientation: Orientation,
    onAnswer: (Orientation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(onClick = { onAnswer(orientation) }, modifier = modifier.defaultMinSize(minHeight = 56.dp)) {
        Text(label)
    }
}

@Composable
private fun FinishedContent(
    summary: String,
    onStart: (Boolean) -> Unit,
    onStartSweep: () -> Unit,
    onOpenHistory: () -> Unit,
    onCopyResults: () -> Unit,
    onClose: () -> Unit,
) {
    Text("Risultati", style = MaterialTheme.typography.headlineSmall)
    Text(summary, style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onCopyResults, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        Text("Copia i risultati (CSV)")
    }
    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        Text("Vedi cronologia test")
    }
    OutlinedButton(onClick = { onStart(true) }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        Text("Controllo con filtro acceso")
    }
    OutlinedButton(onClick = onStartSweep, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        Text("Ripeti la scansione di β")
    }
    TextButton(onClick = onClose) { Text("Chiudi") }
}

private fun LinearRgb.toComposeColor(): Color = Color(
    red = ConeSpace.encodeSrgb(r.coerceIn(0.0, 1.0)).toFloat(),
    green = ConeSpace.encodeSrgb(g.coerceIn(0.0, 1.0)).toFloat(),
    blue = ConeSpace.encodeSrgb(b.coerceIn(0.0, 1.0)).toFloat(),
)
