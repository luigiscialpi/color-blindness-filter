package com.luigiscialpi.colorblindnessfilter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import com.luigiscialpi.colorblindnessfilter.domain.LuminanceShiftFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistenza delle impostazioni su DataStore Preferences.
 *
 * Il DataStore arriva dal costruttore: in produzione è `context.filterSettingsDataStore`,
 * nei test un file temporaneo.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<FilterSettings> = dataStore.data.map { it.toSettings() }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setIntensity(intensity: Double) {
        require(FilterSettings.isValidIntensity(intensity)) { "intensity fuori intervallo: $intensity" }
        dataStore.edit { it[INTENSITY] = intensity }
    }

    private fun Preferences.toSettings(): FilterSettings {
        val defaults = FilterSettings.DEFAULT
        val stored = this[INTENSITY]
        // Un valore memorizzato non valido (file alterato) non deve far fallire la lettura.
        val intensity = if (stored != null && stored.isFinite()) {
            stored.coerceIn(LuminanceShiftFilter.MIN_BETA, LuminanceShiftFilter.MAX_BETA)
        } else {
            defaults.intensity
        }
        return FilterSettings(enabled = this[ENABLED] ?: defaults.enabled, intensity = intensity)
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val INTENSITY = doublePreferencesKey("intensity")
    }
}
