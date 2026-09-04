package com.ttele.algoking.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.progress.LearningProgress
import com.ttele.algoking.engine.progress.ProgressCodec
import com.ttele.algoking.engine.progress.Stage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single source of truth for how far each algorithm has been learned —
 * ARCHITECTURE.md §8.1.
 *
 * Stage completion is three latched booleans per algorithm: small, flat, written a
 * handful of times per lesson and never queried by range or aggregated. That is
 * exactly what DataStore Preferences is for. The Room `AlgorithmProgress` entity
 * remains the home for the things that genuinely need a database — attempt history,
 * personal bests, streak dates — and none of that belongs on the Home ring.
 *
 * Nothing here stores a percentage. The percentage is derived by
 * [com.ttele.algoking.engine.progress.AlgorithmProgress], so a stored number cannot
 * drift out of step with the stages it summarises.
 */
class ProgressRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.progressDataStore)

    /**
     * Reactive: a stage completed anywhere in the app reaches Home on the next
     * frame, with no restart and no manual refresh.
     */
    val progress: Flow<LearningProgress> =
        store.data.map { ProgressCodec.decode(it[COMPLETED] ?: emptySet()) }

    /**
     * Records one stage as finished.
     *
     * Additive by construction — the stored set only ever grows — so a retry, a
     * or a repeat practice run cannot subtract progress.
     */
    suspend fun complete(id: AlgorithmId, stage: Stage) {
        store.edit { prefs ->
            val current = ProgressCodec.decode(prefs[COMPLETED] ?: emptySet())
            prefs[COMPLETED] = ProgressCodec.encode(current.complete(id, stage))
        }
    }

    private companion object {
        val COMPLETED = stringSetPreferencesKey("completed_stages")
    }
}

private val Context.progressDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "learning_progress")
