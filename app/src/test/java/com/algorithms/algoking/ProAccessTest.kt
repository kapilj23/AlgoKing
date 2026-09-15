package com.algorithms.algoking

import com.algorithms.algoking.billing.AccessDecision
import com.algorithms.algoking.billing.ProAccess
import com.algorithms.algoking.billing.ProEntitlement
import com.algorithms.algoking.engine.core.AlgorithmId
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

    /** The twelve the paywall sells, by name, in library order. */
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
    )

    @Test
    fun `exactly twelve lessons are Pro, and they are the Advanced shelf`() {
        val pro = algorithmLibrary.filter { ProAccess.requiresPro(it.category) }
        assertEquals(12, pro.size)
        assertEquals(expectedPro, pro.map { it.title })
        // No duplicates — the paywall's list is what the learner is buying.
        assertEquals(pro.size, pro.map { it.id }.toSet().size)
    }

    @Test
    fun `every other lesson is free, including the newest sort`() {
        val free = algorithmLibrary.filterNot { ProAccess.requiresPro(it.category) }
        assertEquals(12, free.size)
        // Counting Sort ships free and must stay that way.
        assertTrue(free.any { it.id == AlgorithmId.COUNTING_SORT })
        assertTrue(free.any { it.id == AlgorithmId.BINARY_SEARCH })
        assertTrue(free.any { it.id == AlgorithmId.HASH_MAP })
        // ...and no free lesson is filed under the Pro category by accident.
        assertTrue(free.none { it.category == ProAccess.PRO_CATEGORY })
    }

    @Test
    fun `every lesson in the library is either free or Pro, and never both`() {
        // 24 lessons, and the partition is total: a lesson that fell out of both
        // sets would be one the access check has no answer for.
        assertEquals(24, algorithmLibrary.size)
        val pro = algorithmLibrary.count { ProAccess.requiresPro(it.category) }
        val free = algorithmLibrary.count { !ProAccess.requiresPro(it.category) }
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
                ProAccess.decide("Sorting", entitlement),
            )
        }
    }

    @Test
    fun `a Pro lesson opens only for a verified entitlement`() {
        assertEquals(
            AccessDecision.OpenLesson,
            ProAccess.decide(ProAccess.PRO_CATEGORY, ProEntitlement.Pro),
        )
        assertEquals(
            AccessDecision.ShowPaywall,
            ProAccess.decide(ProAccess.PRO_CATEGORY, ProEntitlement.Free),
        )
    }

    @Test
    fun `an unknown entitlement is not an entitlement`() {
        // Showing the paywall to someone who turns out to own Pro is a moment's
        // friction the purchase state corrects. Opening a paid lesson for someone
        // who does not own it is giving it away.
        assertEquals(
            AccessDecision.ShowPaywall,
            ProAccess.decide(ProAccess.PRO_CATEGORY, ProEntitlement.Unknown),
        )
        assertFalse(ProEntitlement.Unknown.isPro)
        assertFalse(ProEntitlement.Free.isPro)
        assertTrue(ProEntitlement.Pro.isPro)
    }

    @Test
    fun `every Pro lesson in the library resolves to the paywall without Pro`() {
        // The rule applied to the real catalogue rather than to a string: all twelve
        // are locked, and none of the twelve free ones is.
        for (entry in algorithmLibrary) {
            val decision = ProAccess.decide(entry.category, ProEntitlement.Free)
            val expected = if (entry.title in expectedPro) {
                AccessDecision.ShowPaywall
            } else {
                AccessDecision.OpenLesson
            }
            assertEquals(entry.title, expected, decision)
        }
    }

    @Test
    fun `Fibonacci is Pro, and the tap resolves both ways`() {
        val fibonacci = algorithmLibrary.single { it.id == AlgorithmId.FIBONACCI }

        // Filed under Advanced, which is the whole of the registration: no flag,
        // no billing change, no second taxonomy (ADR-032, ADR-041).
        assertEquals(ProAccess.PRO_CATEGORY, fibonacci.category)
        assertTrue(ProAccess.requiresPro(fibonacci.category))

        // A free learner is sent to the existing paywall...
        assertEquals(
            AccessDecision.ShowPaywall,
            ProAccess.decide(fibonacci.category, ProEntitlement.Free),
        )
        // ...as is one whose entitlement has not come back from the store yet.
        assertEquals(
            AccessDecision.ShowPaywall,
            ProAccess.decide(fibonacci.category, ProEntitlement.Unknown),
        )
        // ...and a subscriber goes straight into the lesson.
        assertEquals(
            AccessDecision.OpenLesson,
            ProAccess.decide(fibonacci.category, ProEntitlement.Pro),
        )
    }

    @Test
    fun `every free and Pro lesson is where it should be`() {
        // The safety claim, as a test: every free lesson is still free and every
        // Pro lesson is still Pro, whatever was added last.
        val free = algorithmLibrary
            .filterNot { ProAccess.requiresPro(it.category) }
            .map { it.title }
        assertEquals(
            listOf(
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
            ).sorted(),
            free.sorted(),
        )
        assertEquals(
            expectedPro.sorted(),
            algorithmLibrary
                .filter { ProAccess.requiresPro(it.category) }
                .map { it.title }
                .sorted(),
        )
    }

    @Test
    fun `Caesar Cipher is free, and opens for anyone`() {
        val caesar = algorithmLibrary.single { it.id == AlgorithmId.CAESAR_CIPHER }

        // Filed under its own category, which is not the Pro one — and that is the
        // whole of it. Access derives from the category, so a lesson outside the
        // Advanced shelf is free with nothing saying so (ADR-032, ADR-041).
        assertEquals("Encryption", caesar.category)
        assertFalse(ProAccess.requiresPro(caesar.category))

        // No entitlement, an unknown one, or Pro — the lesson opens either way, and
        // the paywall is never reached.
        for (entitlement in listOf(
            ProEntitlement.Unknown,
            ProEntitlement.Free,
            ProEntitlement.Pro,
        )) {
            assertEquals(
                "Caesar Cipher with $entitlement",
                AccessDecision.OpenLesson,
                ProAccess.decide(caesar.category, entitlement),
            )
        }
    }

    @Test
    fun `the Encryption category exists and holds only free lessons`() {
        // A chip that filters to an empty list is a dead end, and one that filters
        // to a locked list would be a shelf the learner cannot open.
        val encryption = algorithmLibrary.filter { it.category == "Encryption" }
        assertTrue(encryption.isNotEmpty())
        assertTrue(encryption.none { ProAccess.requiresPro(it.category) })
    }

    @Test
    fun `Pro unlocks every locked lesson and changes nothing about the free ones`() {
        for (entry in algorithmLibrary) {
            assertEquals(
                entry.title,
                AccessDecision.OpenLesson,
                ProAccess.decide(entry.category, ProEntitlement.Pro),
            )
        }
    }
}
