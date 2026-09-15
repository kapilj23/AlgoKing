package com.algorithms.algoking.engine.algorithms.caesar

import com.algorithms.algoking.engine.core.CipherProblem
import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.core.alphabetIndexOf
import com.algorithms.algoking.engine.core.letterAt
import com.algorithms.algoking.engine.core.shiftLetter
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.CipherScene
import com.algorithms.algoking.engine.scene.CipherStep
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The Caesar Cipher walkthrough — the rule, the letters, and the ring.
 *
 * ### The shape
 *
 * **Opening.** Two beats: the problem, and the mapping. The alphabet and its
 * shifted twin are on screen from the first frame, so the second beat has real
 * evidence to point at rather than a sentence to assert.
 *
 * **Build.** The first two letters in full, any run of repeats collapsed to one
 * beat, and the last letter in full — ADR-025's rule, and here the collapse says
 * something extra: the two `L`s become the same `O`, every time, which is both why
 * the cipher is easy to apply and why it is easy to break.
 *
 * **Closing.** The wrap, then the insight, then the recap. The wrap gets its own
 * beat even though `HELLO` never needs one, because `Z -> C` is the half of this
 * cipher a learner gets wrong and the mapping row is already showing `X Y Z` above
 * `A B C` for it to point at.
 */
class CaesarWatchNarrator : WatchNarrator<CaesarState> {

    override fun opening(state: CaesarState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(
                NarrationId.CC_WATCH_SETUP,
                listOf(state.plaintext),
            ),
            support = NarrationKey(NarrationId.CC_WATCH_SETUP_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            // The mapping row is already drawn, so this beat reads it out rather
            // than describing something the learner cannot see.
            headline = NarrationKey(NarrationId.CC_WATCH_RULE, listOf(state.shift)),
            support = NarrationKey(
                NarrationId.CC_WATCH_RULE_SUPPORT,
                listOf(state.shift, letterAt(0), shiftLetter('A', state.shift)),
            ),
        ),
    )

    override fun onFrame(
        previous: CaesarState,
        frame: Frame<CaesarState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state

        // A non-letter passed through untouched. Worth a beat when it happens,
        // because "the cipher does not shift this" is a rule, not an omission.
        if (frame.events.any { it is VizEvent.Hold }) {
            val char = previous.currentChar ?: return emptyList()
            return listOf(
                PartialStep(
                    kind = WatchStepKind.KEEP,
                    scene = scene,
                    headline = NarrationKey(NarrationId.CC_WATCH_COPIED, listOf(char)),
                    support = NarrationKey(NarrationId.CC_WATCH_COPIED_SUPPORT),
                ),
            )
        }

        val inserted = frame.events
            .filterIsInstance<VizEvent.Insert>()
            .firstOrNull()
            ?: return emptyList()

        val at = inserted.at
        val from = previous.plaintext[at]
        val to = letterAt(inserted.value)
        val plan = BuildPlan(previous.plaintext)

        return when {
            // The last letter always gets its own beat: it completes the answer.
            at == previous.plaintext.lastIndex -> listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.CC_WATCH_LETTER,
                        listOf(from, previous.shift, to),
                    ),
                    support = NarrationKey(
                        NarrationId.CC_WATCH_DONE,
                        listOf(previous.plaintext, state.produced),
                    ),
                ),
            )

            // The opening letters, in full — this is where the model is built.
            at in plan.detailed -> listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.CC_WATCH_LETTER,
                        listOf(from, previous.shift, to),
                    ),
                    support = NarrationKey(
                        NarrationId.CC_WATCH_LETTER_SUPPORT,
                        listOf(alphabetIndexOf(from), previous.shift, state.produced),
                    ),
                ),
            )

            // The middle, collapsed into one beat at its last position — by which
            // point every character it covers is on screen to be read.
            at == plan.collapsedAt -> listOf(
                PartialStep(
                    kind = WatchStepKind.PASS_COMPLETE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.CC_WATCH_COLLAPSED,
                        listOf(
                            plan.collapsed.map { previous.plaintext[it] }
                                .joinToString(""),
                            state.produced,
                        ),
                    ),
                    support = NarrationKey(NarrationId.CC_WATCH_COLLAPSED_SUPPORT),
                ),
            )

            // Covered by the collapsed beat. A step where nothing new is said is a
            // step that should not exist (ADR-020).
            else -> emptyList()
        }
    }

    override fun closing(
        state: CaesarState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> {
        val shift = state.shift
        val z = 'Z'
        return listOf(
            // The wrap, on its own, even though this message never needed one. It
            // is the half of the cipher that gets got wrong, and the mapping row is
            // already showing X Y Z above A B C for this beat to point at.
            //
            // So it *does* point: the beat lights Z's tile and prints the sum that
            // runs past the end. A beat that says "look at the end of the row"
            // while lighting a letter in the middle of it is asking the learner to
            // find the evidence themselves, and it would be the fourth screen in a
            // row showing an identical picture (ADR-020).
            PartialStep(
                kind = WatchStepKind.EXAMINE,
                scene = scene.wrapExample(z, shift),
                headline = NarrationKey(
                    NarrationId.CC_WATCH_WRAP,
                    listOf(z, shift, shiftLetter(z, shift)),
                ),
                support = NarrationKey(
                    NarrationId.CC_WATCH_WRAP_SUPPORT,
                    listOf(alphabetIndexOf(z) + shift, shiftLetter(z, shift)),
                ),
            ),
            PartialStep(
                kind = WatchStepKind.INSIGHT,
                scene = scene,
                headline = NarrationKey(NarrationId.CC_WATCH_INSIGHT),
                support = NarrationKey(NarrationId.CC_WATCH_INSIGHT_SUPPORT, listOf(shift)),
            ),
            PartialStep(
                kind = WatchStepKind.SUMMARY,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.CC_WATCH_SUMMARY,
                    listOf(state.plaintext, state.produced),
                ),
                support = NarrationKey(NarrationId.CC_WATCH_SUMMARY_SUPPORT),
                bullets = listOf(
                    NarrationKey(NarrationId.CC_IDEA_1),
                    NarrationKey(NarrationId.CC_IDEA_2),
                    NarrationKey(NarrationId.CC_IDEA_3),
                    NarrationKey(NarrationId.CC_IDEA_4),
                ),
            ),
        )
    }
}

/**
 * The same scene, with the mapping row lit on [letter] instead of on the message.
 *
 * Presentation knowledge, sitting beside the algorithm exactly where
 * ARCHITECTURE.md §7.2 puts it. The projector's job is to draw a *state*; this beat
 * is not about a state at all — it is about a letter the message never contains —
 * so the narrator dresses the picture rather than the engine inventing a step that
 * does not happen.
 *
 * Only two things change: which tile is lit, and what the strip says. Every cell
 * of the finished message stays exactly as it was, so the beat reads as an aside
 * about the alphabet rather than as the run doing something more.
 */
private fun Scene.wrapExample(letter: Char, shift: Int): Scene {
    if (this !is CipherScene) return this
    val at = alphabetIndexOf(letter)
    return copy(
        alphabet = alphabet.map { pair ->
            pair.copy(
                state = if (pair.slot == at) CellState.COMPARING else CellState.IDLE,
            )
        },
        step = CipherStep(
            from = letter,
            fromIndex = at,
            shift = shift,
            sum = at + shift,
            wrapped = at + shift >= CipherProblem.ALPHABET_SIZE,
            toIndex = alphabetIndexOf(shiftLetter(letter, shift)),
            to = shiftLetter(letter, shift),
        ),
    )
}

/**
 * Which characters the walkthrough narrates in full, and which it collapses.
 *
 * Stated as a rule over the message rather than as positions for one dataset, so a
 * lesson given a different word behaves sensibly instead of silently narrating
 * every character or none of them.
 *
 * For `HELLO`: `H` and `E` in full, `LL` as one beat, `O` in full.
 */
internal class BuildPlan(plaintext: String) {

    /** The opening characters, narrated one at a time. */
    val detailed: IntRange = 0 until minOf(DETAILED_COUNT, plaintext.length - 1)

    /** The characters folded into a single beat. Empty when there are none. */
    val collapsed: IntRange = detailed.last + 1 until plaintext.lastIndex.coerceAtLeast(0)

    /** The position whose frame carries the collapsed beat, or null if none. */
    val collapsedAt: Int? = collapsed.lastOrNull()

    private companion object {
        /**
         * Two is enough: the first shows the rule applied, the second shows it was
         * not a coincidence. A third says nothing the mapping row has not already
         * said, and this lesson's whole content is on screen from frame one.
         */
        const val DETAILED_COUNT = 2
    }
}
