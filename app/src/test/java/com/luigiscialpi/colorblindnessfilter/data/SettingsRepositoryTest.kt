package com.luigiscialpi.colorblindnessfilter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
    )

    @Test
    fun senzaValoriMemorizzatiRestituisceIValoriIniziali() {
        runBlocking {
            val repository = SettingsRepository(newStore())

            assertEquals(FilterSettings.DEFAULT, repository.settings.first())
        }
    }

    @Test
    fun setEnabledVieneMemorizzatoELasciaInvariataLIntensita() {
        runBlocking {
            val repository = SettingsRepository(newStore())

            repository.setEnabled(true)

            assertEquals(FilterSettings.DEFAULT.copy(enabled = true), repository.settings.first())
        }
    }

    @Test
    fun setIntensityVieneMemorizzataELasciaInvariatoLoStato() {
        runBlocking {
            val repository = SettingsRepository(newStore())

            repository.setIntensity(0.3)

            assertEquals(FilterSettings.DEFAULT.copy(intensity = 0.3), repository.settings.first())
        }
    }

    @Test
    fun duePersistenzeSuccessiveSiSommano() {
        runBlocking {
            val repository = SettingsRepository(newStore())

            repository.setEnabled(true)
            repository.setIntensity(0.25)

            assertEquals(FilterSettings(enabled = true, intensity = 0.25), repository.settings.first())
        }
    }

    @Test
    fun setIntensityRifiutaValoriFuoriIntervallo() {
        val repository = SettingsRepository(newStore())
        for (bad in listOf(-0.01, 0.51, Double.NaN)) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.setIntensity(bad) }
            }
        }
    }

    @Test
    fun unValoreMemorizzatoFuoriIntervalloVieneRiportatoAlLimite() {
        runBlocking {
            val store = newStore()
            store.edit { it[doublePreferencesKey("intensity")] = 0.9 }

            assertEquals(0.5, SettingsRepository(store).settings.first().intensity, 0.0)
        }
    }

    @Test
    fun unValoreMemorizzatoNonNumericoRiportaLIntensitaIniziale() {
        runBlocking {
            val store = newStore()
            store.edit { it[doublePreferencesKey("intensity")] = Double.NaN }

            val intensity = SettingsRepository(store).settings.first().intensity

            assertEquals(FilterSettings.DEFAULT.intensity, intensity, 0.0)
        }
    }
}
