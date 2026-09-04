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
import com.ttele.algoking.engine.challenge.ChallengeRun
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.progress.LearningProgress
import com.ttele.algoking.engine.progress.Stage
import com.ttele.algoking.engine.scoring.ScoreInput
import com.ttele.algoking.engine.scoring.ScoreResult
import com.ttele.algoking.engine.scoring.Scorer
import com.ttele.algoking.feature.lesson.ChallengeIntroScreen
import com.ttele.algoking.feature.lesson.LessonScreen
import com.ttele.algoking.feature.lesson.MissionIntroScreen
import com.ttele.algoking.feature.mission.MissionChallengeScreen
import com.ttele.algoking.engine.challenge.toChallengeRun
import com.ttele.algoking.feature.lesson.Phase
import com.ttele.algoking.feature.lesson.WatchScreen
import com.ttele.algoking.feature.result.ResultScreen
import com.ttele.algoking.ui.screens.HomeScreen
import com.ttele.algoking.ui.theme.AlgoKingTheme
import kotlinx.coroutines.launch

/**
 * Navigation for the vertical slice.
 *
 * The learning spine is WATCH → TRY → CHALLENGE → RESULT, and it is the *same*
 * spine for every algorithm — the route holds an [AlgorithmId], and the screens
 * read everything else from that algorithm's `LessonPack`. There is no Master
 * destination: mastery is a status printed on [Route.Result].
 */
private sealed interface Route {
    data object Home : Route
    data class Watch(val algorithm: AlgorithmId) : Route
    data class TryIt(val algorithm: AlgorithmId) : Route
    data class ChallengeIntro(val algorithm: AlgorithmId) : Route
    data class Challenge(val algorithm: AlgorithmId) : Route
    data class Result(val algorithm: AlgorithmId) : Route
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
    // read as a flow, so a stage finished three screens deep reaches Home's rings
    // on the next frame — no restart, no manual refresh.
    val context = LocalContext.current
    val progressRepository = remember(context) { ProgressRepository(context) }
    val progress by progressRepository.progress.collectAsState(LearningProgress.EMPTY)
    val scope = rememberCoroutineScope()
    fun markComplete(id: AlgorithmId, stage: Stage) {
        scope.launch { progressRepository.complete(id, stage) }
    }

    // Round drives difficulty; seed drives the data. Bumping the seed alone gives a
    // *new* problem at the same difficulty ("practice again"); keeping both replays
    // the identical one ("try again").
    var round by remember { mutableIntStateOf(1) }
    var seed by remember { mutableIntStateOf(1) }
    var attempt by remember { mutableIntStateOf(0) }

    var lastRun by remember { mutableStateOf<ChallengeRun?>(null) }
    // How the search space fell, round by round — the efficiency story the
    // result screen tells.
    var missionTrail by remember { mutableStateOf<List<Int>>(emptyList()) }
    var score by remember { mutableStateOf<ScoreResult?>(null) }

    when (val current = route) {
        Route.Home -> HomeScreen(
            progress = progress,
            onOpenAlgorithm = { entry ->
                round = 1
                seed = 1
                attempt = 0
                // Resume where the learner actually left off. A finished algorithm
                // reopens at Watch, because re-reading is what "practice again"
                // means before a fresh challenge.
                route = when (progress[entry.id].nextStage) {
                    Stage.TRY -> Route.TryIt(entry.id)
                    Stage.CHALLENGE -> Route.ChallengeIntro(entry.id)
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
                phase = Phase.Try,
                pack = pack,
                dataset = pack.tryDataset,
                onBack = { route = Route.Home },
                onAdvance = { route = Route.ChallengeIntro(current.algorithm) },
                onStageComplete = { markComplete(current.algorithm, Stage.TRY) },
            )
        }

        // The briefing. A lesson with a mission gets the situation; one without
        // gets the bare brief. Neither explains the algorithm.
        is Route.ChallengeIntro -> LessonFlow(current.algorithm) { pack ->
            val challenge = remember(pack.id, round, seed) {
                pack.challenge(round, seed.toLong())
            }
            val mission = challenge.mission
            if (mission != null) {
                MissionIntroScreen(
                    mission = mission,
                    algorithmName = pack.displayName,
                    onBack = { route = Route.TryIt(current.algorithm) },
                    onStart = { route = Route.Challenge(current.algorithm) },
                )
            } else {
                ChallengeIntroScreen(
                    challenge = challenge,
                    algorithmName = pack.displayName,
                    brief = pack.challengeBrief,
                    onBack = { route = Route.TryIt(current.algorithm) },
                    onStart = { route = Route.Challenge(current.algorithm) },
                )
            }
        }

        is Route.Challenge -> LessonFlow(current.algorithm) { pack ->
            val challenge = remember(pack.id, round, seed) { pack.challenge(round, seed.toLong()) }
            val mission = challenge.mission
            if (mission != null) {
                // A mission runs its own three-gate loop. It still reports the
                // same ChallengeRun, so scoring, the result screen and progress
                // need no branch of their own.
                MissionChallengeScreen(
                    mission = mission,
                    algorithmName = pack.displayName,
                    attempt = attempt,
                    onBack = { route = Route.ChallengeIntro(current.algorithm) },
                    onStageComplete = { markComplete(current.algorithm, Stage.CHALLENGE) },
                    onComplete = { run ->
                        val summary = run.toChallengeRun(challenge)
                        lastRun = summary
                        missionTrail = run.trail
                        score = Scorer.score(
                            ScoreInput(
                                family = pack.starFamily,
                                metrics = summary.toMetrics(),
                                optimalComparisons = challenge.optimalComparisons,
                                completed = true,
                            ),
                        )
                        route = Route.Result(current.algorithm)
                    },
                )
                return@LessonFlow
            }
            LessonScreen(
                // `attempt` re-keys the controller so "try again" starts the same
                // problem from scratch rather than resuming a finished run.
                key = attempt,
                phase = Phase.Challenge,
                pack = pack,
                dataset = challenge.dataset,
                challenge = challenge,
                onBack = { route = Route.ChallengeIntro(current.algorithm) },
                // Solving it is what counts. Mistakes and hints shape the stars on
                // the result screen; they never decide whether it was learned.
                onStageComplete = { markComplete(current.algorithm, Stage.CHALLENGE) },
                onFinishChallenge = { controller ->
                    val run = controller.runSummary() ?: return@LessonScreen
                    lastRun = run
                    score = Scorer.score(
                        ScoreInput(
                            family = pack.starFamily,
                            metrics = run.toMetrics(),
                            optimalComparisons = challenge.optimalComparisons,
                            completed = true,
                        ),
                    )
                    route = Route.Result(current.algorithm)
                },
            )
        }

        is Route.Result -> {
            val run = lastRun
            val result = score
            if (run == null || result == null) {
                route = Route.Home
            } else {
                LessonFlow(current.algorithm) { pack ->
                    ResultScreen(
                        algorithmName = pack.displayName,
                        algorithmId = pack.id,
                        run = run,
                        score = result,
                        onBack = { route = Route.Home },
                        onTryAgain = {
                            // Same problem, fresh run.
                            attempt += 1
                            route = Route.Challenge(current.algorithm)
                        },
                        onPracticeAgain = {
                            // A genuinely new problem, one difficulty step along.
                            round += 1
                            seed += 1
                            attempt += 1
                            route = Route.ChallengeIntro(current.algorithm)
                        },
                        onNextAlgorithm = {
                            val next = when (current.algorithm) {
                                AlgorithmId.BINARY_SEARCH -> AlgorithmId.BUBBLE_SORT
                                AlgorithmId.BUBBLE_SORT -> AlgorithmId.SELECTION_SORT
                                AlgorithmId.SELECTION_SORT -> AlgorithmId.INSERTION_SORT
                                AlgorithmId.INSERTION_SORT -> AlgorithmId.MERGE_SORT
                                AlgorithmId.MERGE_SORT -> AlgorithmId.QUICK_SORT
                                // Sorting hands over to the structures, and the
                                // Queue follows the Stack so the contrast lands.
                                AlgorithmId.QUICK_SORT -> AlgorithmId.STACK
                                AlgorithmId.STACK -> AlgorithmId.QUEUE
                                AlgorithmId.QUEUE -> AlgorithmId.LINKED_LIST
                                AlgorithmId.LINKED_LIST -> AlgorithmId.HASH_MAP
                                AlgorithmId.HASH_MAP -> AlgorithmId.BINARY_SEARCH
                            }
                            round = 1
                            seed = 1
                            attempt = 0
                            route = Route.Watch(next)
                        },
                    )
                }
            }
        }
    }
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
