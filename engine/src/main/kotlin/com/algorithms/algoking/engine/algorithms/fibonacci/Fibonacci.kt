package com.algorithms.algoking.engine.algorithms.fibonacci

import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Transition
import com.algorithms.algoking.engine.decision.Action
import com.algorithms.algoking.engine.decision.ActionOption
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What the learner does in Fibonacci.
 *
 * One action, because the lesson has one idea. Every beat asks the same question —
 * *what is `dp[i]`?* — and the answer is always `dp[i - 1] + dp[i - 2]`. Repeating
 * one judgement is the point here rather than a shortage of them: a recurrence is
 * something you apply, and applying it seven times is what turns it from a line of
 * notation into a habit.
 */
sealed interface FibonacciAction : Action {

    /** Commit [value] as the next table entry. */
    data class Fill(val value: Int) : FibonacciAction
}

/**
 * Immutable state — the single source of truth for the table, the cursor, the two
 * source cells and the expected answer.
 *
 * ### The representation
 *
 * ```
 * index   0   1   2   3   4   5   6   7   8
 * dp      0   1   1   2   3   5   8  13  21
 *
 * dp[0] = 0                         the base cases
 * dp[1] = 1
 * dp[i] = dp[i - 1] + dp[i - 2]     every other entry
 * ```
 *
 * [dp] holds only the entries computed so far, so its size *is* the build cursor
 * and the two can never disagree — the same call [PrefixSumState] makes about its
 * prefix array. The base cases are seeded by [FibonacciAlgorithm.initial] because
 * they are the *definition* of the sequence rather than a step a learner could get
 * wrong; asking for them would be the gesture-teaching trap PRODUCT_SPEC.md §3
 * warns about.
 */
data class FibonacciState(
    /** The index the lesson is working towards. `F(n)` is the answer. */
    val n: Int,
    /** Entries computed so far, starting with the base cases. Grows to `n + 1`. */
    val dp: List<Int>,
) {

    /** The index currently being computed. Past the end once the table is full. */
    val nextIndex: Int get() = dp.size

    val buildComplete: Boolean get() = dp.size >= n + 1

    /** `dp[i - 1]` — the nearer of the two cells the next entry reads. */
    val previous: Int? get() = if (buildComplete) null else dp.getOrNull(dp.size - 1)

    /** `dp[i - 2]` — the further of the two. */
    val beforeThat: Int? get() = if (buildComplete) null else dp.getOrNull(dp.size - 2)

    /**
     * The value [nextIndex] should take.
     *
     * **Nothing outside the engine computes a Fibonacci number.** The projector,
     * the walkthrough and the UI all read this or [table]; a second implementation
     * anywhere is how the picture and the algorithm start disagreeing.
     */
    val expectedNext: Int?
        get() {
            val a = previous ?: return null
            val b = beforeThat ?: return null
            return a + b
        }

    /** The complete table, whether or not the learner has built it yet. */
    val table: List<Int> get() = fibonacciTable(n)

    /** `F(n)` — the answer the lesson is working towards. */
    val answer: Int get() = table.last()

    /** The answer, once the learner has actually reached it. */
    val result: Int? get() = if (buildComplete) dp[n] else null

    val finished: Boolean get() = buildComplete

    /**
     * How many calls naive recursion would make to compute `F(n)`.
     *
     * This is the number the "repeated work" beat is built on, and it is computed
     * rather than asserted: 67 against a nine-cell table is an argument, and an
     * argument the learner can check.
     */
    val naiveCalls: Int get() = naiveCallCount(n)

    /** How many times naive recursion would recompute `F(k)` on the way to `F(n)`. */
    fun naiveRecomputesOf(k: Int): Int = naiveCallsTo(n, k)
}

/**
 * Fibonacci by tabulation — the second dynamic-programming lesson.
 *
 * ### What it teaches
 *
 * `F(n) = F(n - 1) + F(n - 2)` is the easy half. The idea worth having is what
 * goes wrong when you run that recurrence as written: `fib(8)` calls `fib(6)`
 * twice, `fib(5)` three times, `fib(3)` eight times, and 67 calls in total to
 * produce nine numbers. Every one of those repeats is work already done.
 *
 * Dynamic programming is the fix, and it has two shapes:
 *
 *  - **memoization** — run the recursion, but write each answer down the first
 *    time and read it back after that. Top-down.
 *  - **tabulation** — never recurse at all. Start at the base cases and build
 *    upward, so every value a cell needs is already sitting to its left.
 *    Bottom-up, and what this lesson does.
 *
 * Tabulation is the interactive half because it is the one with a picture: the
 * table *is* the algorithm, and "these two cells make that one" is a thing the
 * learner can point at. Memoization is the same O(n) idea arrived at from the
 * other end, and the walkthrough says so rather than leaving it out.
 *
 * ### Why every beat is a decision
 *
 * There is no `Probe.Mechanical` anywhere in this lesson. The addition *is* the
 * recurrence, so handing it to the app would leave the learner tapping through a
 * table that fills itself — which is the one thing PRODUCT_SPEC.md §1 says a
 * lesson must not be. What the app owns is the pair of base cases and the cursor;
 * what the learner owns is every value that follows from them.
 */
class FibonacciAlgorithm : Algorithm<FibonacciState, FibonacciAction> {

    override val id = AlgorithmId.FIBONACCI

    /**
     * The base cases are given, not asked for.
     *
     * `F(0) = 0` and `F(1) = 1` are the definition of the sequence — there is no
     * reasoning that produces them and nothing to get wrong, so they are on the
     * table before the lesson starts. The same call Prefix Sum makes about
     * `prefix[0] = 0` (ADR-033).
     */
    override fun initial(dataset: Dataset): FibonacciState {
        val n = (dataset.target ?: DEFAULT_N).coerceAtLeast(0)
        return FibonacciState(
            n = n,
            // A table that stops short of index 1 gets only the entries it has
            // room for, so `F(0)` is a finished lesson rather than a crash.
            dp = fibonacciTable(n).take(minOf(2, n + 1)),
        )
    }

    override fun probe(state: FibonacciState): Probe<FibonacciAction> {
        if (state.buildComplete) return Probe.Terminal(Outcome.Completed(true))
        return Probe.Decide(fillDecision(state))
    }

    // -- The one decision -----------------------------------------------------

    private fun fillDecision(state: FibonacciState): Decision<FibonacciAction> {
        val i = state.nextIndex
        val a = requireNotNull(state.previous)
        val b = requireNotNull(state.beforeThat)
        val correct = a + b
        val choices = fillChoices(i, a, b, correct)
        val isLast = i == state.n

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.FIB_ASK_NEXT, listOf(i)),
            options = choices.map { candidate ->
                ActionOption<FibonacciAction>(
                    action = FibonacciAction.Fill(candidate),
                    label = NarrationKey(NarrationId.FIB_OPTION_VALUE, listOf(candidate)),
                )
            },
            correct = FibonacciAction.Fill(correct),
            // The two cells the answer is made of. Everything the learner needs is
            // on screen; the question is only ever which two and what they make.
            focus = listOf(i - 2, i - 1),
            hint = NarrationKey(NarrationId.FIB_HINT_RULE, listOf(i, i - 1, i - 2)),
            guidance = listOf(
                NarrationKey(NarrationId.FIB_RETRY_LOOK, listOf(i - 1, i - 2)),
                NarrationKey(NarrationId.FIB_RETRY_ASK, listOf(a, b)),
                NarrationKey(NarrationId.FIB_RETRY_EXPLAIN, listOf(i, a, b, correct)),
            ),
            minimalFeedback = NarrationKey(
                NarrationId.FIB_RETRY_LOOK,
                listOf(i - 1, i - 2),
            ),
            whyWrong = buildMap {
                // Each distractor is named for the misconception that produces it,
                // so the feedback describes what the learner actually did rather
                // than restating the rule at them.
                for (candidate in choices) {
                    if (candidate == correct) continue
                    val id = when (candidate) {
                        a -> NarrationId.FIB_WHY_ONLY_PREVIOUS
                        a + a -> NarrationId.FIB_WHY_DOUBLED_PREVIOUS
                        a - b -> NarrationId.FIB_WHY_SUBTRACTED
                        correct + a -> NarrationId.FIB_WHY_REACHED_TOO_FAR
                        else -> NarrationId.FIB_WHY_NOT_THE_TWO
                    }
                    put(
                        FibonacciAction.Fill(candidate),
                        NarrationKey(id, listOf(a, b, correct, i)),
                    )
                }
            },
            correctFeedback = NarrationKey(
                if (isLast) NarrationId.FIB_CORRECT_LAST else NarrationId.FIB_CORRECT,
                listOf(a, b, correct, i),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.FIB_HINT_RULE, listOf(i, i - 1, i - 2)),
                NarrationKey(NarrationId.FIB_RETRY_ASK, listOf(a, b)),
                NarrationKey(NarrationId.FIB_RETRY_EXPLAIN, listOf(i, a, b, correct)),
            ),
        )
    }

    /**
     * Three values, one right, and the wrong two are mistakes learners actually
     * make rather than arbitrary near-misses:
     *
     *  - `dp[i - 1]` alone — the second operand forgotten, which is what happens
     *    when someone reads the recurrence as "carry the running value forward";
     *  - `dp[i - 1] + dp[i - 1]` — the right shape with the wrong pair, and the
     *    single most common slip once the numbers get close together;
     *  - `dp[i - 1] - dp[i - 2]` — the operator misread.
     *
     * Deterministic: the correct answer's seat rotates with the index rather than
     * being drawn at random, so a run is reproducible and a test can pin it. The
     * same construction Prefix Sum uses.
     */
    private fun fillChoices(index: Int, a: Int, b: Int, correct: Int): List<Int> {
        val pool = LinkedHashSet<Int>()
        pool += correct
        // Only the previous value — the other operand dropped.
        pool += a
        // The right shape, the wrong pair.
        pool += a + a
        // The operator misread.
        pool += a - b
        // One index too far ahead: dp[i] + dp[i - 1], which is dp[i + 1].
        pool += correct + a
        // Last resorts, so there are always three distinct options even where the
        // numbers collide — which they do at the start, when dp is 0 and 1.
        pool += correct + 1
        pool += correct + 2

        val three = pool.take(3)
        val shift = index % three.size
        return three.drop(shift) + three.take(shift)
    }

    // -- Transitions ----------------------------------------------------------

    /**
     * Appends whatever it was given, correct or not.
     *
     * `apply` takes any legal action and is not the thing that decides which are
     * legal (ARCHITECTURE.md §4.1) — which is what makes `validate` a pure
     * comparison rather than a special case. In Try nothing ever calls it with a
     * wrong value, because a `Retry` carries no action (ADR-021).
     */
    override fun apply(
        state: FibonacciState,
        action: FibonacciAction,
    ): Transition<FibonacciState> = when (action) {
        is FibonacciAction.Fill -> fill(state, action.value)
    }

    private fun fill(state: FibonacciState, value: Int): Transition<FibonacciState> {
        // Nothing left to fill: a no-op that leaves the state byte-for-byte as it
        // was, rather than an exception. An over-long action sequence is a bad
        // caller, not a crash.
        if (state.buildComplete) {
            return Transition(state, emptyList(), null, correct = false)
        }

        val i = state.nextIndex
        val a = requireNotNull(state.previous)
        val b = requireNotNull(state.beforeThat)
        val correct = value == state.expectedNext
        val next = state.copy(dp = state.dp + value)

        return Transition(
            next = next,
            events = buildList {
                // The two cells the answer came from, lit as the pair under
                // examination — the whole picture the lesson is built on.
                add(VizEvent.Examine(listOf(i - 2, i - 1), ExamineRole.COMPARING))
                add(VizEvent.Insert(value, i))
                add(VizEvent.Meter(MeterId.RUNNING_SUM, value.toLong()))
                if (next.buildComplete) {
                    add(VizEvent.Finalize(0..state.n))
                    add(VizEvent.Terminal(Outcome.Completed(correct)))
                }
            },
            narration = NarrationKey(NarrationId.FIB_BUILT, listOf(i, a, b, value)),
            correct = correct,
        )
    }

    private companion object {
        /** Used only when a dataset forgets to say; every authored one says. */
        const val DEFAULT_N = 8
    }
}

// -- The sequence, and the cost of computing it naively -----------------------

/**
 * `F(0) .. F(n)`, built the way the lesson builds it.
 *
 * The one place a Fibonacci number is produced in the whole codebase. Tests check
 * it against an independently written recursive definition rather than against
 * itself.
 */
fun fibonacciTable(n: Int): List<Int> {
    if (n < 0) return listOf(0)
    val table = ArrayList<Int>(n + 1)
    table += 0
    if (n >= 1) table += 1
    for (i in 2..n) table += table[i - 1] + table[i - 2]
    return table
}

/**
 * How many calls naive `fib(n)` makes in total, counting itself.
 *
 * ```
 * calls(0) = calls(1) = 1
 * calls(n) = 1 + calls(n - 1) + calls(n - 2)
 * ```
 *
 * For `n = 8` that is **67** — against nine table entries. This is the number the
 * "repeated work" beat rests on, so it is computed here and asserted in a test
 * against a real counting recursion, never written into copy as a claim.
 */
fun naiveCallCount(n: Int): Int {
    if (n < 0) return 0
    if (n <= 1) return 1
    var twoBack = 1 // calls(0)
    var oneBack = 1 // calls(1)
    var current = oneBack
    for (i in 2..n) {
        current = 1 + oneBack + twoBack
        twoBack = oneBack
        oneBack = current
    }
    return current
}

/**
 * How many times naive `fib(n)` evaluates `fib(k)`.
 *
 * ```
 * T(m, k) = [m == k] + if (m > 1) T(m - 1, k) + T(m - 2, k) else 0
 * ```
 *
 * For `k >= 1` that grows like Fibonacci itself — `fib(k)` is reached once from
 * `fib(k)`, once from `fib(k + 1)`, twice from `fib(k + 2)` — which is exactly why
 * the repeated work grows as fast as the answer does.
 *
 * `k = 0` is the exception, and it is the reason this is computed rather than
 * written as that closed form: naive `fib(1)` returns without recursing, so it
 * never calls `fib(0)` at all. A test checks every `(n, k)` pair against a
 * recursion that actually counts itself.
 *
 * It is also, pleasingly, the lesson's own technique: a table built upward from
 * the base cases so nothing is computed twice.
 */
fun naiveCallsTo(n: Int, k: Int): Int {
    if (k < 0 || n < 0 || k > n) return 0
    val counts = IntArray(n + 1)
    for (m in 0..n) {
        var hits = if (m == k) 1 else 0
        // Only a call with `m > 1` recurses; the base cases return immediately.
        if (m > 1) hits += counts[m - 1] + counts[m - 2]
        counts[m] = hits
    }
    return counts[n]
}
