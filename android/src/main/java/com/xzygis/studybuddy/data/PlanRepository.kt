package com.xzygis.studybuddy.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.planDataStore by preferencesDataStore(name = "study_buddy")

class PlanRepository(private val context: Context) {
    private val key = stringPreferencesKey("plan_database")
    private val mutex = Mutex()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    val database: Flow<PlanDatabase> = context.planDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::decode)

    suspend fun initialize() {
        update { database ->
            if (database.didSeedStarterPlans) database
            else database.copy(
                records = database.records + StarterPlans.all.map(::PlanRecord),
                didSeedStarterPlans = true,
            )
        }
    }

    suspend fun current(): PlanDatabase = database.first()

    suspend fun update(transform: (PlanDatabase) -> PlanDatabase): PlanDatabase = mutex.withLock {
        var result = PlanDatabase()
        context.planDataStore.edit { preferences ->
            result = transform(decode(preferences))
            result.validate()
            preferences[key] = json.encodeToString(PlanDatabase.serializer(), result)
        }
        result
    }

    private fun decode(preferences: Preferences): PlanDatabase {
        val raw = preferences[key] ?: return PlanDatabase()
        return json.decodeFromString(PlanDatabase.serializer(), raw).also { it.validate() }
    }
}
