package com.algorithms.algoking.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether the app has ever asked for a review — ARCHITECTURE.md §8.1.
 *
 * One boolean, written at most once in the life of an install and never queried by
 * range or aggregated, which is exactly the "small, flat, never queried" case
 * DataStore Preferences is for. It sits beside `ProgressRepository` and is shaped
 * the same way, because it is the same kind of fact.
 *
 * ### One flag, and deliberately not four
 *
 * A timestamp, an attempt counter and a completed-lessons watermark were all
 * considered and all left out. They only earn their keep if the policy has a
 * cooldown or a second milestone to evaluate, and it has neither: the automatic ask
 * happens **once**. Persisting state that nothing reads is how a store grows fields
 * whose meaning nobody can reconstruct later — and if a future policy does want a
 * cooldown, adding the timestamp then is a smaller change than keeping a field
 * warm on the chance.
 *
 * ### It is additive, for the reason progress is
 *
 * [markAsked] only ever sets the flag. There is no method that clears it, so
 * "ask again" is not a thing a caller can express — the same structural guarantee
 * `AlgorithmProgress.complete` has (ADR-028), applied to the opposite kind of
 * value. It survives a restart, process death, navigation and recomposition,
 * because it is on disk and not in a composition.
 */
class ReviewStore(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.reviewDataStore)

    /**
     * Whether Play's review flow has already been launched for this install.
     *
     * Read at the moment the decision is made rather than collected into
     * composition: a flow collected into state starts at its default, and a default
     * of `false` read a beat before the real value arrived is exactly how a learner
     * gets asked a second time.
     */
    val asked: Flow<Boolean> = store.data.map { it[ASKED] ?: false }

    /**
     * Records that the flow was launched.
     *
     * Called **only** after `InAppReviewManager.launch` reports success, so a
     * request that failed before Play saw it does not burn the one ask. Note what
     * this does not record: whether Play showed anything, and whether the learner
     * rated. Play reports neither, to any app.
     */
    suspend fun markAsked() {
        store.edit { prefs -> prefs[ASKED] = true }
    }

    private companion object {
        val ASKED = booleanPreferencesKey("review_prompt_attempted")
    }
}

private val Context.reviewDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "review_prompt")
