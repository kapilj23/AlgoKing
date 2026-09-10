package com.ttele.algoking

import com.ttele.algoking.billing.AccessDecision
import com.ttele.algoking.billing.ProAccess
import com.ttele.algoking.billing.ProEntitlement
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.ui.screens.algorithmLibrary
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

    /** The ten the paywall sells, by name, in library order. */
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
    )

    @Test
    fun `exactly ten lessons are Pro, and they are the Advanced shelf`() {
        val pro = algorithmLibrary.filter { ProAccess.requiresPro(it.category) }
        assertEquals(10, pro.size)
        assertEquals(expectedPro, pro.map { it.title })
        // No duplicates — the paywall's list is what the learner is buying.
        assertEquals(pro.size, pro.map { it.id }.toSet().size)
    }

    @Test
    fun `every other lesson is free, including the newest sort`() {
        val free = algorithmLibrary.filterNot { ProAccess.requiresPro(it.category) }
        assertEquals(11, free.size)
        // Counting Sort ships free and must stay that way.
        assertTrue(free.any { it.id == AlgorithmId.COUNTING_SORT })
        assertTrue(free.any { it.id == AlgorithmId.BINARY_SEARCH })
        assertTrue(free.any { it.id == AlgorithmId.HASH_MAP })
        // ...and no free lesson is filed under the Pro category by accident.
        assertTrue(free.none { it.category == ProAccess.PRO_CATEGORY })
    }

    @Test
    fun `every lesson in the library is either free or Pro, and never both`() {
        // 21 lessons, and the partition is total: a lesson that fell out of both
        // sets would be one the access check has no answer for.
        assertEquals(21, algorithmLibrary.size)
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
        // The rule applied to the real catalogue rather than to a string: all ten
        // are locked, and none of the eleven free ones is.
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
