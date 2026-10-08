package com.luigiscialpi.colorblindnessfilter.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luigiscialpi.colorblindnessfilter.FilterController
import com.luigiscialpi.colorblindnessfilter.data.SettingsRepository
import com.luigiscialpi.colorblindnessfilter.data.filterSettingsDataStore
import com.luigiscialpi.colorblindnessfilter.system.LibsuRootShell
import com.luigiscialpi.colorblindnessfilter.system.SurfaceFlingerColorApplier

class MainActivity : ComponentActivity() {

    // Cablaggio manuale delle dipendenze: tre oggetti non giustificano un framework di DI.
    private val viewModel: FilterViewModel by viewModels {
        viewModelFactory {
            initializer {
                FilterViewModel(
                    repository = SettingsRepository(applicationContext.filterSettingsDataStore),
                    controller = FilterController(SurfaceFlingerColorApplier(LibsuRootShell())),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // ponytail: colori dinamici sempre disponibili (minSdk 31), nessun tema proprio.
            val context = LocalContext.current
            val colorScheme = if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            MaterialTheme(colorScheme = colorScheme) {
                // ponytail: collectAsState e non collectAsStateWithLifecycle: lo StateFlow non ha
                // lavoro a monte da sospendere, quindi evitiamo la dipendenza lifecycle-runtime-compose.
                val state by viewModel.state.collectAsState()
                FilterScreen(
                    state = state,
                    onEnabledChange = viewModel::onEnabledChange,
                    onIntensityChange = viewModel::onIntensityChange,
                    onIntensityChangeFinished = viewModel::onIntensityChangeFinished,
                )
            }
        }
    }
}
