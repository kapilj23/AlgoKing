package com.algorithms.algoking.engine.scene

/**
 * A pipeline, a digest, and messages compared against each other —
 * DESIGN_SYSTEM.md §6.16l.
 *
 * ### Why this is a ninth shape
 *
 * The bar every shape has had to clear (ADR-030, ADR-033, ADR-034, ADR-040,
 * ADR-044, ADR-046, ADR-047) and that ADR-036 and ADR-045 each refused: it is a new
 * *kind* of data, not a familiar kind in a new lesson.
 *
 * A hash is **an input of any size, a fixed-size output, and no positional
 * relationship whatsoever between them**. That last clause is the lesson, and it is
 * what every existing shape would contradict:
 *
 *  - `CipherScene` is the near miss by subject and the furthest by shape. Its two
 *    messages are *the same length and aligned position by position*, because
 *    plaintext letter 3 became ciphertext letter 3. Hashing `hi` gives 64 hex
 *    characters, and digest character 3 came from the whole message. Drawing them
 *    aligned would assert the one thing that is false;
 *  - `BitwiseScene` shares one set of columns across its rows, which is the same
 *    lie in stronger form — there is no column here that input and digest both sit
 *    in;
 *  - `SequenceScene` is one row whose slots are positions. Digest position 7 is not
 *    a concept this lesson has, and `groups` would be drawing structure into a
 *    value that has none;
 *  - `BucketScene` is the interesting one, because it already carries a *hash
 *    flow*: `GET 12 → HASH 12 % 5 → BUCKET ?`. But its flow ends in an index into a
 *    five-row table, and the table is the lesson. Here there is no table, no
 *    bucket, no key and no value — the digest is the whole output, and it is 64
 *    characters rather than one small number;
 *  - `CountingScene` and `PrefixScene` are rows of cells indexed by value and by
 *    position; `DpTableScene` has two axes and a cell is a point in that space.
 *    None of them holds a string, and a 64-character digest chopped into `Cell`s
 *    would be sixty-four boxes asserting sixty-four meanings.
 *
 * So the data is: **a labelled pipeline, a digest as text, and rows to compare** —
 * and comparison is the only way the three properties this lesson teaches can be
 * shown at all. Fixed length is two rows of different input and the same output
 * length; determinism is one message hashed twice; the avalanche is one character
 * changed. Each is a *relationship between rows*, which is why the rows are
 * first-class scene data rather than something the copy describes.
 *
 * What it did **not** need: no new `VizEvent`, no new interaction model, no new
 * cell state, and no change to any existing lesson.
 */
data class HashScene(
    /**
     * The conceptual pipeline, left to right — always every stage.
     *
     * Showing only the stage in play would turn the pipeline into a progress bar.
     * It is on screen whole from the first frame for the reason the XOR truth
     * table is (ADR-047): it is the thing the learner is meant to be reading, and a
     * diagram that comes and goes reads as a hint rather than as the shape of the
     * operation.
     */
    val pipeline: List<HashStage>,
    /**
     * The message currently in the pipeline, and what it became.
     *
     * Null before anything has been hashed — the rule the hash flow set (ADR-030):
     * an answer already on screen is not a question, and an empty pipeline should
     * not print a digest it has not produced.
     */
    val digest: HashDigest? = null,
    /**
     * Messages set against each other, in the order the beat wants them read.
     *
     * Empty when the beat is about one message. Never the whole dataset at once:
     * the projector shows the two or three rows that answer the question being
     * asked, because a screen of every row answers none of them in particular.
     */
    val comparisons: List<HashRow> = emptyList(),
    /**
     * The two statements the learner is choosing between, in the order and tones
     * of the buttons beneath them.
     *
     * This is `DpTableScene.ChoiceStrip`'s arrangement reused (DESIGN_SYSTEM.md
     * §6.16i): a `DecisionButton` is one line at `labelLarge`, so a statement long
     * enough to be unambiguous cannot live on it. The button carries the short
     * word and the card above carries the claim, which keeps both readable and
     * keeps the options at identical visual weight (PRODUCT_SPEC.md §5).
     */
    val claims: List<HashClaim> = emptyList(),
    /** e.g. "SHA-256". */
    val badge: Badge? = null,
    val meters: List<MeterReadout> = emptyList(),
    /** Renames a cell state in the legend, as [SequenceScene.legendLabels] does. */
    val legendLabels: Map<CellState, String> = emptyMap(),
) : Scene {

    /** Every state on screen, so the legend can name exactly what is drawn. */
    val legendStates: Set<CellState>
        get() = (pipeline.map { it.state } + comparisons.map { it.state }).toSet()
}

/**
 * One stage of the pipeline.
 *
 * [detail] is the stage's own annotation — `"256 bits"` under the output, the
 * message under the input — and is null for a stage that has nothing to add yet.
 *
 * The SHA-256 stage is deliberately **one stage and not sixty-four**. Padding, the
 * message schedule and the compression rounds are real and are what the box stands
 * for; a beginner lesson that drew them would be teaching the implementation
 * instead of the idea, and faking them would be worse (ADR-048).
 */
data class HashStage(
    val label: String,
    val detail: String? = null,
    val state: CellState = CellState.IDLE,
)

/**
 * A message and the digest it produced.
 *
 * [groups] is [hex] split for reading and nothing more — the characters and their
 * order are identical, so the exact value is always preserved on screen.
 */
data class HashDigest(
    val input: String,
    val inputLength: Int,
    val hex: String,
    val groups: List<String>,
    val bits: Int,
    val bytes: Int,
)

/**
 * One row of a comparison: a message, its digest, and how it differs from the row
 * it is being read against.
 *
 * [differing] holds digest character positions, so the renderer can colour the
 * characters that changed rather than assert in prose that most of them did. Empty
 * for a row that is not being compared to anything, and — importantly — empty for
 * two identical digests, which is what makes the determinism beat a picture of
 * *nothing* having changed.
 */
data class HashRow(
    val label: String,
    val input: String,
    val inputLength: Int,
    val hex: String,
    val groups: List<String>,
    val state: CellState = CellState.IDLE,
    val differing: Set<Int> = emptySet(),
) {
    /** Always 64 for a real digest, which is the point the fixed-length beat makes. */
    val hexLength: Int get() = hex.length
}

/**
 * One of the statements on offer, as the learner reads it.
 *
 * It carries no `correct` flag, for the reason [ActionOption] does not
 * (ARCHITECTURE.md §6): a renderer that knew which claim was true could style it
 * differently by accident, and the decision would answer itself.
 */
data class HashClaim(
    /** The full statement, long enough to be unambiguous. */
    val text: String,
    /** The word on the button beneath it, so the pairing is unmistakable. */
    val choice: String,
)
