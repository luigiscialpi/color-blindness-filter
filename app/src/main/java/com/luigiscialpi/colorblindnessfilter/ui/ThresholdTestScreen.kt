package com.luigiscialpi.colorblindnessfilter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luigiscialpi.colorblindnessfilter.measure.ConeSpace
import com.luigiscialpi.colorblindnessfilter.measure.LinearRgb
import com.luigiscialpi.colorblindnessfilter.measure.Orientation

// ponytail: testi in italiano direttamente nel codice, come nella schermata principale.
@Composable
fun ThresholdTestScreen(
    state: ThresholdTestState,
    onStart: (withFilter: Boolean) -> Unit,
    onAnswer: (Orientation) -> Unit,
    onCopyResults: () -> Unit,
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
            ThresholdTestState.Intro -> IntroContent(onStart = onStart, onClose = onClose)
            ThresholdTestState.Preparing -> Text("Preparazione…", style = MaterialTheme.typography.titleMedium)
            is ThresholdTestState.Running -> RunningContent(state = state, onAnswer = onAnswer)
            is ThresholdTestState.Finished -> FinishedContent(
                summary = state.summary,
                onStart = onStart,
                onCopyResults = onCopyResults,
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
private fun IntroContent(onStart: (Boolean) -> Unit, onClose: () -> Unit) {
    Text("Test a soglie", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Prima di iniziare disattiva Night Light e la modalità lettura di MIUI. Durante il test lo " +
            "schermo resta alla luminosità massima e il filtro viene azzerato; alla fine torna come prima.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "A ogni schermata indica dove si apre la C. Se non la vedi, scegli comunque la direzione che ti sembra più probabile.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Button(onClick = { onStart(false) }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        Text("Avvia il test")
    }
    OutlinedButton(onClick = { onStart(true) }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        Text("Controllo con filtro acceso")
    }
    TextButton(onClick = onClose) { Text("Chiudi") }
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
    onCopyResults: () -> Unit,
    onClose: () -> Unit,
) {
    Text("Risultati", style = MaterialTheme.typography.headlineSmall)
    Text(summary, style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onCopyResults, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        Text("Copia i risultati (CSV)")
    }
    OutlinedButton(onClick = { onStart(true) }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        Text("Controllo con filtro acceso")
    }
    TextButton(onClick = onClose) { Text("Chiudi") }
}

private fun LinearRgb.toComposeColor(): Color = Color(
    red = ConeSpace.encodeSrgb(r.coerceIn(0.0, 1.0)).toFloat(),
    green = ConeSpace.encodeSrgb(g.coerceIn(0.0, 1.0)).toFloat(),
    blue = ConeSpace.encodeSrgb(b.coerceIn(0.0, 1.0)).toFloat(),
)
