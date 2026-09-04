package com.ttele.algoking.engine.event

/**
 * Metrics — ARCHITECTURE.md §5.3.
 * Derived, never hand-maintained. A metric that cannot be folded from the event
 * stream does not exist.
 */
data class Metrics(
    val comparisons: Int = 0,
    val swaps: Int = 0,
    val passes: Int = 0,
    val pointerMoves: Int = 0,
    val eliminated: Int = 0,
    val steps: Int = 0,
    val wrongDecisions: Int = 0,
    val hintsUsed: Int = 0,
    val elapsedMillis: Long = 0,
) {
    companion object {
        val EMPTY = Metrics()
    }
}

/** Pure and total. */
object MetricsFolder {

    fun fold(previous: Metrics, events: List<VizEvent>, correct: Boolean): Metrics {
        var m = previous.copy(
            steps = previous.steps + 1,
            wrongDecisions = previous.wrongDecisions + if (correct) 0 else 1,
        )
        for (event in events) {
            m = when (event) {
                is VizEvent.Compare -> m.copy(comparisons = m.comparisons + 1)
                is VizEvent.Swap -> m.copy(swaps = m.swaps + 1)
                is VizEvent.MovePointer -> m.copy(pointerMoves = m.pointerMoves + 1)
                // A pass ends by locking one more value in place.
                is VizEvent.Finalize -> m.copy(passes = m.passes + 1)
                is VizEvent.Eliminate -> m.copy(
                    eliminated = m.eliminated + event.range.count(),
                )

                else -> m
            }
        }
        return m
    }

    fun withHint(metrics: Metrics): Metrics = metrics.copy(hintsUsed = metrics.hintsUsed + 1)
}
