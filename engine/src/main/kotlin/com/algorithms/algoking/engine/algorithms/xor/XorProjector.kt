package com.algorithms.algoking.engine.algorithms.xor

import com.algorithms.algoking.engine.core.bitAt
import com.algorithms.algoking.engine.core.xorTruthTable
import com.algorithms.algoking.engine.event.MarkId
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Badge
import com.algorithms.algoking.engine.scene.BitRow
import com.algorithms.algoking.engine.scene.BitStep
import com.algorithms.algoking.engine.scene.BitwiseScene
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.MeterReadout
import com.algorithms.algoking.engine.scene.SceneProjector
import com.algorithms.algoking.engine.scene.TruthRow

/**
 * XOR Cipher presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * ### What the picture has to say
 *
 * One thing, at every beat: *this bit, that bit, and the bit they make.* So the
 * three rows share one set of columns, the column being decided is lit on all
 * three, and the truth-table row that answers it lights with them.
 *
 * The **row labels change with the phase** and nothing else does. Encrypting reads
 * *Plaintext / Key / Ciphertext*; applying the key again reads *Ciphertext / Key /
 * Recovered*, over the ciphertext the first pass actually produced. That sameness
 * is the argument: a learner watching the identical picture run twice and give the
 * original back has seen why XOR is its own inverse, which is stronger than the
 * sentence saying so.
 *
 * Like every other projector, the scene is read from [activeEvents] where it can
 * be: after a bit is decided the cursor has moved on, so lighting the column the
 * cursor points at would show the learner the *next* pair beside the sentence
 * explaining the last one (ADR-032).
 */
class XorProjector : SceneProjector<XorState> {

    override fun project(state: XorState, activeEvents: List<VizEvent>): BitwiseScene {
        // The column just decided, if this frame decided one. That is the column
        // the narration is about, so it is the column the picture should be lighting.
        val justSet = activeEvents
            .filterIsInstance<VizEvent.Insert>()
            .firstOrNull()
            ?.at

        // **Which pass this frame belongs to**, which is not always the pass the
        // state is now in. The frame that writes the last encrypted bit leaves a
        // state whose `phase` has already become DECRYPT — so drawing that would
        // relabel the rows and blank the result underneath a sentence saying
        // "1010 ⊕ 1100 = 0110". A bit was written into the second pass exactly when
        // the second pass has something in it.
        val framePhase = when {
            justSet == null -> state.phase
            state.decrypted.isEmpty() -> XorPhase.ENCRYPT
            else -> XorPhase.DECRYPT
        }
        val encrypting = framePhase == XorPhase.ENCRYPT

        // Everything below reads the frame's pass, never the state's.
        val inputRow = if (encrypting) "Plaintext" else "Ciphertext"
        val resultRow = if (encrypting) "Ciphertext" else "Recovered"
        val inputBits = if (encrypting) state.problem.plaintext else state.encrypted
        val producedBits = if (encrypting) state.encrypted else state.decrypted
        val cursor = producedBits.length

        val focus = justSet ?: cursor.takeIf { it < state.width }

        fun bitCell(keyOffset: Int, index: Int, value: Int?, state: CellState) = Cell(
            key = keyOffset + index,
            value = value ?: -1,
            slot = index,
            state = state,
            // Bits are drawn as bits. `SceneCell` prints `label ?: value`, so this
            // keeps a GHOST from ever showing `-1`.
            label = value?.toString(),
        )

        fun rowState(index: Int): CellState = when {
            index == focus -> CellState.COMPARING
            index < cursor -> CellState.FINALIZED
            else -> CellState.IDLE
        }

        val input = (0 until state.width).map { i ->
            bitCell(0, i, bitAt(inputBits, i), rowState(i))
        }
        val key = (0 until state.width).map { i ->
            bitCell(1_000, i, bitAt(state.key, i), rowState(i))
        }
        val result = (0 until state.width).map { i ->
            val bit = producedBits.getOrNull(i)?.let { it - '0' }
            bitCell(
                keyOffset = 2_000,
                index = i,
                value = bit,
                state = when {
                    // Not decided yet — a hole, never a zero. A `0` here would be
                    // especially dishonest: zero is a real answer in this table.
                    bit == null -> CellState.GHOST
                    i == justSet -> CellState.CANDIDATE
                    else -> CellState.FINALIZED
                },
            )
        }

        val a = focus?.let { bitAt(inputBits, it) }
        val b = focus?.let { bitAt(state.key, it) }

        return BitwiseScene(
            rows = listOf(
                BitRow(inputRow, input),
                BitRow("Key", key),
                BitRow(resultRow, result),
            ),
            truthTable = xorTruthTable.map { (left, right, out) ->
                TruthRow(
                    a = left,
                    b = right,
                    result = out,
                    // The row that answers the column on the table. It is the
                    // lookup the learner should be making, so the picture makes it.
                    active = left == a && right == b,
                )
            },
            step = focus?.let { column ->
                BitStep(
                    index = column,
                    a = requireNotNull(a),
                    b = requireNotNull(b),
                    // Known only once the column actually holds something.
                    result = producedBits.getOrNull(column)?.let { it - '0' },
                )
            },
            badge = Badge(mark = MarkId.TARGET, label = "Operation", value = 0)
                .copy(valueLabel = "XOR"),
            meters = buildList {
                if (!state.finished) {
                    add(MeterReadout(MeterId.REMAINING, "Left", state.remaining.toLong()))
                }
            },
            // Short: four entries share one centred row, and a long label pushes
            // the last one off the edge (DESIGN_SYSTEM.md §6.13).
            legendLabels = mapOf(
                CellState.COMPARING to "Current",
                CellState.CANDIDATE to "New",
                CellState.GHOST to "Empty",
                CellState.FINALIZED to "Done",
                CellState.IDLE to "Waiting",
            ),
        )
    }
}
