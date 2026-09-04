package com.ttele.algoking.feature.lesson

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ttele.algoking.engine.catalog.LessonPack
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.MidpointReadout
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.scene.Scene

/**
 * The two learning stages — PRODUCT_SPEC.md §2.
 *
 * The MVP spine is WATCH → TRY, and finishing TRY completes the algorithm.
 * CHALLENGE is deferred to V2 (`docs/v2-challenge.md`); when it returns it becomes
 * a third entry here, and every screen that maps over [Phase.entries] picks it up.
 */
enum class Phase(val label: String, val blurb: String) {
    Watch("Watch", "Learn how the algorithm works."),
    Try("Try", "Practice the algorithm with guidance."),
}

data class OptionUi<A : Action>(val label: String, val action: A)

data class DecisionUi<A : Action>(
    val prompt: String,
    val options: List<OptionUi<A>>,
    val kind: DecisionKind,
    /** For [DecisionKind.CELL]: slot to the action that selects it. */
    val cellActions: Map<Int, A> = emptyMap(),
    /** The working behind the right answer, for decisions that are arithmetic. */
    val midpoint: MidpointReadout? = null,
)

/**
 * What the screen says back to the learner about their last decision.
 *
 * A [Wrong] never accompanies a state change — see [LessonController.choose].
 */
sealed interface Feedback {
    val body: String

    /** The learner got it. Explains what their choice achieved. */
    data class Correct(override val body: String) : Feedback

    /**
     * The learner did not get it, and **nothing moved**. [level] drives the
     * escalating guidance ladder: point at the evidence, ask the reasoning
     * question, then say it plainly.
     */
    data class Wrong(
        val level: Int,
        override val body: String,
        val why: String?,
    ) : Feedback
}

data class LessonUiState<A : Action>(
    val scene: Scene,
    val narration: String = "",
    val decision: DecisionUi<A>? = null,
    val feedback: Feedback? = null,
    /** Bumped on every wrong answer so the UI can shake or flash again. */
    val wrongTick: Int = 0,
    val metrics: Metrics = Metrics.EMPTY,
    val outcome: Outcome? = null,
    val canRewind: Boolean = false,
    val step: Int = 0,
    val mistakes: Int = 0,
)

val LessonUiState<*>.finished: Boolean get() = outcome != null

/**
 * One controller drives TRY, for **every** algorithm — ARCHITECTURE.md §4.3.
 *
 * It is generic over the algorithm's state and action types and reads everything
 * else from a [LessonPack], so Binary Search and Bubble Sort share this file
 * rather than each getting their own.
 *
 * The host resolves the beats that are bookkeeping rather than judgement — pointer
 * advancement, pass boundaries, and the arithmetic an algorithm marks with
 * `Decision.autoInTry`. Everything else is the learner's.
 *
 * **A wrong answer never touches the algorithm state.**
 */
class LessonController<S : Any, A : Action>(
    val phase: Phase,
    val pack: LessonPack<S, A>,
    val dataset: Dataset,
) {
    private val runner = AlgorithmRunner(pack.algorithm, dataset)

    private var attempt = 0
    private var wrongTick = 0
    private var decisionsOffered = 0
    private var correctDecisions = 0
    private var mistakes = 0
    private val startedAt = System.currentTimeMillis()

    // Declared last: project() reads the fields above, so they must be initialised first.
    var ui by mutableStateOf(settleAndProject())
        private set

    // -- Public API -----------------------------------------------------------

    /**
     * The learner decides.
     *
     * **A wrong answer is a learning event, not a state transition.** The runner is
     * not touched — the learner is returned to the exact same decision, and the only
     * way forward is the correct action. The rule lives in [DecisionValidation], so
     * it holds for every algorithm rather than being re-implemented per lesson.
     */
    fun choose(action: A) {
        val probe = runner.probe() as? Probe.Decide<A> ?: return
        val decision = probe.decision

        when (val verdict = DecisionValidation.validate(decision, action, attempt)) {
            is Validation.Accept -> {
                attempt = 0
                decisionsOffered++
                correctDecisions++
                runner.apply(verdict.action)
                ui = settleAndProject(
                    feedback = Feedback.Correct(Narration.resolve(verdict.feedback)),
                )
            }

            is Validation.Retry -> {
                attempt++
                decisionsOffered++
                mistakes++
                wrongTick++
                ui = project(
                    feedback = Feedback.Wrong(
                        level = verdict.level,
                        body = Narration.resolve(verdict.guidance),
                        why = verdict.whyWrong?.let(Narration::resolve),
                    ),
                )
            }
        }
    }

    /** Clears the feedback card and returns the learner to the same decision. */
    fun dismissFeedback() {
        ui = ui.copy(feedback = null)
    }

    /** Undo means "unmake my last decision", not the app's bookkeeping. */
    fun rewind() {
        runner.rewind(1)
        while (isHostResolved() && runner.canRewind()) runner.rewind(1)
        attempt = 0
        ui = settleAndProject()
    }

    fun restart() {
        runner.reset()
        attempt = 0
        wrongTick = 0
        decisionsOffered = 0
        correctDecisions = 0
        mistakes = 0
        ui = settleAndProject()
    }

    /** Everything the completion screen needs. */
    fun finalMetrics(): Metrics = runner.current.metrics.copy(
        steps = decisionsOffered,
        wrongDecisions = mistakes,
        elapsedMillis = System.currentTimeMillis() - startedAt,
    )

    // -- Host policy ----------------------------------------------------------

    /**
     * Beats the *app* performs rather than the learner.
     *
     * `Mechanical` probes are always the app's — pointer advancement and pass
     * boundaries are bookkeeping, and tapping the only legal target teaches a
     * gesture (PRODUCT_SPEC.md §3). Beyond those, only decisions the algorithm has
     * *marked* as the app's arithmetic are resolved for the learner; anything else
     * is a judgement, and Try must ask it.
     */
    private fun isHostResolved(): Boolean {
        val probe = runner.probe()
        return probe is Probe.Mechanical ||
            (probe is Probe.Decide && probe.decision.autoInTry)
    }

    private fun settle() {
        var guard = 0
        while (guard++ < SETTLE_LIMIT && isHostResolved()) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return
            }
        }
    }

    private fun settleAndProject(feedback: Feedback? = null): LessonUiState<A> {
        settle()
        return project(feedback)
    }

    // -- Projection -----------------------------------------------------------

    private fun project(feedback: Feedback? = null): LessonUiState<A> {
        val frame = runner.current
        val scene = pack.projector.project(frame.state, frame.events)

        val probe = runner.probe()
        @Suppress("UNCHECKED_CAST")
        val decision = (probe as? Probe.Decide<A>)?.let { toUi(it.decision) }
        val outcome = (probe as? Probe.Terminal)?.outcome

        return LessonUiState(
            scene = scene,
            narration = frame.narration?.let(Narration::resolve).orEmpty(),
            decision = decision,
            feedback = feedback,
            wrongTick = wrongTick,
            metrics = finalMetrics(),
            outcome = outcome,
            canRewind = runner.canRewind(),
            step = frame.metrics.comparisons,
            mistakes = mistakes,
        )
    }

    private fun toUi(decision: Decision<A>) = DecisionUi(
        prompt = Narration.resolve(decision.prompt),
        options = decision.options.map { OptionUi(Narration.resolve(it.label), it.action) },
        kind = decision.kind,
        midpoint = decision.midpoint,
        cellActions = decision.options
            .mapNotNull { option -> option.slot?.let { it to option.action } }
            .toMap(),
    )

    private companion object {
        const val SETTLE_LIMIT = 512
    }
}
