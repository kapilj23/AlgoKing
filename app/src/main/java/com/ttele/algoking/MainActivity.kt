package com.ttele.algoking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ttele.algoking.ads.AdDecision
import com.ttele.algoking.ads.AdPolicy
import com.ttele.algoking.ads.Placement
import com.ttele.algoking.analytics.Analytics
import com.ttele.algoking.analytics.MonetizationEvent
import com.ttele.algoking.analytics.NoopAnalytics
import com.ttele.algoking.billing.AccessDecision
import com.ttele.algoking.billing.ProAccess
import com.ttele.algoking.billing.PurchaseOutcome
import com.ttele.algoking.billing.SubscriptionRepository
import com.ttele.algoking.billing.PlayBillingGateway
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
import com.ttele.algoking.feature.paywall.PaywallScreen
import com.ttele.algoking.feature.settings.AboutScreen
import com.ttele.algoking.feature.settings.PrivacyPolicyScreen
import com.ttele.algoking.feature.settings.SettingsScreen
import com.ttele.algoking.feature.settings.openPlayStoreListing
import com.ttele.algoking.feature.settings.rememberVersionLabel
import com.ttele.algoking.ui.screens.HomeScreen
import com.ttele.algoking.ui.screens.algorithmLibrary
import com.ttele.algoking.ui.theme.AlgoKingTheme
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** Settings and the two pages it opens. Reached from Home's gear, and only there. */
    data object Settings : Route
    data object Privacy : Route
    data object About : Route

    /**
     * The paywall, carrying the lesson that opened it so the screen can say why it
     * appeared. Null when it was reached some other way.
     */
    data class Paywall(val algorithm: AlgorithmId?) : Route
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
    val versionLabel = rememberVersionLabel()

    val progress by progressRepository.progress.collectAsState(LearningProgress.EMPTY)
    val scope = rememberCoroutineScope()

    // What the learner owns, and what can be sold. Both come from Play and from
    // nowhere else — there is no path from a tap to an entitlement (ADR-041).
    val gateway = remember(context) { PlayBillingGateway(context, scope) }
    val subscriptions = remember(gateway) { SubscriptionRepository(gateway) }
    // The store connection belongs to this screen, and goes when it does.
    DisposableEffect(gateway) { onDispose { gateway.close() } }
    val entitlement by subscriptions.entitlement.collectAsState()
    val billing by subscriptions.billing.collectAsState()
    val lastOutcome by subscriptions.lastOutcome.collectAsState()
    var purchasing by remember { mutableStateOf(false) }

    // No analytics implementation exists; the events are emitted through the seam
    // ARCHITECTURE.md §10.4 specified and go nowhere until one does.
    val analytics: Analytics = remember { NoopAnalytics }

    fun markComplete(id: AlgorithmId, stage: Stage) {
        scope.launch { progressRepository.complete(id, stage) }
    }

    // Bumped to start a stage over from scratch rather than resuming a finished run.
    var attempt by remember { mutableIntStateOf(0) }

    // The ad layer. `interstitials` lives on the Application — one instance, one
    // initialisation — and the Activity is passed in at show time rather than
    // held, so nothing here can leak a window or show into a finishing one.
    val application = context.applicationContext as? AlgoKingApplication
    val interstitials = remember(application) { application?.interstitials }
    val activity = remember(context) { context.findActivity() }
    var completionId by rememberSaveable { mutableIntStateOf(0) }
    var lastAdCompletion by rememberSaveable { mutableStateOf<Int?>(null) }

    // A subscription bought mid-session stops ads immediately, including one
    // already in hand: an ad loaded before the purchase must not be shown after
    // it. `AdPolicy` refuses it anyway; this throws it away as well.
    LaunchedEffect(entitlement) {
        if (entitlement.isPro) interstitials?.discard() else interstitials?.resume()
    }

    // Consent, on **every launch** — a learner's region and the rules for it can
    // both change between sessions (docs/ads.md). UMP decides whether a form is
    // shown at all; a Pro subscriber is never asked, because they will never see
    // an ad to consent to. Ads start only once this says they may, and if it never
    // does, nothing here notices and every lesson works as it always did.
    val consent = remember(application) { application?.consent }
    val canRequestAds by (consent?.canRequestAds ?: remember { MutableStateFlow(false) })
        .collectAsState()
    val privacyOptionsRequired by
        (consent?.privacyOptionsRequired ?: remember { MutableStateFlow(false) })
            .collectAsState()

    LaunchedEffect(activity, entitlement) {
        val host = activity ?: return@LaunchedEffect
        consent?.gather(host, showFormIfRequired = !entitlement.isPro)
    }

    LaunchedEffect(canRequestAds, entitlement) {
        if (AdPolicy.mayRequestAds(entitlement, canRequestAds)) {
            application?.initializeAdsOnce()
        }
    }

    // What the finished lesson cost, for the completion screen.
    var lastRun by remember { mutableStateOf<Metrics?>(null) }

    when (val current = route) {
        Route.Home -> HomeScreen(
            progress = progress,
            onOpenAlgorithm = { entry ->
                attempt = 0
                // **The one access check in the app.** Free lessons and paid-for
                // lessons open; a locked one opens the paywall instead, and the
                // rule lives in `ProAccess` rather than in this screen (ADR-041).
                route = when (ProAccess.decide(entry.category, entitlement)) {
                    AccessDecision.OpenLesson ->
                        // Resume where the learner actually left off. A finished
                        // algorithm reopens at Watch, because re-reading is what
                        // practising it again means once there is nothing left to
                        // unlock.
                        when (progress[entry.id].nextStage) {
                            Stage.TRY -> Route.TryIt(entry.id)
                            else -> Route.Watch(entry.id)
                        }

                    AccessDecision.ShowPaywall -> {
                        analytics.log(
                            MonetizationEvent.PremiumAlgorithmTapped(entry.id.name),
                        )
                        Route.Paywall(entry.id)
                    }
                }
            },
            onOpenSettings = { route = Route.Settings },
        )

        is Route.Paywall -> {
            val name = current.algorithm?.let { id ->
                algorithmLibrary.firstOrNull { it.id == id }?.title
            }
            LaunchedEffect(current.algorithm) {
                analytics.log(MonetizationEvent.PaywallViewed(current.algorithm?.name))
            }
            PaywallScreen(
                billing = billing,
                triggeringAlgorithm = name,
                lastOutcome = lastOutcome,
                purchasing = purchasing,
                onBack = {
                    subscriptions.clearOutcome()
                    route = Route.Home
                },
                onUnlock = {
                    // The purchase is the repository's; this only says when to
                    // start one and what to do with the answer. Entitlement is
                    // never set here — it is re-read from the store.
                    analytics.log(MonetizationEvent.PurchaseStarted(current.algorithm?.name))
                    purchasing = true
                    scope.launch {
                        val outcome = subscriptions.purchase()
                        purchasing = false
                        analytics.log(
                            when (outcome) {
                                is PurchaseOutcome.Purchased ->
                                    MonetizationEvent.PurchaseSucceeded(current.algorithm?.name)

                                is PurchaseOutcome.Cancelled ->
                                    MonetizationEvent.PurchaseCancelled(current.algorithm?.name)

                                is PurchaseOutcome.Failed ->
                                    MonetizationEvent.PurchaseFailed(outcome.message)

                                is PurchaseOutcome.Unavailable ->
                                    MonetizationEvent.PurchaseFailed("unavailable")
                            },
                        )
                        // Only a store-verified entitlement opens the lesson, and
                        // it is re-read rather than assumed from the outcome.
                        val id = current.algorithm
                        if (subscriptions.entitlement.value.isPro && id != null) {
                            route = Route.Watch(id)
                        }
                    }
                },
                onRestore = {
                    analytics.log(MonetizationEvent.RestoreStarted)
                    scope.launch {
                        subscriptions.restore()
                        val restored = subscriptions.entitlement.value.isPro
                        analytics.log(MonetizationEvent.RestoreFinished(restored))
                    }
                },
                onRetry = { subscriptions.refresh() },
                onPrivacy = { route = Route.Privacy },
            )
        }

        Route.Settings -> SettingsScreen(
            versionLabel = versionLabel,
            // UMP decides whether this learner gets the row at all.
            privacyOptionsRequired = privacyOptionsRequired,
            onPrivacyOptions = {
                activity?.let { host -> consent?.showPrivacyOptions(host) }
            },
            onBack = { route = Route.Home },
            // The store listing is another app's job, so the intent is fired here
            // rather than inside the screen, which stays a function of its
            // arguments (ARCHITECTURE.md §2).
            onRate = { openPlayStoreListing(context) },
            onOpenPrivacy = { route = Route.Privacy },
            onOpenAbout = { route = Route.About },
        )

        Route.Privacy -> PrivacyPolicyScreen(onBack = { route = Route.Settings })

        Route.About -> AboutScreen(
            versionLabel = versionLabel,
            onBack = { route = Route.Settings },
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
                    // Each finished run gets its own id. It is what makes "one
                    // completion, at most one ad" true no matter how many times
                    // Compose recomposes or the screen is rotated.
                    completionId += 1
                    route = Route.Complete(current.algorithm)
                },
            )
        }

        is Route.Complete -> LessonFlow(current.algorithm) { pack ->
            // **The only ad in the app.** The learner has finished TRY and is
            // looking at how the run went; the lesson is over, so nothing is
            // interrupted. Whether it may actually show is `AdPolicy`'s decision,
            // and the answer is no for a Pro subscriber, no for a completion that
            // has already had its turn, and no when nothing is loaded — in which
            // case the learner simply carries on (docs/ads.md).
            LaunchedEffect(completionId, entitlement) {
                // A beat, so the metrics and the takeaway land before anything
                // covers them. Completion feedback first; the ad is the thing
                // that comes after.
                delay(AD_SETTLE_MS)
                val ads = interstitials
                val host = activity
                val decision = AdPolicy.decide(
                    placement = Placement.LESSON_COMPLETE,
                    entitlement = entitlement,
                    completionId = completionId,
                    lastShownForCompletion = lastAdCompletion,
                    adReady = ads?.isReady == true,
                )
                if (decision is AdDecision.Show && ads != null && host != null) {
                    // Recorded before the ad opens, so a recomposition while it is
                    // on screen cannot queue a second one.
                    lastAdCompletion = completionId
                    ads.show(host)
                }
            }

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
    // The comparison sorts hand over to the one that does not compare at all.
    AlgorithmId.QUICK_SORT -> AlgorithmId.COUNTING_SORT
    AlgorithmId.COUNTING_SORT -> AlgorithmId.STACK
    AlgorithmId.STACK -> AlgorithmId.QUEUE
    AlgorithmId.QUEUE -> AlgorithmId.LINKED_LIST
    AlgorithmId.LINKED_LIST -> AlgorithmId.HASH_MAP
    // The structures hand over to the Advanced shelf, which is where the library
    // stops teaching named routines and starts teaching techniques.
    AlgorithmId.HASH_MAP -> AlgorithmId.TWO_POINTERS
    AlgorithmId.TWO_POINTERS -> AlgorithmId.PREFIX_SUM
    AlgorithmId.PREFIX_SUM -> AlgorithmId.GRAPH_DFS
    // BFS immediately after DFS, so the contrast lands while DFS is still fresh —
    // the same reason Queue follows Stack.
    AlgorithmId.GRAPH_DFS -> AlgorithmId.GRAPH_BFS
    // The shelf ends on the Binary Search Tree, which closes the loop: it is
    // Binary Search's decision rule again, this time held by the structure
    // instead of recomputed from positions.
    // BFS hands over to Dijkstra, which is the same idea once edges cost
    // something — so the contrast lands while BFS is still fresh.
    AlgorithmId.GRAPH_BFS -> AlgorithmId.DIJKSTRA
    AlgorithmId.DIJKSTRA -> AlgorithmId.BINARY_SEARCH_TREE
    // The BST hands over to the tree that keeps itself short — which is the
    // answer to the caveat that lesson has to end on.
    AlgorithmId.BINARY_SEARCH_TREE -> AlgorithmId.AVL_TREE
    // The trees hand over to the three traversals of one.
    AlgorithmId.AVL_TREE -> AlgorithmId.TREE_INORDER
    // The three traversals run consecutively, so the contrast lands while the
    // previous order is still fresh — the same reason Queue follows Stack.
    AlgorithmId.TREE_INORDER -> AlgorithmId.TREE_PREORDER
    AlgorithmId.TREE_PREORDER -> AlgorithmId.TREE_POSTORDER
    AlgorithmId.TREE_POSTORDER -> AlgorithmId.BINARY_SEARCH
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

/**
 * The Activity behind a Compose `LocalContext`, or null.
 *
 * An interstitial needs a real window to show into. Walking the wrapper chain is
 * the supported way to find one, and returning null rather than casting means a
 * context that is not an Activity is a "no ad" — never a crash.
 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * How long the Complete screen has to itself before an ad may cover it.
 *
 * The rule is that completion feedback comes first (`docs/ads.md`): the learner
 * finished the lesson, and what they earned is the point of the screen. Long
 * enough to read the metric row and register the takeaway; short enough that the
 * ad still reads as part of the same beat rather than as an ambush later on.
 */
private const val AD_SETTLE_MS = 1_200L
