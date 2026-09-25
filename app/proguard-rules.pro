# R8 rules for the release build — ARCHITECTURE.md §12.
#
# This file is deliberately almost empty, and that is the point. Every library
# this app uses ships its own consumer rules, so the correct amount of
# hand-written configuration is whatever R8 actually asks for and nothing more.
#
# **No blanket rules.** `-keep class **`, `-keepnames **` and friends are not
# shrinking; they are shrinking turned off with extra steps, and they hide the
# one thing this file is for — the specific reflective access some dependency
# genuinely makes. If a rule is needed here it names a class and says why.


# -- Play In-App Review (ADR-056) ---------------------------------------------
#
# `review-ktx:2.0.2` resolves `play-services-basement:18.4.0`, and its compiled
# classes carry an annotation — `@NoNullnessRewrite` — that was added to
# basement *after* that version. So the reference is real and the class is
# genuinely absent, which is what R8 is reporting.
#
# It is an annotation and nothing reads it at runtime: it survives only in the
# annotation table of a lambda `review-ktx` synthesises for an `OnSuccessListener`,
# and ART ignores annotation entries whose type is missing. There is therefore
# nothing to `-keep` — the class does not exist in this app and does not need to.
# Telling R8 the absence is expected is the whole of the fix.
#
# Scoped to the one class R8 named. Not `com.google.android.gms.**`, which would
# also silence a genuinely missing Play Services class later on.
#
# The alternatives were worse: pinning a newer `play-services-basement` in the
# catalog adds a transitive version to keep in step for the sake of an
# annotation, and dropping to non-KTX `review` means hand-rolling the coroutine
# wrapper that `requestReview`/`launchReview` already are.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
