package com.luigiscialpi.colorblindnessfilter.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: FilterViewModel by viewModels {
        viewModelFactory {
            initializer {
                val dependencies = AppDependencies(applicationContext)
                FilterViewModel(repository = dependencies.repository, controller = dependencies.controller)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FilterTheme {
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
