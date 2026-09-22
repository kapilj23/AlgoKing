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
     * The step the lesson is on, drawn as a flow: what goes in, what acts on it,
     * what comes out.
     *
     * **This is the picture Act I is made of** (ADR-052). Before the arithmetic is
     * introduced there is nothing to put in [chain], and a lesson whose whole first
     * half draws an empty panel is a lesson with no picture. What it draws instead
     * is the thing the learner is being told: `message → public key → encrypt →
     * ciphertext`, one node per line, with the node the beat is about lit.
     *
     * Null on the beats that have no flow of their own — the two that are about the
     * keys themselves, and the caveat.
     */
    val flow: FlowView? = null,
    /**
     * The one beat that shows text turning into numbers.
     *
     * `M E E T` over `77 69 69 84`. It is the join between the lesson's two layers,
     * and without it the toy layer's `m = 4` arrives from nowhere — a learner who has
     * spent nine beats on a message is owed an account of how RSA gets a *number* to
     * work on (ADR-053).
     */
    val bridge: TextBridgeView? = null,
    /**
     * The arithmetic behind the flow beside it, revealed a line at a time.
     *
     * The **right-hand column** of DESIGN_SYSTEM.md §6.16n's two-column layout, and
     * it exists to keep a promise the flow cannot keep on its own: a learner who has
     * watched `4 → 9` happen is owed `c = 4³ mod 55 = 9` before they are asked to
     * believe it.
     *
     * It appears **only once the flow it explains has been shown**, never before, and
     * its last line is withheld while that value is the thing being asked for — the
     * same rule [DerivationStep.value] follows, applied to a worked calculation.
     */
    val maths: MathsPanel? = null,
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
     * Which of the lesson's two layers this frame belongs to — **the field that
     * keeps the lesson honest** (ADR-053).
     *
     * RSA's concept layer is a true account of what the algorithm is for, told on a
     * message a person would send. Its toy layer is real arithmetic on numbers small
     * enough to check by hand. **Neither is the other**: `n = 55` cannot encrypt
     * *"MEET AT 7"*, and a lesson that let the two blur would be teaching something
     * false in order to make a picture tidier.
     *
     * So every frame says which layer it is on, and the renderer prints it. Null on
     * the bridge beats, which are about the move between them.
     */
    val layer: LessonLayer? = null,
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
        get() = (
            chain.map { it.state } +
                keys.map { it.state } +
                (flow?.nodes?.map { it.state } ?: emptyList())
            ).toSet()
}

/**
 * Text, and the numbers a computer stores it as — the join between the two layers.
 *
 * Each character sits above its code, aligned, because *that* is the relationship:
 * `M` is 77, and it is 77 in the same column. This is one of the few places in the
 * app where two rows genuinely do correspond position by position, which is why it
 * is drawn as a pair of aligned rows rather than as a flow.
 *
 * [characters] and [codes] are always the same length, and the type says so by
 * holding pairs rather than two lists that could drift apart.
 */
data class TextBridgeView(
    /** The message, split into the characters shown. */
    val cells: List<TextBridgeCell>,
    /** True when the message was too long to show whole and has been cut. */
    val truncated: Boolean = false,
)

/** One character and the number a computer stores it as. */
data class TextBridgeCell(val character: String, val code: Int)

/**
 * Which of RSA's two layers a frame belongs to.
 *
 * Carried as data and printed as a banner, rather than left to the copy, because
 * "the lesson is careful about this" is the sort of thing that stays true only while
 * someone is watching (ADR-053).
 */
enum class LessonLayer(val label: String, val note: String) {
    /**
     * A message, two keys and what happens between them. True, and free of
     * arithmetic.
     */
    CONCEPT(
        label = "HOW RSA IS USED",
        note = "The ciphertext here stands for what encrypted bytes look like — " +
            "it is not this message run through the numbers below.",
    ),

    /**
     * The mechanism, on numbers a learner can check — and **not** this message being
     * encrypted, which is the thing the banner exists to say.
     */
    TOY(
        label = "EDUCATIONAL TOY EXAMPLE — NOT SECURE RSA",
        note = "Small numbers, so the arithmetic can be checked by hand. This is " +
            "the mechanism, not the message above.",
    ),
}

/**
 * One step of the lesson drawn as what-goes-in / what-acts / what-comes-out.
 *
 * Deliberately **not** a `HashScene` pipeline, which is the near miss. A pipeline
 * stage is a box the data passes through and carries no value of its own; every node
 * here is a value or a key the learner can read, and two of them are the same two key
 * cards drawn above. It is the same data the rest of the scene holds, arranged as the
 * sentence the beat is making.
 */
data class FlowView(
    /** "ENCRYPTING" / "DECRYPTING" / "THE ROUND TRIP". */
    val title: String,
    /** Top to bottom, with an arrow drawn between each pair. */
    val nodes: List<FlowNode>,
)

/**
 * One line of a [FlowView].
 *
 * [value] is null for a node that is an *action* rather than a thing — "ENCRYPT" has
 * no value, and printing one would make it look like a number the learner should
 * recognise. It is also null for a value the lesson has not produced yet, which the
 * renderer draws as `?`; [known] tells the two apart.
 */
data class FlowNode(
    /** "MESSAGE", "PUBLIC KEY", "ENCRYPT", "CIPHERTEXT". */
    val label: String,
    /**
     * `"MEET AT 7"`, `"8F 3A C1 D4 9B 22"`, `4`, `(3, 55)` — or null for an action,
     * or for a value the lesson has not produced yet.
     */
    val value: String? = null,
    val kind: FlowNodeKind,
    /** How to draw [value]. A message is not a number and must not look like one. */
    val style: FlowValueStyle = FlowValueStyle.NUMBER,
    /** A short clause under the value, e.g. "Can be shared". */
    val note: String? = null,
    val state: CellState = CellState.IDLE,
) {
    /** Whether this node is a value the lesson has produced. */
    val known: Boolean get() = kind != FlowNodeKind.OPERATION && value != null
}

/**
 * How a [FlowNode]'s value is set.
 *
 * The lesson runs on two layers and they must not look alike (ADR-053): a message a
 * person wrote is prose, a ciphertext is bytes, and the toy arithmetic is numerals.
 * A learner who cannot tell at a glance which layer they are looking at is exactly
 * the learner who will come away thinking `n = 55` encrypted *"MEET AT 7"*.
 */
enum class FlowValueStyle {
    /** `4`, `9`, `(3, 55)` — the toy layer. Tabular numerals. */
    NUMBER,

    /** `"MEET AT 7"` — something a person wrote. Quoted, in prose weight. */
    TEXT,

    /** `8F 3A C1 D4 9B 22` — unreadable output. Monospace, so it reads as data. */
    BYTES,
}

/** What a [FlowNode] is, which is what decides how it is drawn. */
enum class FlowNodeKind {
    /** The message going in, or coming back. */
    MESSAGE,

    /** The ciphertext. */
    CIPHERTEXT,

    /** `(e, n)` — drawn in the violet the public half carries everywhere else. */
    PUBLIC_KEY,

    /** `(d, n)` — drawn in the gold the private half carries, with its lock. */
    PRIVATE_KEY,

    /** "ENCRYPT" / "DECRYPT". A verb, not a value. */
    OPERATION,
}

/**
 * The worked arithmetic for the beat the flow is showing.
 *
 * Lines arrive in order and the panel grows; nothing is ever re-flowed or replaced,
 * so a learner reading down it is reading the calculation in the order it happens.
 */
data class MathsPanel(
    /** "THE MATHEMATICS". */
    val title: String,
    val lines: List<MathsLine>,
)

/** One line of worked arithmetic, and how much weight it carries. */
data class MathsLine(val text: String, val emphasis: MathsEmphasis = MathsEmphasis.STEP)

/** What a [MathsLine] is doing, which is what decides how it is drawn. */
enum class MathsEmphasis {
    /** `c = mᵉ mod n` — the rule, before any number goes into it. */
    FORMULA,

    /** `m = 4` — one operand named. */
    SUBSTITUTION,

    /** `c = 4³ mod 55` — the calculation partway through. */
    STEP,

    /** `c = 9` — the answer, and the only line drawn as one. */
    RESULT,
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
    /**
     * `(3, 55)` — or **null in the concept layer**, where a key is a lock and a key
     * rather than a pair of numbers.
     *
     * Layer 1's whole job is to have no arithmetic on it (ADR-053), and a key card
     * printing `(3, 55)` there would put the first numbers of the lesson on the
     * screen that is meant to be free of them. The card still says which half it is
     * and what it is for; the numbers arrive with Layer 2, which is where they mean
     * something.
     */
    val printed: String? = null,
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
