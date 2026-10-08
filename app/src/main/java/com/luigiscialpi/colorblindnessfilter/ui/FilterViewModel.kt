package com.luigiscialpi.colorblindnessfilter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luigiscialpi.colorblindnessfilter.FilterController
import com.luigiscialpi.colorblindnessfilter.data.FilterSettings
import com.luigiscialpi.colorblindnessfilter.data.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilterViewModel(
    private val repository: SettingsRepository,
    private val controller: FilterController,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(FilterUiState())
    val state: StateFlow<FilterUiState> = _state.asStateFlow()

    // Le chiamate root sono bloccanti: un solo consumatore le esegue in ordine e, se
    // arrivano più richieste mentre una è in corso, resta solo l'ultima (trascinamento dello slider).
    private val requests = Channel<FilterSettings>(Channel.CONFLATED)

    init {
        // ponytail: all'avvio si legge solo lo stato salvato e non lo si applica (la matrice
        // sopravvive in sistema). Se il telefono è stato riavviato la matrice è persa finché
        // non c'è lo script Magisk; chi agisce prima di questa lettura (millisecondi) viene
        // sovrascritto dal valore letto.
        viewModelScope.launch {
            _state.update { it.copy(settings = repository.settings.first()) }
        }
        viewModelScope.launch {
            for (settings in requests) {
                val result = withContext(ioDispatcher) { controller.apply(settings) }
                _state.update { it.copy(lastResult = result) }
            }
        }
    }

    fun onEnabledChange(enabled: Boolean) {
        val settings = _state.value.settings.copy(enabled = enabled)
        _state.update { it.copy(settings = settings) }
        requests.trySend(settings)
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    /** Anteprima dal vivo durante il trascinamento: applica ma non salva (vedi [onIntensityChangeFinished]). */
    fun onIntensityChange(intensity: Double) {
        val settings = _state.value.settings.copy(intensity = intensity)
        _state.update { it.copy(settings = settings) }
        if (settings.enabled) requests.trySend(settings)
    }

    fun onIntensityChangeFinished() {
        val intensity = _state.value.settings.intensity
        viewModelScope.launch { repository.setIntensity(intensity) }
    }
}
