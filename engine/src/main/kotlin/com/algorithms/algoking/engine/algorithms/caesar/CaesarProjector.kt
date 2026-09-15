package com.algorithms.algoking.engine.algorithms.caesar

import com.algorithms.algoking.engine.core.CipherProblem
import com.algorithms.algoking.engine.core.alphabetIndexOf
import com.algorithms.algoking.engine.event.MarkId
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Badge
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.CipherPair
import com.algorithms.algoking.engine.scene.CipherScene
import com.algorithms.algoking.engine.scene.CipherStep
import com.algorithms.algoking.engine.scene.MeterReadout
import com.algorithms.algoking.engine.scene.SceneProjector

/**
 * Caesar Cipher presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * ### What the picture has to say
 *
 * One thing, at every beat: *this letter, that rule, this other letter.* So the
 * message row marks where the cursor is, the mapping row lights the one tile that
 * answers it, and the answer row fills in behind.
 *
 * The alphabet is drawn **in full, always** — all 26 tiles, including the letters
 * this message never touches. The wrap is the half of the cipher a learner gets
 * wrong, and it is only visible as a picture if `X Y Z` are on screen sitting
 * above `A B C`. A mapping that showed only the letters in play would quietly
 * remove the lesson.
 *
 * Like every other projector, the scene is read from [activeEvents] where it can
 * be: after a character is encrypted the cursor has already moved on, so lighting
 * the tile the cursor points at would show the learner the *next* letter beside
 * the sentence explaining the last one — the order ADR-032 split Two Pointers'
 * comparison from its move to avoid.
 */
class CaesarProjector : SceneProjector<CaesarState> {

    override fun project(
        state: CaesarState,
        activeEvents: List<VizEvent>,
    ): CipherScene {
        // The position just written, if this frame wrote one. That is the letter
        // the narration is about, so it is the letter the picture should be lighting.
        val justWritten = activeEvents
            .filterIsInstance<VizEvent.Insert>()
            .firstOrNull()
            ?.at

        val focus = justWritten ?: state.index.takeIf { !state.finished }
        val focusChar = focus?.let { state.plaintext.getOrNull(it) }
        val focusSlot = focusChar?.let { alphabetIndexOf(it) }?.takeIf { it >= 0 }

        // -- The message ------------------------------------------------------
        val plaintext = state.plaintext.mapIndexed { index, char ->
            Cell(
                key = index,
                value = alphabetIndexOf(char),
                slot = index,
                state = when {
                    index == focus -> CellState.COMPARING
                    index < state.index -> CellState.FINALIZED
                    else -> CellState.IDLE
                },
                label = char.toString(),
            )
        }

        // -- The answer, holes and all ----------------------------------------
        val ciphertext = state.plaintext.indices.map { index ->
            val produced = state.produced.getOrNull(index)
            Cell(
                key = 1_000 + index,
                value = produced?.let { alphabetIndexOf(it) } ?: -1,
                slot = index,
                state = when {
                    // Not produced yet — a hole, never a placeholder letter.
                    produced == null -> CellState.GHOST
                    // The one just made.
                    index == justWritten -> CellState.CANDIDATE
                    state.finished -> CellState.FINALIZED
                    else -> CellState.IDLE
                },
                label = produced?.toString(),
            )
        }

        // -- The rule ---------------------------------------------------------
        val alphabet = caesarAlphabet(state.shift).mapIndexed { index, (from, to) ->
            CipherPair(
                slot = index,
                from = from,
                to = to,
                // Exactly one tile is ever lit: the tile is the mapping, so
                // lighting it says "this is the row of the table you are reading".
                state = if (index == focusSlot) CellState.COMPARING else CellState.IDLE,
            )
        }

        return CipherScene(
            plaintext = plaintext,
            ciphertext = ciphertext,
            alphabet = alphabet,
            plaintextLabel = "Plaintext",
            ciphertextLabel = "Ciphertext",
            alphabetLabel = "Shift ${state.shift}",
            step = step(state, justWritten),
            badge = Badge(
                mark = MarkId.TARGET,
                label = "Shift",
                value = state.shift,
            ),
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

    /**
     * The working, as data: `H (7) + 3 = 10 → K`, and `Z (25) + 3 = 28 − 26 = 2 → C`
     * where it wraps.
     *
     * [CipherStep.toIndex] stays null while the result is still the question, which
     * is what lets one component carry both the walkthrough's statement and Try's
     * prompt without the projector knowing which phase is running (ADR-030).
     */
    private fun step(state: CaesarState, justWritten: Int?): CipherStep? {
        val at = justWritten ?: state.index.takeIf { !state.finished } ?: return null
        val from = state.plaintext.getOrNull(at) ?: return null
        // A space has no arithmetic behind it, so it gets no strip.
        if (!from.isLetter()) return null

        val fromIndex = alphabetIndexOf(from)
        val sum = fromIndex + state.shift
        val answered = justWritten?.let { state.produced.getOrNull(it) }

        return CipherStep(
            from = from,
            fromIndex = fromIndex,
            shift = state.shift,
            sum = sum,
            wrapped = sum >= CipherProblem.ALPHABET_SIZE,
            toIndex = answered?.let { alphabetIndexOf(it) },
            to = answered,
        )
    }
}
