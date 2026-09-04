package com.ttele.algoking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ttele.algoking.data.ProgressRepository
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.catalog.LessonPack
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.progress.LearningProgress
import com.ttele.algoking.engine.progress.Stage
import com.ttele.algoking.feature.complete.LessonCompleteScreen
import com.ttele.algoking.feature.lesson.LessonScreen
import com.ttele.algoking.feature.lesson.Phase
import com.ttele.algoking.feature.lesson.WatchScreen
import com.ttele.algoking.ui.screens.HomeScreen
import com.ttele.algoking.ui.theme.AlgoKingTheme
import kotlinx.coroutines.launch

/**
 * Navigation for the MVP.
 *
 * The learning spine is **WATCH → TRY → COMPLETE**, and it is the *same* spine for
 * every algorithm — the route holds an [AlgorithmId], and the screens read
 * everything else from that algorithm's `LessonPack`.
 *
 * CHALLENGE is deferred to V2 (`docs/v2-challenge.md`). There is deliberately no
 * placeholder route for it: a destination that exists but does nothing is worse
 * than one that does not exist, and the engine keeps the challenge machinery ready
 * behind `ChallengeCatalog` for when the stage is designed properly.
 */
private sealed interface Route {
    data object Home : Route
    data class Watch(val algorithm: AlgorithmId) : Route
    data class TryIt(val algorithm: AlgorithmId) : Route
    data class Complete(val algorithm: AlgorithmId) : Route
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AlgoKingTheme { AlgoKingApp() } }
    }
}

@Composable
private fun AlgoKingApp() {
    var route by remember { mutableStateOf<Route>(Route.Home) }

    // The one source of truth for how far each algorithm has been learned. It is
    // read as a flow, so a stage finished two screens deep reaches Home's rings
    // on the next frame — no restart, no manual refresh.
    val context = LocalContext.current
    val progressRepository = remember(context) { ProgressRepository(context) }
    val progress by progressRepository.progress.collectAsState(LearningProgress.EMPTY)
    val scope = rememberCoroutineScope()
    fun markComplete(id: AlgorithmId, stage: Stage) {
        scope.launch { progressRepository.complete(id, stage) }
    }

    // Bumped to start a stage over from scratch rather than resuming a finished run.
    var attempt by remember { mutableIntStateOf(0) }

    // What the finished lesson cost, for the completion screen.
    var lastRun by remember { mutableStateOf<Metrics?>(null) }

    when (val current = route) {
        Route.Home -> HomeScreen(
            progress = progress,
            onOpenAlgorithm = { entry ->
                attempt = 0
                // Resume where the learner actually left off. A finished algorithm
                // reopens at Watch, because re-reading is what practising it again
                // means once there is nothing left to unlock.
                route = when (progress[entry.id].nextStage) {
                    Stage.TRY -> Route.TryIt(entry.id)
                    else -> Route.Watch(entry.id)
                }
            },
        )

        is Route.Watch -> LessonFlow(current.algorithm) { pack ->
            // WATCH is a user-paced walkthrough, so it has its own screen — and no
            // play button anywhere on it.
            WatchScreen(
                pack = pack,
                onBack = { route = Route.Home },
                onStartTry = { route = Route.TryIt(current.algorithm) },
                onWatchComplete = { markComplete(current.algorithm, Stage.WATCH) },
            )
        }

        is Route.TryIt -> LessonFlow(current.algorithm) { pack ->
            LessonScreen(
                // `attempt` re-keys the controller so "try again" starts the stage
                // from scratch rather than resuming a finished run.
                key = attempt,
                phase = Phase.Try,
                pack = pack,
                dataset = pack.tryDataset,
                onBack = { route = Route.Home },
                // Reaching the terminal state is what counts. Wrong turns shape the
                // guidance the learner got; they never decide whether it was learned.
                onStageComplete = { markComplete(current.algorithm, Stage.TRY) },
                onFinishLesson = { controller ->
                    lastRun = controller.finalMetrics()
                    route = Route.Complete(current.algorithm)
                },
            )
        }

        is Route.Complete -> LessonFlow(current.algorithm) { pack ->
            LessonCompleteScreen(
                algorithmName = pack.displayName,
                algorithmId = pack.id,
                metrics = lastRun ?: Metrics.EMPTY,
                onHome = { route = Route.Home },
                onWatchAgain = {
                    attempt += 1
                    route = Route.Watch(current.algorithm)
                },
                onTryAgain = {
                    attempt += 1
                    route = Route.TryIt(current.algorithm)
                },
                onNextAlgorithm = {
                    attempt = 0
                    route = Route.Watch(nextAlgorithm(current.algorithm))
                },
            )
        }
    }
}

/**
 * The order the library teaches in.
 *
 * Searching first, then the three elementary sorts, then the two divide-and-conquer
 * sorts, then the structures — with Queue immediately after Stack so the contrast
 * lands while the first one is still fresh — and the Advanced shelf last, because a
 * technique reads as a technique only once the named routines are familiar.
 */
private fun nextAlgorithm(current: AlgorithmId): AlgorithmId = when (current) {
    AlgorithmId.BINARY_SEARCH -> AlgorithmId.BUBBLE_SORT
    AlgorithmId.BUBBLE_SORT -> AlgorithmId.SELECTION_SORT
    AlgorithmId.SELECTION_SORT -> AlgorithmId.INSERTION_SORT
    AlgorithmId.INSERTION_SORT -> AlgorithmId.MERGE_SORT
    AlgorithmId.MERGE_SORT -> AlgorithmId.QUICK_SORT
    AlgorithmId.QUICK_SORT -> AlgorithmId.STACK
    AlgorithmId.STACK -> AlgorithmId.QUEUE
    AlgorithmId.QUEUE -> AlgorithmId.LINKED_LIST
    AlgorithmId.LINKED_LIST -> AlgorithmId.HASH_MAP
    // The structures hand over to the Advanced shelf, which is where the library
    // stops teaching named routines and starts teaching techniques.
    AlgorithmId.HASH_MAP -> AlgorithmId.TWO_POINTERS
    AlgorithmId.TWO_POINTERS -> AlgorithmId.PREFIX_SUM
    AlgorithmId.PREFIX_SUM -> AlgorithmId.BINARY_SEARCH
}

/**
 * Resolves an [AlgorithmId] to its pack and hands it to [content] with its state
 * and action types intact.
 *
 * The star projection on `LessonPack<*, *>` is unavoidable at the routing layer —
 * a route can only carry an id — but it stops here: everything below is fully
 * typed, so no screen ever branches on which algorithm it is showing.
 */
@Composable
private fun LessonFlow(
    id: AlgorithmId,
    content: @Composable (LessonPack<Any, Action>) -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    val pack = remember(id) { AlgorithmCatalog.byId(id) as LessonPack<Any, Action> }
    content(pack)
}
