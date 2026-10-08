package com.luigiscialpi.colorblindnessfilter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luigiscialpi.colorblindnessfilter.domain.LuminanceShiftFilter
import com.luigiscialpi.colorblindnessfilter.system.ApplyResult
import java.util.Locale
import kotlin.math.roundToInt

private const val INTENSITY_STEP = 0.01

// ponytail: testi in italiano direttamente nel codice, app personale a lingua singola.
// Per localizzare basta spostarli in strings.xml.
@Composable
fun FilterScreen(
    state: FilterUiState,
    onEnabledChange: (Boolean) -> Unit,
    onIntensityChange: (Double) -> Unit,
    onIntensityChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Text(text = "Color Blindness Filter", style = MaterialTheme.typography.headlineSmall)
        EnabledRow(enabled = state.settings.enabled, onEnabledChange = onEnabledChange)
        IntensityControl(
            intensity = state.settings.intensity,
            onIntensityChange = onIntensityChange,
            onIntensityChangeFinished = onIntensityChangeFinished,
        )
        ReferencePatches()
        StatusMessage(result = state.lastResult)
    }
}

@Composable
private fun EnabledRow(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(text = "Filtro colori", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (enabled) "Attivo" else "Spento",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // L'interazione è gestita dalla riga (toggleable): lo Switch è solo l'indicatore.
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun IntensityControl(
    intensity: Double,
    onIntensityChange: (Double) -> Unit,
    onIntensityChangeFinished: () -> Unit,
) {
    val steps = ((LuminanceShiftFilter.MAX_BETA - LuminanceShiftFilter.MIN_BETA) / INTENSITY_STEP)
        .roundToInt() - 1
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Intensità", style = MaterialTheme.typography.titleMedium)
            Text(
                // Solo visualizzazione: qui il separatore decimale segue il locale dell'utente.
                text = String.format(Locale.getDefault(), "%.2f", intensity),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Slider(
            value = intensity.toFloat(),
            onValueChange = { onIntensityChange((it / INTENSITY_STEP.toFloat()).roundToInt() * INTENSITY_STEP) },
            onValueChangeFinished = onIntensityChangeFinished,
            valueRange = LuminanceShiftFilter.MIN_BETA.toFloat()..LuminanceShiftFilter.MAX_BETA.toFloat(),
            steps = steps,
            modifier = Modifier.semantics { contentDescription = "Intensità" },
        )
    }
}

/**
 * Campioni fissi di rosso, verde e grigio: sono filtrati insieme al resto dello schermo,
 * quindi muovendo lo slider si vede subito come cambiano. Ogni campione ha un'etichetta
 * di testo: il colore non è l'unica informazione.
 */
@Composable
private fun ReferencePatches() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Campioni di riferimento", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Patch(label = "Rosso", color = Color(0xFFD32F2F), modifier = Modifier.weight(1f))
            Patch(label = "Verde", color = Color(0xFF388E3C), modifier = Modifier.weight(1f))
            Patch(label = "Grigio", color = Color(0xFF808080), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Patch(label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(64.dp).background(color))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusMessage(result: ApplyResult?) {
    val message = when (result) {
        null, ApplyResult.Success -> return
        ApplyResult.RootUnavailable -> "Root non disponibile. Concedi il root all'app in Magisk e riaprila."
        is ApplyResult.Failed -> "Comando non riuscito: ${result.message}"
    }
    Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
