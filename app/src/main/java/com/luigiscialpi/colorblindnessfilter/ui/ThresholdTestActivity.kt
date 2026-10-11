package com.luigiscialpi.colorblindnessfilter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class ThresholdTestActivity : ComponentActivity() {

    private val viewModel: ThresholdTestViewModel by viewModels {
        viewModelFactory {
            initializer {
                val dependencies = AppDependencies(applicationContext)
                ThresholdTestViewModel(
                    applier = dependencies.applier,
                    controller = dependencies.controller,
                    repository = dependencies.repository,
                    testSessionRepository = dependencies.testSessionRepository,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Condizioni del test: schermo sempre acceso, luminosità massima, modalità colore predefinita.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        setContent {
            FilterTheme {
                val state by viewModel.state.collectAsState()
                ThresholdTestScreen(
                    state = state,
                    onStart = viewModel::start,
                    onStartSweep = viewModel::startSweep,
                    onResumeSession = viewModel::resumeActiveSession,
                    onDiscardSession = viewModel::discardActiveSession,
                    onOpenHistory = viewModel::openHistory,
                    onBackToIntro = viewModel::backToIntro,
                    onSelectHistorySession = viewModel::selectHistorySession,
                    onDeleteHistorySession = viewModel::deleteHistorySession,
                    onAnswer = viewModel::onAnswer,
                    onCopyResults = ::copyResults,
                    onClose = ::finish,
                )
            }
        }
    }

    private fun copyResults(customText: String? = null) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val textToCopy = customText ?: viewModel.exportText()
        clipboard.setPrimaryClip(ClipData.newPlainText("Risultati del test a soglie", textToCopy))
    }
}
