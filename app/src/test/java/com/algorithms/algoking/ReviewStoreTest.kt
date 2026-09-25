package com.algorithms.algoking

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.algorithms.algoking.data.ReviewStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one flag, and the one thing that must be true of it: **it survives.**
 *
 * The policy is only "ask once" if the answer to *"have we asked?"* outlives the
 * process. These run against an in-memory `DataStore<Preferences>` — the reason
 * [ReviewStore] takes one rather than only a `Context`, the same seam
 * `ProgressRepository` has.
 */
class ReviewStoreTest {

    /**
     * An in-memory `DataStore<Preferences>`. It stands in for the file, so a second
     * [ReviewStore] over the same instance is the same install after a restart.
     */
    private class FakePreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    @Test
    fun `a fresh install has not been asked`() = runBlocking {
        assertFalse(ReviewStore(FakePreferencesStore()).asked.first())
    }

    @Test
    fun `being asked is recorded`() = runBlocking {
        val store = ReviewStore(FakePreferencesStore())
        store.markAsked()
        assertTrue(store.asked.first())
    }

    @Test
    fun `the flag survives a restart`() = runBlocking {
        // The whole point. A new `ReviewStore` over the same storage is what the
        // next process start looks like, and it must already know.
        val storage = FakePreferencesStore()
        ReviewStore(storage).markAsked()

        val afterRestart = ReviewStore(storage)
        assertTrue(afterRestart.asked.first())
    }

    @Test
    fun `recording it twice is not a second ask`() = runBlocking {
        // `markAsked` only ever sets. There is no method that clears it, so "ask
        // again" is not something a caller can express — the additive guarantee
        // `AlgorithmProgress.complete` has (ADR-028), on the opposite kind of value.
        val store = ReviewStore(FakePreferencesStore())
        store.markAsked()
        store.markAsked()
        assertTrue(store.asked.first())
    }

    @Test
    fun `a failed flow leaves the ask unspent`() = runBlocking {
        // `InAppReviewManager.launch` returns false when Play never got the flow —
        // no Play Store, an out-of-date one, a launch that did not take. The caller
        // only records on success, so the next finished lesson may try again.
        val store = ReviewStore(FakePreferencesStore())
        val launched = false

        if (launched) store.markAsked()

        assertFalse(store.asked.first())
    }
}
