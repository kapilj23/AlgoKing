package com.ttele.algoking.engine.algorithms.countingsort

import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.CountBucket
import com.ttele.algoking.engine.scene.CountTally
import com.ttele.algoking.engine.scene.CountingScene
import com.ttele.algoking.engine.scene.MeterReadout
import com.ttele.algoking.engine.scene.SceneProjector

/**
 * Counting Sort presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * Three rows, read top to bottom: **what we have**, **what we counted**, **what we
 * are building**. The relationship the learner has to see is between one input
 * cell and one bucket, so exactly one of each is ever lit at a time — the value
 * being counted in violet, and the bucket it lands in.
 *
 * The rebuild reverses the reading: the input row goes quiet, the bucket being
 * emptied lights up, and the output row fills left to right out of holes.
 */
class CountingSortProjector : SceneProjector<CountingSortState> {

    override fun project(
        state: CountingSortState,
        activeEvents: List<VizEvent>,
    ): CountingScene {
        val counting = !state.countingComplete && state.values.isNotEmpty()
        val current = state.currentValue
        val nextOut = state.nextToPlace

        // -- The input array, which never moves ---------------------------------
        val input = state.values.mapIndexed { index, value ->
            Cell(
                key = index,
                value = value,
                slot = index,
                state = when {
                    counting && index == state.cursor -> CellState.COMPARING
                    // Counted already. Not "sorted" — nothing here moved.
                    index < state.cursor -> CellState.IDLE
                    else -> CellState.IDLE
                },
            )
        }

        // -- The table ----------------------------------------------------------
        val buckets = state.bucketValues.mapIndexed { slot, value ->
            val count = state.countOf(value)
            val remaining = state.remainingOf(value)
            CountBucket(
                slot = slot,
                value = value,
                count = count,
                remaining = if (counting) null else remaining,
                state = when {
                    // The bucket that just took a value: amber, because it is
                    // holding something, while violet stays on the input cell
                    // currently under examination. Two different jobs, two
                    // different colours, exactly as the legend promises.
                    counting && value == state.lastValue -> CellState.CANDIDATE
                    counting -> CellState.IDLE
                    // Rebuilding: the bucket being read from now.
                    value == nextOut -> CellState.CANDIDATE
                    // Gave up everything it had.
                    count > 0 && remaining == 0 -> CellState.FINALIZED
                    // Counted nothing, and that is an answer: place none of these.
                    count == 0 -> CellState.ELIMINATED
                    else -> CellState.IDLE
                },
            )
        }

        // -- The answer, holes and all ------------------------------------------
        val output = List(state.size) { index ->
            val value = state.placed.getOrNull(index)
            Cell(
                key = 1_000 + index,
                value = value ?: 0,
                slot = index,
                state = when {
                    value == null -> CellState.GHOST
                    state.rebuildComplete -> CellState.FINALIZED
                    index == state.placed.lastIndex -> CellState.CANDIDATE
                    else -> CellState.FINALIZED
                },
            )
        }

        return CountingScene(
            input = input,
            buckets = buckets,
            output = output,
            inputLabel = "Input",
            countLabel = "Count",
            outputLabel = "Output",
            tally = tally(state, counting),
            meters = buildList {
                if (counting) {
                    add(
                        MeterReadout(
                            MeterId.REMAINING,
                            "Counted",
                            state.cursor.toLong(),
                        ),
                    )
                } else if (state.values.isNotEmpty()) {
                    add(
                        MeterReadout(
                            MeterId.REMAINING,
                            "Placed",
                            state.placed.size.toLong(),
                        ),
                    )
                }
            },
            legendLabels = mapOf(
                CellState.COMPARING to if (counting) "Counting" else "Reading",
                CellState.CANDIDATE to if (counting) "Bucket" else "Next out",
                CellState.FINALIZED to if (counting) "Counted" else "Done",
                CellState.ELIMINATED to "None",
                CellState.GHOST to "Empty",
                CellState.IDLE to "Waiting",
            ),
        )
    }

    /**
     * The change that just happened: `count[3]: 1 → 2` while counting, and its
     * mirror image `count[3]: 2 → 1` while the same table is emptied again.
     *
     * It shows **what the last action did**, never what the next one should do.
     * A strip reading `count[3]: 1 → ?` while the learner is being asked which
     * bucket takes a 3 would have answered the question in the act of posing it
     * — the rule the hash flow set (ADR-030) — so before the first action there
     * is deliberately no strip at all.
     */
    private fun tally(state: CountingSortState, counting: Boolean): CountTally? {
        val last = state.lastValue ?: return null
        if (state.values.isEmpty()) return null

        return if (counting) {
            // The bucket was just raised by one.
            CountTally(value = last, from = state.countOf(last) - 1, to = state.countOf(last))
        } else {
            // ...and now it is being spent, one value at a time.
            val left = state.remainingOf(last)
            CountTally(value = last, from = left + 1, to = left)
        }
    }
}
