package com.algorithms.algoking.engine.scene

/**
 * A chain of derived values, the key pair it produces, and the round trip through
 * them — DESIGN_SYSTEM.md §6.16n.
 *
 * ### Why this is an eleventh shape
 *
 * The bar every shape has had to clear (ADR-030, ADR-033, ADR-034, ADR-040,
 * ADR-044, ADR-046, ADR-047, ADR-048, ADR-049) and that ADR-036 and ADR-045 each
 * refused: it is a new *kind* of data, not a familiar kind in a new lesson.
 *
 * RSA's data is a **derivation chain** — a short list of named scalars where each
 * one is produced from earlier ones by a printed formula, and **the dependency is
 * the lesson**. `n` comes from `p` and `q`; `φ(n)` from the same two; `d` from `e`
 * and `φ(n)`; and the two keys from `e`, `d` and `n`. A learner who can recite the
 * six numbers but not say which produced which has not learned RSA.
 *
 * Nothing existing says that:
 *
 *  - `SequenceScene` is one row whose slots are **positions**. These are not
 *    positions and their order is a dependency order, not an index. Its `equation`
 *    field is one `PrefixEquation` for the whole scene — a single two-operand line
 *    with `+` or `−` — and this needs six different formulas, one per value, two of
 *    which (`mod`, exponentiation) it cannot express;
 *  - `DpTableScene` and `CountingScene` are grids indexed by quantities; there is no
 *    second axis here at all;
 *  - `BlockCipherScene` is the near miss, because it carries a key schedule — but
 *    that is *one* key expanded into many of the same kind, drawn as hex, with a
 *    4 × 4 State beside it. RSA's two keys are **different kinds with opposite
 *    rules**: one is published and one must not be, and saying which is which is
 *    half of what asymmetric means. Its State grid, round strip and variant table
 *    would all be null here, which is the union-pretending-to-be-a-record ADR-047
 *    refused;
 *  - `HashScene` has a pipeline of stages, but a stage is a labelled box the data
 *    passes *through*, carrying no value of its own. Every step here **is** a value,
 *    and the whole point is that it stays on screen and gets read by later ones;
 *  - `CipherScene` and `BitwiseScene` align rows position by position, which is a
 *    relationship this data does not have.
 *
 * So what it holds is: **a chain of named values with their formulas, two key cards
 * with opposite rules, and a round trip that shows one undoing the other.**
 *
 * What it did **not** need: no new `VizEvent`, no new cell state, and no change to
 * any existing lesson.
 */
data class KeyPairScene(
    /**
     * The derivation, in the order it is built.
     *
     * Every step stays on screen once it arrives, because later steps read it — the
     * opposite of a pipeline, where a stage is done with once the data has passed
     * through. A value not yet known is null rather than zero, so the picture never
     * claims a number the lesson has not produced (ADR-030's rule).
     */
    val chain: List<DerivationStep> = emptyList(),
    /**
     * The two halves of the pair.
     *
     * Drawn together, always, once they exist. They share a modulus and differ in
     * exactly one number and one rule, and a learner who sees them apart can read
     * them as two unrelated keys — which is the misconception the lesson is for.
     */
    val keys: List<KeyCard> = emptyList(),
    /** The message going out and coming back, when the lesson has got that far. */
    val roundTrip: RoundTripView? = null,
    /**
     * Full-width cards the learner taps, for a judgement whose options are sentences
     * rather than numbers.
     *
     * A `DecisionButton` is one line at `labelLarge` and four share a row, which
     * leaves about 76dp each — enough for `(3, 55)` and nothing like enough for
     * "Asymmetric cryptography". The wall Two Pointers hit (ADR-032) and AES solved
     * by making the round strip the control (ADR-049); here the cards are the
     * control, stacked, each clearing the 48dp touch minimum.
     *
     * [ChoiceCard.slot] is what a `DecisionKind.CELL` option selects.
     */
    val choices: List<ChoiceCard> = emptyList(),
    /**
     * The standing reminder that these numbers are a demonstration.
     *
     * On screen for the whole lesson rather than saved for the recap. A learner who
     * looks away at the wrong moment should still never be able to come back
     * believing they have watched real encryption — and unlike a bullet at the end,
     * a caveat attached to the picture cannot be skipped.
     */
    val caveat: String? = null,
    /**
     * Whether the caveat is the subject of this beat rather than a standing note.
     *
     * Quiet for the whole lesson — a small pill that does not compete with the
     * arithmetic — and prominent on the one beat that is *about* the difference
     * between these numbers and real RSA. That beat has nothing else to change, and
     * ADR-020 makes a step where nothing changed a bug rather than a beat.
     */
    val caveatProminent: Boolean = false,
    /** e.g. "RSA". */
    val badge: Badge? = null,
    val meters: List<MeterReadout> = emptyList(),
    /** Renames a cell state in the legend, as [SequenceScene.legendLabels] does. */
    val legendLabels: Map<CellState, String> = emptyMap(),
) : Scene {

    /** Every state on screen, so the legend names exactly what is drawn. */
    val legendStates: Set<CellState>
        get() = (chain.map { it.state } + keys.map { it.state }).toSet()
}

/**
 * One value in the derivation, with the formula that produced it.
 *
 * [formula] is printed beside the value rather than instead of it, because the
 * lesson is the relationship and not the number: `40` on its own teaches nothing,
 * and `φ(n) = (p − 1)(q − 1) = 40` teaches the whole step.
 *
 * [value] is null while the value is unknown — before the run reaches it, or while
 * the learner is being asked for it. An answer already on screen is not a question
 * (ADR-030).
 */
data class DerivationStep(
    /** `n`, `φ(n)`, `e`, `d` — what this value is called. */
    val symbol: String,
    /** `p × q`, `(p − 1)(q − 1)`, `d × e ≡ 1 (mod φ(n))`. */
    val formula: String,
    val value: Long? = null,
    /**
     * What to print instead of [value], when the step holds more than one number.
     *
     * The first row of the chain is `p, q` — two values under one symbol, because
     * they are chosen together and neither is derived from anything. Printing one of
     * them beside the label `p, q` would be worse than printing neither. The same
     * job `Badge.valueLabel` does for a target that is not a bare number.
     */
    val valueLabel: String? = null,
    /** A short clause about why this step exists, when it needs one. */
    val note: String? = null,
    val state: CellState = CellState.IDLE,
) {
    /** Whether the lesson has produced this value yet. */
    val known: Boolean get() = value != null || valueLabel != null
}

/**
 * One half of the key pair, as the learner reads it.
 *
 * [secret] is the whole difference between the two and is carried as data so the
 * renderer draws it rather than the copy asserting it. It is deliberately **not**
 * expressed as "this one is dangerous" styling — a locked key is a normal thing,
 * and the lesson's point is which one you may hand out.
 */
data class KeyCard(
    /** "Public key" / "Private key". */
    val label: String,
    /** `(3, 55)`. */
    val printed: String,
    /** "share it freely" / "never leave the device". */
    val rule: String,
    /** What it is for in this lesson, e.g. "encrypts". */
    val use: String,
    val secret: Boolean,
    val state: CellState = CellState.IDLE,
)

/** How far the message has got round the loop. */
enum class RoundTripStage {
    /** Nothing has been encrypted yet. */
    READY,

    /** The ciphertext exists; the message has not come back. */
    ENCRYPTED,

    /** Both, and they match — which is the thing the lesson is proving. */
    DECRYPTED,
}

/**
 * The message out and back: `4 → 9 → 4`.
 *
 * [recovered] is read out of a real decryption rather than copied from [message] —
 * the call `XorState.recovered` makes (ADR-047). If applying the private key did not
 * give the message back, this lesson would say so rather than assert what it hoped
 * for.
 */
data class RoundTripView(
    val message: Long,
    val ciphertext: Long?,
    val recovered: Long?,
    /** `c = mᵉ mod n`, with the numbers filled in. */
    val encryptFormula: String,
    /** `m = c^d mod n`, likewise. */
    val decryptFormula: String,
    val stage: RoundTripStage,
)

/**
 * One tappable card, for a judgement whose options are sentences.
 *
 * It carries no `correct` flag, for the reason [ActionOption] does not
 * (ARCHITECTURE.md §6): a renderer that knew which card was right could style it
 * differently by accident, and the decision would answer itself.
 */
data class ChoiceCard(
    /** Which option this card selects. */
    val slot: Int,
    /** "Asymmetric cryptography". */
    val title: String,
    /** The clause that makes it unambiguous. */
    val detail: String,
    val state: CellState = CellState.IDLE,
)
