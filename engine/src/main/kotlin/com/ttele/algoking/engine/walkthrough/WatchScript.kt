package com.ttele.algoking.engine.walkthrough

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.MidpointReadout
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.SceneProjector
import com.ttele.algoking.engine.scene.Scene

/**
 * WATCH is a walkthrough, not a video — it is a list of deterministic algorithmic
 * states, each with the sentence that explains it. The learner advances it; nothing
 * advances on a timer.
 *
 * The machinery here is generic: it walks the trace the real algorithm produces and
 * asks a per-algorithm [WatchNarrator] to caption each frame. Bubble Sort, Two
 * Pointers and Sliding Window plug in by supplying a narrator, not a new screen.
 */
data class WatchStep(
    val index: Int,
    val kind: WatchStepKind,
    /** The exact visual state, projected from a real algorithm state. */
    val scene: Scene,
    /** One short sentence. Never a paragraph. */
    val headline: NarrationKey,
    /** One more short sentence, when it earns its place. */
    val support: NarrationKey? = null,
    /** Rendered as a chip: `45 < 73`. */
    val comparison: ComparisonReadout? = null,
    /** Rendered as a chip: `mid = (0 + 8) ÷ 2 = 4`. */
    val midpoint: MidpointReadout? = null,
    /** The closing recap, as separate lines. */
    val bullets: List<NarrationKey> = emptyList(),
    /** One unscored checkpoint, to prepare the learner for Try. */
    val prediction: WatchPrediction? = null,
)

/**
 * An unscored checkpoint inside a walkthrough.
 *
 * It exists to make the learner commit to an answer before Try asks for real. It is
 * never scored, never blocks, and never changes the algorithm state.
 */
data class WatchPrediction(
    val prompt: NarrationKey,
    val options: List<NarrationKey>,
    val correctIndex: Int,
    val whenRight: NarrationKey,
    val whenWrong: NarrationKey,
)

enum class WatchStepKind {
    /** The problem, before anything happens. */
    SETUP,

    /** Attention moves to a cell. */
    EXAMINE,

    /** The comparison is stated. */
    COMPARE,

    /** Part of the search space leaves. */
    ELIMINATE,

    FOUND,
    NOT_FOUND,

    /** A swap actually happened. */
    SWAP,

    /** Examined and deliberately left alone. */
    KEEP,

    /** A pass finished and one more value is provably final. */
    PASS_COMPLETE,

    /** The array is sorted. */
    SORTED,

    /** One unscored checkpoint, to prepare the learner for Try. */
    PREDICT,

    /** An item entered a data structure. */
    ADD,

    /** An item left a data structure. */
    REMOVE,

    /** An item was read without being taken. */
    PEEK,

    /** The single engineered idea of the lesson. */
    INSIGHT,

    /** The recap that hands over to Try. */
    SUMMARY,
}

data class ComparisonReadout(val left: Int, val relation: Relation, val right: Int)

// `MidpointReadout` lives in `decision/` — Watch shows it, but Try and Challenge
// attach it to the decision it explains, and walkthrough already depends on
// decision rather than the other way round.

@JvmInline
value class WatchScript(val steps: List<WatchStep>) {
    val size: Int get() = steps.size
    operator fun get(index: Int): WatchStep = steps[index.coerceIn(0, steps.lastIndex)]
}

/**
 * The algorithm-specific half: which frames deserve a step, and what each one says.
 * Presentation knowledge lives beside the algorithm; the builder stays generic.
 */
interface WatchNarrator<S : Any> {

    /** Steps shown before the algorithm does anything. */
    fun opening(state: S, scene: Scene): List<PartialStep>

    /**
     * Steps for one applied frame. A single engine transition may deserve more than
     * one beat — "highlight the middle" and "state the comparison" are one
     * transition but two things to understand.
     */
    fun onFrame(previous: S, frame: Frame<S>, scene: Scene): List<PartialStep>

    /** The insight and the recap, once the run has terminated. */
    fun closing(state: S, metrics: Metrics, scene: Scene): List<PartialStep>
}

/** A step before the builder assigns it an index. */
data class PartialStep(
    val kind: WatchStepKind,
    val scene: Scene,
    val headline: NarrationKey,
    val support: NarrationKey? = null,
    val comparison: ComparisonReadout? = null,
    val midpoint: MidpointReadout? = null,
    val bullets: List<NarrationKey> = emptyList(),
    val prediction: WatchPrediction? = null,
)

class WatchScriptBuilder<S : Any, A : Action>(
    private val algorithm: Algorithm<S, A>,
    private val projector: SceneProjector<S>,
    private val narrator: WatchNarrator<S>,
) {
    fun build(dataset: Dataset): WatchScript {
        val runner = AlgorithmRunner(algorithm, dataset)
        val trace = runner.runToCompletion()
        val frames = trace.frames

        val partials = mutableListOf<PartialStep>()
        val first = frames.first()
        partials += narrator.opening(first.state, projector.project(first.state, first.events))

        for (i in 1 until frames.size) {
            val frame = frames[i]
            partials += narrator.onFrame(
                previous = frames[i - 1].state,
                frame = frame,
                scene = projector.project(frame.state, frame.events),
            )
        }

        val last = frames.last()
        partials += narrator.closing(
            state = last.state,
            metrics = last.metrics,
            scene = projector.project(last.state, last.events),
        )

        return WatchScript(
            partials.mapIndexed { index, p ->
                WatchStep(
                    index = index,
                    kind = p.kind,
                    scene = p.scene,
                    headline = p.headline,
                    support = p.support,
                    comparison = p.comparison,
                    midpoint = p.midpoint,
                    bullets = p.bullets,
                    prediction = p.prediction,
                )
            },
        )
    }
}
