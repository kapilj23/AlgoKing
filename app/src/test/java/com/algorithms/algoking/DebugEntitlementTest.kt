package com.algorithms.algoking

import com.algorithms.algoking.billing.ProEntitlement
import com.algorithms.algoking.devtools.DebugEntitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The debug-only entitlement override, and — more importantly — the release
 * build's inability to have one.
 *
 * Unit tests run against the **debug** variant, so `DebugEntitlement` here is the
 * overriding implementation. The release half cannot be loaded into this test at
 * all, which is the whole point of the arrangement and also why the assertions
 * about it read its source instead.
 */
class DebugEntitlementTest {

    // ── The rule, in the build that has one ──────────────────────────────────

    @Test
    fun `FREE forces free, whatever the store said`() {
        for (actual in listOf(ProEntitlement.Unknown, ProEntitlement.Free, ProEntitlement.Pro)) {
            assertEquals(ProEntitlement.Free, DebugEntitlement.applyOverride("FREE", actual))
        }
    }

    @Test
    fun `PRO forces pro, whatever the store said`() {
        for (actual in listOf(ProEntitlement.Unknown, ProEntitlement.Free, ProEntitlement.Pro)) {
            assertEquals(ProEntitlement.Pro, DebugEntitlement.applyOverride("PRO", actual))
        }
    }

    @Test
    fun `STORE overrides nothing`() {
        // The default, and what a fresh clone gets: production behaviour.
        for (actual in listOf(ProEntitlement.Unknown, ProEntitlement.Free, ProEntitlement.Pro)) {
            assertEquals(actual, DebugEntitlement.applyOverride("STORE", actual))
        }
    }

    @Test
    fun `anything unrecognised falls back to the store rather than to a guess`() {
        // A typo, a blank, a value from a future version of this file. Every one of
        // them means "do not pretend" — failing towards the real answer is the only
        // safe direction, because the unsafe one hands out Pro.
        for (setting in listOf("", "  ", "pro", "Free", "TRUE", "yes", "PREMIUM", "STOR")) {
            assertEquals(
                "`$setting` must not be honoured",
                ProEntitlement.Unknown,
                DebugEntitlement.applyOverride(setting, ProEntitlement.Unknown),
            )
        }
    }

    @Test
    fun `the override cannot invent an entitlement the type system does not have`() {
        // It substitutes one of the three real states and never a fourth thing —
        // so everything downstream, `ProAccess` and `AdPolicy` included, is reading
        // exactly the kind of value it would read in production.
        val produced = listOf("FREE", "PRO", "STORE")
            .map { DebugEntitlement.applyOverride(it, ProEntitlement.Unknown) }
        assertTrue(
            produced.all {
                it == ProEntitlement.Free || it == ProEntitlement.Pro ||
                    it == ProEntitlement.Unknown
            },
        )
    }

    // ── The build that has none ──────────────────────────────────────────────

    @Test
    fun `the release implementation cannot produce Pro`() {
        // It is not on this test's classpath — only the debug half is — so the
        // assertion is against its source. What matters is that it contains no
        // branch, no constant to read, and no mention of Pro at all: the whole
        // file is the identity function.
        val release = releaseSource()

        assertTrue(
            "the release override must return what it was given",
            release.contains("fun override(actual: ProEntitlement): ProEntitlement = actual"),
        )
        // Against the **code**, not the file: that file's whole job is explaining
        // why it does not do these things, so its prose necessarily names them.
        val code = release.codeOnly()
        for (forbidden in listOf("ProEntitlement.Pro", "BuildConfig", "DEBUG_ENTITLEMENT")) {
            assertTrue(
                "the release implementation must not use $forbidden — found in: $code",
                !code.contains(forbidden),
            )
        }
        assertTrue(
            "the release build must report that it cannot pretend",
            release.contains("IS_AVAILABLE: Boolean = false"),
        )
    }

    @Test
    fun `the two implementations offer the same surface`() {
        // If they drifted, the one call site in MainActivity would stop compiling
        // in one variant — but only when someone built that variant. Cheaper to
        // notice here.
        val release = releaseSource()
        val debug = File(
            "src/debug/java/com/algorithms/algoking/devtools/DebugEntitlement.kt",
        ).readText()

        for (member in listOf("fun override(", "IS_AVAILABLE", "activeLabel")) {
            assertTrue("release is missing $member", release.contains(member))
            assertTrue("debug is missing $member", debug.contains(member))
        }
    }

    @Test
    fun `the override is emitted for the debug build type only`() {
        // The constant the debug implementation reads is declared inside the
        // `debug { }` block. If it ever moved to `defaultConfig`, release would
        // gain a DEBUG_ENTITLEMENT field — and the separation would be a
        // convention again rather than a property of the build.
        val gradle = File("build.gradle.kts").readText()
        val debugBlock = gradle.substringAfter("debug {").substringBefore("}")

        assertTrue(
            "DEBUG_ENTITLEMENT must be declared in the debug build type",
            debugBlock.contains("DEBUG_ENTITLEMENT"),
        )
        assertEquals(
            "DEBUG_ENTITLEMENT must be declared exactly once",
            1,
            Regex("buildConfigField\\(\"String\", \"DEBUG_ENTITLEMENT\"").findAll(gradle).count(),
        )
        assertFalse(
            "the release build type must not declare it",
            gradle.substringAfter("release {").substringBefore("debug {")
                .contains("DEBUG_ENTITLEMENT"),
        )
    }

    @Test
    fun `the default is the store, so a fresh clone behaves like production`() {
        // Nobody should have to configure anything to get real behaviour, and a
        // machine with no `local.properties` entry must not silently be Pro.
        val gradle = File("build.gradle.kts").readText()
        assertTrue(
            "the fallback for the missing property must be STORE",
            gradle.contains("\"algoking.debug.entitlement\", \"STORE\""),
        )
    }

    @Test
    fun `the override value never lives in a tracked file`() {
        // It is read from `local.properties`, which `.gitignore` covers. That is
        // what makes this safe to commit: there is no value to remember to revert,
        // because the value is not in the repository.
        val gradle = File("build.gradle.kts").readText()
        assertTrue(
            "the setting must come from local.properties",
            gradle.contains("rootProject.file(\"local.properties\")"),
        )

        val ignored = File("../.gitignore").readText()
        assertTrue(
            "local.properties must stay git-ignored",
            ignored.lineSequence().any { it.trim() == "local.properties" },
        )
    }

    private fun releaseSource() = File(
        "src/release/java/com/algorithms/algoking/devtools/DebugEntitlement.kt",
    ).readText()

    /**
     * The file with its comments stripped.
     *
     * A source-reading test that matches on the whole file cannot tell *using* a
     * thing from *explaining why it is not used*, and these files do a lot of the
     * second. (Naive about `//` inside a string literal, which none of them has.)
     */
    private fun String.codeOnly(): String =
        replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lineSequence()
            .joinToString("\n") { line -> line.substringBefore("//") }
}
