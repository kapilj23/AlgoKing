package com.algorithms.algoking.devtools

import com.algorithms.algoking.billing.ProEntitlement

/**
 * **The release build has no entitlement override, and this file is the proof.**
 *
 * It is the `src/release/` half of a two-source-set pair. The debug half can make
 * a build pretend to be Free or Pro for testing; this one returns what the store
 * said and has no other behaviour, no configuration to read, and no way to be
 * switched on.
 *
 * ### Why a source set rather than a flag
 *
 * ADR-041 considered a debug flag that grants Pro and rejected it: *"it is one
 * merge away from shipping, and it is the exact fake entitlement the design exists
 * to prevent."* That objection is about a runtime check living in shared code —
 * `if (BuildConfig.DEBUG)` is one edit, one bad merge or one inverted condition
 * away from being wrong in release, and nothing about the build stops it.
 *
 * Source-set separation answers it structurally instead. The overriding code is
 * **not compiled into the release variant at all**: there is no branch to invert,
 * because the other implementation does not exist in that binary. The constant it
 * reads is emitted for the debug build type only, so release would not even
 * compile against it.
 *
 * So the invariant ADR-041 protects is intact. In a release build, Pro still comes
 * from a verified, acknowledged Play purchase and from nowhere else.
 */
object DebugEntitlement {

    /**
     * Returns [actual] unchanged, always.
     *
     * The signature matches the debug implementation so the one call site in
     * `MainActivity` is the same in both variants.
     */
    fun override(actual: ProEntitlement): ProEntitlement = actual

    /** Whether this build can pretend. It cannot. */
    const val IS_AVAILABLE: Boolean = false

    /** What a build is pretending to be, for a debug banner. Never anything here. */
    val activeLabel: String? = null
}
