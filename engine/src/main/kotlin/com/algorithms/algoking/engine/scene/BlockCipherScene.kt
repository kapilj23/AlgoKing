package com.algorithms.algoking.engine.scene

/**
 * A block cipher's State, the round it is in, and the key schedule behind it —
 * DESIGN_SYSTEM.md §6.16m.
 *
 * ### Why this is a tenth shape
 *
 * The bar every shape has had to clear (ADR-030, ADR-033, ADR-034, ADR-040,
 * ADR-044, ADR-046, ADR-047, ADR-048) and that ADR-036 and ADR-045 each refused: it
 * is a new *kind* of data, not a familiar kind in a new lesson.
 *
 * AES's State is a **4 × 4 grid of bytes whose rows and columns are each named by a
 * transformation that acts on them**. ShiftRows moves along rows; MixColumns mixes
 * down columns. No existing shape has an axis an operation is named after:
 *
 *  - `SequenceScene` is one row whose slots are positions, and its `GRID` layout is
 *    a *wrap* — boxes flowing onto as many lines as they need, with no meaning in
 *    which line a box lands on. Here the row a byte sits in decides how far it
 *    moves, so a wrap would draw the thing that is false;
 *  - `DpTableScene` is the near miss and the instructive one. It is genuinely rows
 *    × columns — but its two axes are two *quantities* (the first `i` items, at
 *    capacity `c`) and a cell is a point in that space. The State's axes are not
 *    quantities at all; a byte's row and column are its *position in a block*, and
 *    the grid is the same sixteen bytes rearranged rather than a space being
 *    filled in. It also carries item cards, a bag meter and a two-sided choice
 *    strip a cipher would null out, which is the union-pretending-to-be-a-record
 *    ADR-047 refused;
 *  - `CipherScene` aligns two messages of the same length position by position,
 *    because Caesar letter 3 became ciphertext letter 3. AES byte 3 after one round
 *    depends on four input bytes, and after two on all sixteen;
 *  - `BitwiseScene` shares one set of columns across three rows, which is the same
 *    assertion in stronger form;
 *  - `HashScene` holds a pipeline and a digest as *text*, with deliberately no
 *    positional relationship between them — the opposite claim to this one, where
 *    position is the whole subject;
 *  - `CountingScene`, `PrefixScene`, `BucketScene` and `GraphScene` are a table
 *    indexed by value, two offset rows, a bucket table and a set of nodes. None is
 *    a square that transforms in place.
 *
 * So what it holds is: **a pipeline, a square of bytes, the round it is in, the
 * steps that round is made of, and the key schedule beside it** — plus the variant
 * table, because "the block is always 128 bits and only the key grows" is a
 * relationship between three rows and cannot be drawn on one.
 *
 * What it did **not** need: no new `VizEvent`, no new cell state, no new
 * interaction model, and no change to any existing lesson. The State's bytes are
 * ordinary [Cell]s carrying a hex [Cell.label], so they are drawn by the same
 * `SceneCell` every other lesson uses and the visual language cannot fork.
 */
data class BlockCipherScene(
    /**
     * The conceptual pipeline, left to right — always every stage.
     *
     * On screen whole from the first frame, for the reason the XOR truth table and
     * the SHA-256 pipeline are (ADR-047, ADR-048): it is the shape of the
     * operation, and a diagram that came and went would read as a hint rather than
     * as the thing the learner is meant to be reading.
     */
    val pipeline: List<CipherStage>,
    /**
     * The State, as sixteen cells whose `slot` is `row + 4 × column`.
     *
     * Null only before the block has been arranged into one. AES fills the State
     * **column by column**, which is why slot 4 is the top of the second column
     * rather than the start of a second row — the renderer lays it out that way and
     * the lesson's first beats say so out loud.
     */
    val state: List<Cell> = emptyList(),
    /**
     * Positions this step changed, computed by comparing the State on both sides
     * of it.
     *
     * This is what makes each transformation legible as itself: SubBytes marks all
     * sixteen and moves none, ShiftRows marks twelve and leaves row 0 alone. Both
     * are *pictures* rather than sentences, and neither is authored.
     */
    val changed: Set<Int> = emptySet(),
    /** e.g. `"Round 3 / 10"`, or `"Before round 1"`. Null when no round is live. */
    val roundLabel: String? = null,
    /** The transformation being applied right now, e.g. `"SubBytes"`. */
    val transformation: String? = null,
    /** What that transformation does, in one short clause. */
    val transformationDetail: String? = null,
    /**
     * The steps the live round is made of, in order.
     *
     * **This is where the final round states its difference.** A normal round
     * carries four; the final round carries the same four with MixColumns marked
     * [RoundStepState.SKIPPED] rather than dropped, because a step that is drawn
     * and struck through says "this one is left out" while a step simply absent
     * says nothing at all.
     *
     * It is also the control: when the learner is asked which transformation comes
     * next, these are the slots they tap (`slot` is the step's position in a normal
     * round), which is the gesture ADR-034 chose over a row of words.
     */
    val roundSteps: List<RoundStepView> = emptyList(),
    /** The original key and the round keys derived from it. */
    val keySchedule: KeyScheduleView? = null,
    /** The three variants, for the beat that is about the difference between them. */
    val variants: List<VariantRow> = emptyList(),
    /**
     * The statements the learner is choosing between, in the order and tones of the
     * buttons beneath them.
     *
     * `HashScene.claims`' arrangement, for the same reason (ADR-048): a
     * `DecisionButton` is one line at `labelLarge`, so a statement long enough to
     * be unambiguous cannot live on it. The card carries the claim and the button
     * carries one short word.
     */
    val claims: List<CipherClaim> = emptyList(),
    /** e.g. "AES-128". */
    val badge: Badge? = null,
    val meters: List<MeterReadout> = emptyList(),
    /** Renames a cell state in the legend, as [SequenceScene.legendLabels] does. */
    val legendLabels: Map<CellState, String> = emptyMap(),
) : Scene {

    /** Every state on screen, so the legend names exactly what is drawn. */
    val legendStates: Set<CellState>
        get() = (state.map { it.state } + pipeline.map { it.state }).toSet()
}

/**
 * One stage of the pipeline.
 *
 * The **Rounds stage is one stage and not ten**. Drawing ten identical boxes would
 * make the pipeline a progress bar and push everything else off a phone; what the
 * rounds are made of is drawn properly, once, in [BlockCipherScene.roundSteps].
 */
data class CipherStage(
    val label: String,
    val detail: String? = null,
    val state: CellState = CellState.IDLE,
)

/** How far a round has got through one of its steps. */
enum class RoundStepState {
    /** Already applied in this round. */
    DONE,

    /** Being applied right now. */
    CURRENT,

    /** Still to come in this round. */
    UPCOMING,

    /**
     * Drawn, and deliberately not applied — the final round's MixColumns.
     *
     * An omission has to be *visible* to be learned, so the step keeps its place
     * and is struck through instead of disappearing.
     */
    SKIPPED,

    /** Offered as an answer, and not yet chosen. */
    SELECTABLE,
}

/**
 * One transformation in the round on screen.
 *
 * [slot] is its position in a **normal** round, so it is stable whether or not this
 * particular round runs it — which is what lets the same four slots be the answer
 * to "which comes next?" and to "which one is left out?".
 */
data class RoundStepView(
    val slot: Int,
    val label: String,
    val state: RoundStepState,
)

/**
 * The key, and the round keys expansion produced from it.
 *
 * [roundKeys] holds one hex string per round key — always one more than there are
 * rounds, because the initial AddRoundKey spends round key 0 before round 1 begins.
 * The learner is never asked to compute one (PRODUCT_SPEC.md §3); they are asked to
 * know what produces them.
 */
data class KeyScheduleView(
    val keyLabel: String,
    val keyHex: String,
    val keyBits: Int,
    val roundKeys: List<String>,
    /** Which round key is in use right now, if any. */
    val activeRoundKey: Int? = null,
    /** How many round keys to draw before eliding the rest. */
    val shown: Int = roundKeys.size,
)

/**
 * One row of the variant table: a variant, its key size, and how many rounds it
 * runs.
 *
 * [rounds] is null until the learner has settled it. An answer already on screen is
 * not a question — the rule the hash flow set (ADR-030) and the one that makes this
 * table askable rather than merely readable.
 */
data class VariantRow(
    val label: String,
    val keyBits: Int,
    val blockBits: Int,
    val rounds: Int? = null,
    val state: CellState = CellState.IDLE,
)

/**
 * One of the statements on offer, as the learner reads it.
 *
 * It carries no `correct` flag, for the reason [ActionOption] does not
 * (ARCHITECTURE.md §6): a renderer that knew which claim was true could style it
 * differently by accident, and the decision would answer itself.
 */
data class CipherClaim(
    /** The full statement, long enough to be unambiguous. */
    val text: String,
    /** The word on the button beneath it, so the pairing is unmistakable. */
    val choice: String,
)
