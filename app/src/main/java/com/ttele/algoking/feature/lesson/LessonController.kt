package com.ttele.algoking.feature.lesson

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ttele.algoking.engine.catalog.LessonPack
import com.ttele.algoking.engine.challenge.Challenge
import com.ttele.algoking.engine.challenge.HintAccess
import com.ttele.algoking.engine.challenge.HintPolicy
import com.ttele.algoking.engine.scenario.Mission
import com.ttele.algoking.engine.challenge.ChallengeRun
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.MidpointReadout
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.scene.SequenceScene
import com.ttele.algoking.engine.scene.asMission

/** The three learning stages. There is no fourth — mastery is a *result*, not a stage. */
enum class Phase(val label: String, val blurb: String) {
    Watch("Watch", "Learn how the algorithm works."),
    Try("Try", "Practice the algorithm with guidance."),
    Challenge("Challenge", "Apply it independently to a new problem."),
}

data class OptionUi<A : Action>(val label: String, val action: A)

data class DecisionUi<A : Action>(
    val prompt: String,
    val options: List<OptionUi<A>>,
    val kind: DecisionKind,
    /** For [DecisionKind.CELL]: slot → the action that selects it. */
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
     * The learner did not get it, and **nothing moved**. [level] escalates Try's
     * guidance; Challenge stays terse whatever the level.
     */
    data class Wrong(
        val level: Int,
        override val body: String,
        val why: String?,
    ) : Feedback

    /** A hint the learner asked for. */
    data class Hint(val level: Int, override val body: String) : Feedback
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
    val hintsUsed: Int = 0,
    /** Whether the next hint is free, behind an ad, or the ladder is spent. */
    val hintAccess: HintAccess = HintAccess.Free,
    /** The story this run is set in, for the mission strip and the result. */
    val mission: Mission? = null,
)

val LessonUiState<*>.finished: Boolean get() = outcome != null

/**
 * One controller drives Try and Challenge, for **every** algorithm —
 * ARCHITECTURE.md §4.3.
 *
 * It is generic over the algorithm's state and action types and reads everything
 * else from a [LessonPack], so Binary Search and Bubble Sort share this file
 * rather than each getting their own. The stages differ in exactly two host
 * policies:
 *
 *  - **who resolves the mechanical beats.** Try lets the app advance pointers and
 *    pick middles; Challenge asks the learner for anything the algorithm exposes
 *    as a `CELL` decision.
 *  - **how much is said on a miss.** Try climbs a teaching ladder; Challenge gives
 *    one neutral clue and records a mistake.
 *
 * In both, a wrong answer never touches the algorithm state.
 */
class LessonController<S : Any, A : Action>(
    val phase: Phase,
    val pack: LessonPack<S, A>,
    val dataset: Dataset,
    val challenge: Challenge? = null,
) {
    private val runner = AlgorithmRunner(pack.algorithm, dataset)

    /**
     * The story this run is wrapped in, when there is one.
     *
     * It touches exactly one thing: the words on the cells. Every state, pointer
     * and region still comes from the algorithm, so a mission can never change
     * what Binary Search does — only what the learner thinks they are searching.
     */
    private val mission = challenge?.mission

    private var attempt = 0
    private var hintLevel = 0
    private var wrongTick = 0
    private var decisionsOffered = 0
    private var correctDecisions = 0
    private var mistakes = 0
    private var hintsUsed = 0
    private val startedAt = System.currentTimeMillis()

    // Declared last: project() reads the fields above, so they must be initialised first.
    var ui by mutableStateOf(settleAndProject())
        private set

    // ── Public API ────────────────────────────────────────────────────────────

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
                hintLevel = 0
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
                        // Challenge never escalates, so it never climbs past the
                        // first rung — its wording must not imply it did.
                        level = if (phase == Phase.Challenge) 1 else verdict.level,
                        body = if (phase == Phase.Challenge) {
                            // One neutral clue. Challenge must not become Try.
                            Narration.resolve(decision.minimalFeedback)
                        } else {
                            Narration.resolve(verdict.guidance)
                        },
                        // The specific reason is teaching, so Challenge withholds it.
                        why = if (phase == Phase.Challenge) {
                            null
                        } else {
                            verdict.whyWrong?.let(Narration::resolve)
                        },
                    ),
                )
            }
        }
    }

    /**
     * Hints are opt-in and progressive; they are recorded and they never solve the
     * problem outright — the last rung names the rule, the learner still acts.
     */
    fun requestHint() {
        val probe = runner.probe() as? Probe.Decide<A> ?: return
        val ladder = probe.decision.hintLadder.ifEmpty { listOf(probe.decision.hint) }
        val body = ladder.getOrElse(hintLevel) { ladder.last() }
        hintsUsed++
        hintLevel = (hintLevel + 1).coerceAtMost(ladder.lastIndex)
        ui = project(feedback = Feedback.Hint(hintLevel, Narration.resolve(body)))
    }

    /** Clears the feedback card and returns the learner to the same decision. */
    fun dismissFeedback() {
        ui = ui.copy(feedback = null)
    }

    /** Free in Try. Undo means "unmake my last decision", not the app's bookkeeping. */
    fun rewind() {
        runner.rewind(1)
        while (isHostResolved() && runner.canRewind()) runner.rewind(1)
        attempt = 0
        ui = settleAndProject()
    }

    fun restart() {
        runner.reset()
        attempt = 0
        hintLevel = 0
        wrongTick = 0
        decisionsOffered = 0
        correctDecisions = 0
        mistakes = 0
        hintsUsed = 0
        ui = settleAndProject()
    }

    /** Everything the result screen needs. */
    fun runSummary(): ChallengeRun? {
        val c = challenge ?: return null
        val m = runner.current.metrics
        return ChallengeRun(
            challenge = c,
            decisions = decisionsOffered,
            correctDecisions = correctDecisions,
            mistakes = mistakes,
            hintsUsed = hintsUsed,
            comparisons = m.comparisons,
            swaps = m.swaps,
            passes = m.passes,
            elapsedMillis = System.currentTimeMillis() - startedAt,
            completed = ui.finished,
            targetFound = ui.outcome is Outcome.Found,
        )
    }

    fun finalMetrics(): Metrics = runner.current.metrics.copy(
        steps = decisionsOffered,
        wrongDecisions = mistakes,
        hintsUsed = hintsUsed,
        elapsedMillis = System.currentTimeMillis() - startedAt,
    )

    // ── Host policy ───────────────────────────────────────────────────────────

    /**
     * Beats the *app* performs rather than the learner.
     *
     * `Mechanical` probes are always the app's — pointer advancement and pass
     * boundaries are bookkeeping, and tapping the only legal target teaches a
     * gesture (PRODUCT_SPEC.md §3). A `CELL` decision is the app's only in Try,
     * where the learner's job is the judgement rather than the arithmetic.
     */
    private fun isHostResolved(): Boolean {
        val probe = runner.probe()
        return when {
            probe is Probe.Mechanical -> true
            phase == Phase.Challenge -> false
            // Only decisions the algorithm has *marked* as the app-s bookkeeping.
            // Anything else is a judgement, and Try must ask it.
            else -> probe is Probe.Decide && probe.decision.autoInTry
        }
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

    // ── Projection ────────────────────────────────────────────────────────────

    private fun project(feedback: Feedback? = null): LessonUiState<A> {
        val frame = runner.current
        val projected = pack.projector.project(frame.state, frame.events)
        val scene: Scene = when {
            mission == null || projected !is SequenceScene -> projected
            else -> projected.asMission(mission.labels, mission.targetLabel)
        }

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
            canRewind = runner.canRewind() && phase != Phase.Challenge,
            step = frame.metrics.comparisons,
            mistakes = mistakes,
            hintsUsed = hintsUsed,
            hintAccess = HintPolicy.access(hintLevel),
            mission = mission,
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
