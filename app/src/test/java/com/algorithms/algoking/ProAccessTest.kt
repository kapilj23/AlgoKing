package com.algorithms.algoking

import com.algorithms.algoking.billing.AccessDecision
import com.algorithms.algoking.billing.ProAccess
import com.algorithms.algoking.billing.ProEntitlement
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.ui.screens.AlgorithmEntry
import com.algorithms.algoking.ui.screens.algorithmCategories
import com.algorithms.algoking.ui.screens.algorithmLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What costs money, and what a tap on it does.
 *
 * The rules are a truth table, so they are tested as one — no device, no store,
 * no Compose. The same call `AdPolicy` was specified with (ADR-008).
 */
class ProAccessTest {

    /** The thirteen the paywall sells, by name, in library order. */
    private val expectedPro = listOf(
        "Two Pointers",
        "Prefix Sum",
        "Graph DFS",
        "Graph BFS",
        "Dijkstra",
        "Binary Search Tree",
        "AVL Tree",
        "Binary Tree — Inorder",
        "Binary Tree — Preorder",
        "Binary Tree — Postorder",
        "Fibonacci",
        "0/1 Knapsack",
        // Pro without being on the Advanced shelf — the first lesson for which
        // those two are not the same thing (ADR-049).
        "AES",
    )

    /** Every lesson that is free, by name. */
    private val expectedFree = listOf(
        "Binary Search",
        "Bubble Sort",
        "Selection Sort",
        "Insertion Sort",
        "Merge Sort",
        "Quick Sort",
        "Counting Sort",
        "Stack",
        "Queue",
        "Linked List",
        "Hash Map",
        "Caesar Cipher",
        "XOR Cipher",
        "SHA-256 Hashing",
    )

    private fun AlgorithmEntry.locked(entitlement: ProEntitlement) =
        ProAccess.decide(category, id, entitlement)

    private fun proTitles() = algorithmLibrary
        .filter { ProAccess.requiresPro(it.category, it.id) }
        .map { it.title }

    private fun freeTitles() = algorithmLibrary
        .filterNot { ProAccess.requiresPro(it.category, it.id) }
        .map { it.title }

    @Test
    fun `exactly thirteen lessons are Pro`() {
        val pro = algorithmLibrary.filter { ProAccess.requiresPro(it.category, it.id) }
        assertEquals(13, pro.size)
        assertEquals(expectedPro, pro.map { it.title })
        // No duplicates — the paywall's list is what the learner is buying.
        assertEquals(pro.size, pro.map { it.id }.toSet().size)
    }

    /**
     * Rule 1 is unchanged: the Advanced shelf is wholly Pro.
     *
     * The failure this guards is the one ADR-032 named — an Advanced lesson that
     * is accidentally free. Adding rule 2 could not introduce it, and this says so.
     */
    @Test
    fun `every Advanced lesson is Pro, and no free lesson is Advanced`() {
        val advanced = algorithmLibrary.filter { it.category == ProAccess.PRO_CATEGORY }
        assertEquals(12, advanced.size)
        assertTrue(advanced.all { ProAccess.requiresPro(it.category, it.id) })
        assertTrue(
            algorithmLibrary
                .filterNot { ProAccess.requiresPro(it.category, it.id) }
                .none { it.category == ProAccess.PRO_CATEGORY },
        )
    }

    /** Rule 2 is deliberately small, and every id in it is a real lesson. */
    @Test
    fun `the named Pro lessons are real, and there is only one`() {
        assertEquals(setOf(AlgorithmId.AES), ProAccess.PRO_LESSONS)
        ProAccess.PRO_LESSONS.forEach { id ->
            assertTrue("$id is in the library", algorithmLibrary.any { it.id == id })
            // A lesson named here must not also be on the Advanced shelf, or the
            // two rules would be saying the same thing in two places.
            assertTrue(
                "$id is named because its category does not make it Pro",
                algorithmLibrary.single { it.id == id }.category != ProAccess.PRO_CATEGORY,
            )
        }
    }

    @Test
    fun `every other lesson is free, including the newest ones`() {
        val free = algorithmLibrary.filterNot { ProAccess.requiresPro(it.category, it.id) }
        assertEquals(14, free.size)
        // Counting Sort ships free and must stay that way.
        assertTrue(free.any { it.id == AlgorithmId.COUNTING_SORT })
        assertTrue(free.any { it.id == AlgorithmId.BINARY_SEARCH })
        assertTrue(free.any { it.id == AlgorithmId.HASH_MAP })
    }

    @Test
    fun `every lesson in the library is either free or Pro, and never both`() {
        // 27 lessons, and the partition is total: a lesson that fell out of both
        // sets would be one the access check has no answer for.
        assertEquals(27, algorithmLibrary.size)
        val pro = algorithmLibrary.count { ProAccess.requiresPro(it.category, it.id) }
        val free = algorithmLibrary.count { !ProAccess.requiresPro(it.category, it.id) }
        assertEquals(algorithmLibrary.size, pro + free)
    }

    @Test
    fun `a free lesson opens for anyone, whatever the store says`() {
        for (entitlement in listOf(
            ProEntitlement.Unknown,
            ProEntitlement.Free,
            ProEntitlement.Pro,
        )) {
            assertEquals(
                "free lesson with $entitlement",
                AccessDecision.OpenLesson,
                ProAccess.decide("Sorting", AlgorithmId.BUBBLE_SORT, entitlement),
            )
        }
    }

    @Test
    fun `a Pro lesson opens only for a verified entitlement`() {
        assertEquals(
            AccessDecision.OpenLesson,
            ProAccess.decide(ProAccess.PRO_CATEGORY, AlgorithmId.FIBONACCI, ProEntitlement.Pro),
        )
        assertEquals(
            AccessDecision.ShowPaywall,
            ProAccess.decide(ProAccess.PRO_CATEGORY, AlgorithmId.FIBONACCI, ProEntitlement.Free),
        )
    }

    @Test
    fun `an unknown entitlement is not an entitlement`() {
        // Showing the paywall to someone who turns out to own Pro is a moment's
        // friction the purchase state corrects. Opening a paid lesson for someone
        // who does not own it is giving it away.
        assertEquals(
            AccessDecision.ShowPaywall,
            ProAccess.decide(
                ProAccess.PRO_CATEGORY,
                AlgorithmId.FIBONACCI,
                ProEntitlement.Unknown,
            ),
        )
        assertFalse(ProEntitlement.Unknown.isPro)
        assertFalse(ProEntitlement.Free.isPro)
        assertTrue(ProEntitlement.Pro.isPro)
    }

    @Test
    fun `every Pro lesson in the library resolves to the paywall without Pro`() {
        // The rule applied to the real catalogue rather than to a string: all
        // thirteen are locked, and none of the fourteen free ones is.
        for (entry in algorithmLibrary) {
            val expected = if (entry.title in expectedPro) {
                AccessDecision.ShowPaywall
            } else {
                AccessDecision.OpenLesson
            }
            assertEquals(entry.title, expected, entry.locked(ProEntitlement.Free))
        }
    }

    @Test
    fun `Fibonacci is Pro, and the tap resolves both ways`() {
        val fibonacci = algorithmLibrary.single { it.id == AlgorithmId.FIBONACCI }

        // Filed under Advanced, which is the whole of the registration: no flag,
        // no billing change, no second taxonomy (ADR-032, ADR-041).
        assertEquals(ProAccess.PRO_CATEGORY, fibonacci.category)
        assertTrue(ProAccess.requiresPro(fibonacci.category, fibonacci.id))

        // A free learner is sent to the existing paywall...
        assertEquals(AccessDecision.ShowPaywall, fibonacci.locked(ProEntitlement.Free))
        // ...as is one whose entitlement has not come back from the store yet.
        assertEquals(AccessDecision.ShowPaywall, fibonacci.locked(ProEntitlement.Unknown))
        // ...and a subscriber goes straight into the lesson.
        assertEquals(AccessDecision.OpenLesson, fibonacci.locked(ProEntitlement.Pro))
    }

    // ── AES ──────────────────────────────────────────────────────────────────

    /**
     * AES is Pro, and it is Pro for a reason the category cannot express.
     *
     * Every other paid lesson is paid because of its shelf. This one is on the
     * Cryptography shelf — where a block cipher belongs, beside the two ciphers and
     * the hash it builds on — and is named in `ProAccess.PRO_LESSONS` instead
     * (ADR-049). Both halves are asserted here, because getting either wrong is how
     * a paid lesson ships free.
     */
    @Test
    fun `AES is registered as PRO, on the Cryptography shelf`() {
        val aes = algorithmLibrary.single { it.id == AlgorithmId.AES }

        assertEquals("AES", aes.title)
        assertEquals("Cryptography", aes.category)
        // Not filed on the Pro shelf...
        assertFalse(aes.category == ProAccess.PRO_CATEGORY)
        // ...and Pro all the same.
        assertTrue(ProAccess.requiresPro(aes.category, aes.id))
        assertTrue(AlgorithmId.AES in ProAccess.PRO_LESSONS)
    }

    @Test
    fun `a free learner tapping AES gets the paywall, and cannot bypass it`() {
        val aes = algorithmLibrary.single { it.id == AlgorithmId.AES }

        // The two states a learner who has not paid can be in, and both are refused.
        assertEquals(AccessDecision.ShowPaywall, aes.locked(ProEntitlement.Free))
        assertEquals(AccessDecision.ShowPaywall, aes.locked(ProEntitlement.Unknown))

        // There is no other answer. `decide` is total over the entitlements, so
        // "not entitled" cannot resolve to anything but the paywall — and the only
        // way into the lesson is the branch that returns OpenLesson.
        listOf(ProEntitlement.Free, ProEntitlement.Unknown).forEach { entitlement ->
            assertFalse(
                "AES must never open for $entitlement",
                aes.locked(entitlement) == AccessDecision.OpenLesson,
            )
        }
    }

    @Test
    fun `a Pro learner tapping AES opens the lesson directly`() {
        val aes = algorithmLibrary.single { it.id == AlgorithmId.AES }
        assertEquals(AccessDecision.OpenLesson, aes.locked(ProEntitlement.Pro))
    }

    /**
     * The regression claim, as a test.
     *
     * Adding AES changed the signature of the access rule, so this asserts the
     * thing that actually matters: every lesson that was free before is still free,
     * and every lesson that was Pro before is still Pro.
     */
    @Test
    fun `adding AES moved nothing else`() {
        assertEquals(expectedFree.sorted(), freeTitles().sorted())
        assertEquals(expectedPro.sorted(), proTitles().sorted())

        // The twelve that were Pro before AES are still exactly those twelve.
        assertEquals(expectedPro - "AES", proTitles() - "AES")
    }

    // ── The Cryptography shelf ───────────────────────────────────────────────

    @Test
    fun `XOR Cipher is free, and opens for anyone`() {
        val xor = algorithmLibrary.single { it.id == AlgorithmId.XOR_CIPHER }

        // Filed under Cryptography, which is not the Pro category, and not named as
        // a Pro lesson — and that is the whole of the registration.
        assertEquals("Cryptography", xor.category)
        assertFalse(ProAccess.requiresPro(xor.category, xor.id))

        for (entitlement in listOf(
            ProEntitlement.Unknown,
            ProEntitlement.Free,
            ProEntitlement.Pro,
        )) {
            assertEquals(
                "XOR Cipher with $entitlement",
                AccessDecision.OpenLesson,
                xor.locked(entitlement),
            )
        }
    }

    @Test
    fun `Caesar Cipher is free, and opens for anyone`() {
        val caesar = algorithmLibrary.single { it.id == AlgorithmId.CAESAR_CIPHER }

        assertEquals("Cryptography", caesar.category)
        assertFalse(ProAccess.requiresPro(caesar.category, caesar.id))

        for (entitlement in listOf(
            ProEntitlement.Unknown,
            ProEntitlement.Free,
            ProEntitlement.Pro,
        )) {
            assertEquals(
                "Caesar Cipher with $entitlement",
                AccessDecision.OpenLesson,
                caesar.locked(entitlement),
            )
        }
    }

    @Test
    fun `SHA-256 Hashing is free, and opens for anyone`() {
        val sha = algorithmLibrary.single { it.id == AlgorithmId.SHA_256 }

        assertEquals("Cryptography", sha.category)
        assertFalse(ProAccess.requiresPro(sha.category, sha.id))

        for (entitlement in listOf(
            ProEntitlement.Unknown,
            ProEntitlement.Free,
            ProEntitlement.Pro,
        )) {
            assertEquals(
                "SHA-256 Hashing with $entitlement",
                AccessDecision.OpenLesson,
                sha.locked(entitlement),
            )
        }
    }

    /**
     * The shelf now holds four lessons: three free and one Pro.
     *
     * It used to hold only free ones, and that was worth asserting while it was
     * true. What replaces it is the thing that is true now and still worth
     * protecting: **the three free ciphers are still free**, and exactly one lesson
     * on this shelf is not (ADR-049).
     */
    @Test
    fun `the Cryptography shelf holds three free lessons and one Pro one`() {
        val cryptography = algorithmLibrary.filter { it.category == "Cryptography" }
        assertEquals(4, cryptography.size)

        val locked = cryptography.filter { ProAccess.requiresPro(it.category, it.id) }
        assertEquals(listOf("AES"), locked.map { it.title })

        assertEquals(
            listOf("Caesar Cipher", "SHA-256 Hashing", "XOR Cipher"),
            cryptography
                .filterNot { ProAccess.requiresPro(it.category, it.id) }
                .map { it.title }
                .sorted(),
        )

        // The shelf is named for what it holds: ciphers and one hash function.
        // "Encryption" would file SHA-256 under the exact word its lesson exists to
        // correct, on the Home screen, before the learner opens anything (ADR-048).
        assertTrue(algorithmLibrary.none { it.category == "Encryption" })
        assertTrue("Cryptography" in algorithmCategories)
        assertTrue("Encryption" !in algorithmCategories)
    }

    @Test
    fun `Pro unlocks every locked lesson and changes nothing about the free ones`() {
        for (entry in algorithmLibrary) {
            assertEquals(
                entry.title,
                AccessDecision.OpenLesson,
                entry.locked(ProEntitlement.Pro),
            )
        }
    }
}
