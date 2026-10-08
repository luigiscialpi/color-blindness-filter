package com.luigiscialpi.colorblindnessfilter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** DataStore delle impostazioni: un solo file, gestito da `preferencesDataStore`. */
val Context.filterSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "filter_settings")
