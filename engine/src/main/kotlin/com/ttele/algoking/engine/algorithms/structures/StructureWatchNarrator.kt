package com.ttele.algoking.engine.algorithms.structures

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchPrediction
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The walkthrough for a linear structure.
 *
 * One narrator serves both, because the *shape* of the story is the same — items
 * go in, one comes out, the learner is asked which one before it does. Only the
 * sentences differ, and they come from [StructureFlavour.watch]. Every step is a
 * real engine state; nothing here is a hand-authored animation.
 */
class StructureWatchNarrator(
    private val flavour: StructureFlavour,
) : WatchNarrator<StructureState> {

    /** Used to redraw the state the learner is predicting *from*. */
    private val projector = StructureProjector(flavour)

    // The support line earns its place once. Repeating it on every push would turn
    // a walkthrough into a wall of text.
    private var explainedAdd = false
    private var explainedRemove = false

    override fun opening(state: StructureState, scene: Scene) = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(flavour.watch.setup),
            support = NarrationKey(flavour.watch.setupSupport),
        ),
    )

    override fun onFrame(
        previous: StructureState,
        frame: Frame<StructureState>,
        scene: Scene,
    ): List<PartialStep> = when (val task = previous.task) {
        is StructureTask.Predict -> listOf(predictStep(previous))
        is StructureTask.Operate -> operateStep(previous, task, frame, scene)
        null -> emptyList()
    }

    /**
     * The checkpoint is drawn from the state *before* the prediction is answered —
     * with the end labels hidden, because the projector knows a prediction is live.
     * Showing the post-answer scene would put the answer on screen next to the
     * question.
     */
    private fun predictStep(previous: StructureState): PartialStep {
        val size = previous.items.size
        val drawn = (0 until size)
            .sortedBy { flavour.displaySlot(size, it) }
            .map { previous.items[it] }
        val answerIndex = if (flavour.removesFromFront) 0 else previous.items.lastIndex
        return PartialStep(
            kind = WatchStepKind.PREDICT,
            scene = projector.project(previous, emptyList()),
            headline = NarrationKey(flavour.watch.predictPrompt),
            prediction = WatchPrediction(
                prompt = NarrationKey(flavour.watch.predictPrompt),
                options = drawn.map { NarrationKey(NarrationId.STRUCT_VALUE, listOf(it)) },
                correctIndex = flavour.displaySlot(size, answerIndex),
                whenRight = NarrationKey(
                    flavour.watch.predictRight,
                    listOf(previous.items[answerIndex]),
                ),
                whenWrong = NarrationKey(
                    flavour.watch.predictWrong,
                    listOf(previous.items[answerIndex]),
                ),
            ),
        )
    }

    private fun operateStep(
        previous: StructureState,
        task: StructureTask.Operate,
        frame: Frame<StructureState>,
        scene: Scene,
    ): List<PartialStep> = when (task.op) {
        StructureOp.ADD -> {
            val value = task.value ?: 0
            val step = PartialStep(
                kind = WatchStepKind.ADD,
                scene = scene,
                headline = NarrationKey(flavour.watch.added, listOf(value)),
                support = NarrationKey(flavour.watch.addedSupport).takeUnless { explainedAdd },
            )
            explainedAdd = true
            listOf(step)
        }

        StructureOp.REMOVE -> {
            val value = frame.state.lastRemoved
            val step = PartialStep(
                kind = WatchStepKind.REMOVE,
                scene = scene,
                headline = if (value == null) {
                    NarrationKey(flavour.emptyNarration())
                } else {
                    NarrationKey(flavour.watch.removed, listOf(value))
                },
                support = NarrationKey(flavour.watch.removedSupport)
                    .takeUnless { explainedRemove || value == null },
            )
            explainedRemove = true
            listOf(step)
        }

        StructureOp.PEEK -> listOf(
            PartialStep(
                kind = WatchStepKind.PEEK,
                scene = scene,
                headline = frame.state.peeked
                    ?.let { NarrationKey(flavour.watch.peeked, listOf(it)) }
                    ?: NarrationKey(flavour.emptyNarration()),
                support = NarrationKey(flavour.watch.peekedSupport),
            ),
        )
    }

    override fun closing(
        state: StructureState,
        metrics: Metrics,
        scene: Scene,
    ) = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(flavour.watch.insight),
            support = NarrationKey(flavour.watch.insightSupport),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(flavour.watch.summary, listOf(state.items.size)),
            support = NarrationKey(flavour.watch.summarySupport),
            bullets = flavour.watch.ideas.map { NarrationKey(it) },
        ),
    )
}
