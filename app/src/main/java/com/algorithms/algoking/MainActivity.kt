package com.algorithms.algoking

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
import com.algorithms.algoking.ads.AdDecision
import com.algorithms.algoking.ads.AdPolicy
import com.algorithms.algoking.ads.Placement
import com.algorithms.algoking.analytics.Analytics
import com.algorithms.algoking.analytics.MonetizationEvent
import com.algorithms.algoking.analytics.NoopAnalytics
import com.algorithms.algoking.billing.AccessDecision
import com.algorithms.algoking.billing.ProAccess
import com.algorithms.algoking.billing.PurchaseOutcome
import com.algorithms.algoking.billing.SubscriptionRepository
import com.algorithms.algoking.billing.PlayBillingGateway
import com.algorithms.algoking.data.ProgressRepository
import com.algorithms.algoking.data.ReviewStore
import com.algorithms.algoking.devtools.DebugEntitlement
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.catalog.LessonPack
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.decision.Action
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.progress.LearningProgress
import com.algorithms.algoking.engine.progress.Stage
import com.algorithms.algoking.feature.complete.LessonCompleteScreen
import com.algorithms.algoking.feature.lesson.LessonScreen
import com.algorithms.algoking.feature.lesson.Phase
import com.algorithms.algoking.feature.lesson.WatchScreen
import com.algorithms.algoking.feature.paywall.PaywallScreen
import com.algorithms.algoking.feature.paywall.ProUnlockedDialog
import com.algorithms.algoking.feature.settings.AboutScreen
import com.algorithms.algoking.feature.settings.PrivacyPolicyScreen
import com.algorithms.algoking.feature.settings.SettingsScreen
import com.algorithms.algoking.feature.settings.openPlayStoreListing
import com.algorithms.algoking.feature.settings.rememberVersionLabel
import com.algorithms.algoking.review.InAppReviewManager
import com.algorithms.algoking.review.ReviewDecision
import com.algorithms.algoking.review.ReviewPolicy
import com.algorithms.algoking.review.ReviewTrigger
import com.algorithms.algoking.ui.screens.HomeScreen
import com.algorithms.algoking.ui.screens.algorithmLibrary
import com.algorithms.algoking.ui.theme.AlgoKingTheme
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
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

    // Asking for a review: Play's own flow, and the one flag that says it has been
    // asked. Both are constructed here for the reason `progressRepository` is —
    // they hold the application context and outlive no more than this composition.
    val reviewStore = remember(context) { ReviewStore(context) }
    val reviews = remember(context) { InAppReviewManager(context) }

    val progress by progressRepository.progress.collectAsState(LearningProgress.EMPTY)
    val scope = rememberCoroutineScope()

    // What the learner owns, and what can be sold. Both come from Play and from
    // nowhere else — there is no path from a tap to an entitlement (ADR-041).
    val gateway = remember(context) { PlayBillingGateway(context, scope) }
    val subscriptions = remember(gateway) { SubscriptionRepository(gateway) }
    // The store connection belongs to this screen, and goes when it does.
    DisposableEffect(gateway) { onDispose { gateway.close() } }
    // What the store says, and then what this build is allowed to make of it.
    //
    // In release `DebugEntitlement.override` is the identity function and the
    // overriding implementation is not in the binary at all — it lives in
    // `src/debug/`, so Pro still comes only from a verified Play purchase
    // (ADR-041, ADR-058). In a debug build it may substitute FREE or PRO, from
    // `local.properties`, so both paths can actually be walked on a device.
    val storeEntitlement by subscriptions.entitlement.collectAsState()
    val entitlement = DebugEntitlement.override(storeEntitlement)
    val billing by subscriptions.billing.collectAsState()
    val lastOutcome by subscriptions.lastOutcome.collectAsState()
    var purchasing by remember { mutableStateOf(false) }

    // **The receipt, and it is driven by an event rather than by a state.**
    //
    // `entitlement` says whether the learner is Pro, and it says it for every
    // reason they can be — a purchase, a restore, a reinstall, the startup query,
    // a pending payment clearing, a `BillingClient` reconnect. Congratulating them
    // is true of exactly one of those, so the trigger is
    // `subscriptions.proUnlocked`, which `SubscriptionRepository` emits only from a
    // purchase it just completed and which is **consumed on delivery**.
    //
    // `remember` rather than `rememberSaveable`, deliberately. A saved `true` would
    // come back after process death and congratulate a learner on a purchase they
    // made last week, which is the one failure worth ruling out here; the cost is
    // that a rotation while the dialog is open closes it, and a dialog dismissed a
    // moment early is a smaller wrong than one that reappears on a cold start.
    var proJustUnlocked by remember { mutableStateOf(false) }
    LaunchedEffect(subscriptions) {
        subscriptions.proUnlocked.collect { proJustUnlocked = true }
    }

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

    // The completion the review flow was last attempted for. `rememberSaveable`, so
    // a rotation cannot hand the same completion a second attempt — the mechanism
    // `lastAdCompletion` uses above, for the same reason.
    var lastReviewCompletion by rememberSaveable { mutableStateOf<Int?>(null) }

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
                route = when (ProAccess.decide(entry.category, entry.id, entitlement)) {
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

            // **The paywall closes the moment the store says Pro is owned**, by
            // whatever route that happened: a purchase just completed, a restore
            // found one, a pending payment cleared, or the query that runs at
            // startup simply arrived after the learner had already tapped a locked
            // lesson. Entitlement is the trigger rather than the purchase flow's
            // own answer, so there is still exactly one thing that opens a paid
            // lesson — what the store owns (ADR-041).
            LaunchedEffect(entitlement) {
                if (!entitlement.isPro) return@LaunchedEffect
                subscriptions.clearOutcome()
                route = current.algorithm?.let(Route::Watch) ?: Route.Home
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

                                is PurchaseOutcome.Pending ->
                                    MonetizationEvent.PurchasePending(current.algorithm?.name)

                                is PurchaseOutcome.Failed ->
                                    MonetizationEvent.PurchaseFailed(outcome.message)

                                is PurchaseOutcome.Unavailable ->
                                    MonetizationEvent.PurchaseFailed("unavailable")
                            },
                        )
                        // Nothing is navigated from here. Only a store-verified
                        // entitlement opens a paid lesson, and the effect above
                        // does that the instant one arrives — from this purchase
                        // or from anywhere else.
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
            // **What happens after a lesson, in order, in one place.**
            //
            // Two things may follow a finished run — the app's only ad, and its
            // only review prompt — and they must never overlap. So this is one
            // sequential effect rather than two timers that would have to be kept
            // from colliding by guessing at each other's durations:
            //
            //     settle -> [ad, if any] -> wait for it to be gone -> settle
            //            -> [review, if eligible]
            //
            // A Pro learner has no ad step, so their sequence is simply the two
            // settles and the ask. A free learner gets the review *after* the ad is
            // fully dismissed — the ad delays the ask, it does not cancel it.
            LaunchedEffect(completionId, entitlement) {
                // A beat, so the metrics and the takeaway land before anything
                // covers them. Completion feedback first; everything else comes
                // after (docs/ads.md).
                delay(AD_SETTLE_MS)

                // **The only ad in the app.** Whether it may show is `AdPolicy`'s
                // decision: no for a Pro subscriber, no for a completion that has
                // already had its turn, and no when nothing is loaded — in which
                // case the learner simply carries on.
                val ads = interstitials
                val host = activity
                val decision = AdPolicy.decide(
                    placement = Placement.LESSON_COMPLETE,
                    // **Read from the repository, not from the composition.** This
                    // is the last moment before an ad could be presented, and a
                    // purchase that completed during the settle above must count.
                    // `collectAsState` is a snapshot that recomposition has to
                    // catch up to; `entitlement.value` is the store's own answer as
                    // it stands right now — the single source of truth.
                    //
                    // Through the same debug seam as above, so that a debug build
                    // told to be FREE can actually reach an ad. Without it this one
                    // read would bypass the override and see `Unknown`, which since
                    // ADR-057 suppresses — making the free path untestable on a
                    // sideloaded build. In release the call is the identity.
                    entitlement = DebugEntitlement.override(subscriptions.entitlement.value),
                    completionId = completionId,
                    lastShownForCompletion = lastAdCompletion,
                    adReady = ads?.isReady == true,
                )
                if (decision is AdDecision.Show && ads != null && host != null) {
                    // Recorded before the ad opens, so a recomposition while it is
                    // on screen cannot queue a second one.
                    lastAdCompletion = completionId
                    // **Waits for the ad to be completely gone.** `show` calls back
                    // exactly once however it goes — dismissed, failed to present,
                    // or nothing to show — so this resumes on every path and the
                    // review below can never be drawn over an ad. If a callback
                    // somehow never arrives the effect simply stays suspended, the
                    // ask is not spent, and the next finished lesson gets it.
                    suspendCancellableCoroutine { continuation ->
                        ads.show(host) {
                            if (continuation.isActive) continuation.resume(Unit) {}
                        }
                    }
                }

                // **The one place the app ever asks for a review**, and the only
                // trigger there is: a lesson the learner actually finished.
                //
                // A second beat, now that the screen is the learner's again. It
                // separates the ask from whatever just closed, and it means a
                // learner who taps straight on to the next lesson is gone before it
                // fires — the ask reaches someone who stayed to read how the run
                // went, which is when it is most honestly earned.
                delay(REVIEW_SETTLE_MS)
                val reviewHost = activity ?: return@LaunchedEffect

                val reviewDecision = ReviewPolicy.decide(
                    trigger = ReviewTrigger.LESSON_COMPLETE,
                    // From disk, authoritative, and the reason this is asked once.
                    // Read here rather than collected into composition: a flow
                    // collected into state starts at its default, and a `false`
                    // read a beat before the real value arrived is exactly how a
                    // learner gets asked twice.
                    alreadyAsked = reviewStore.asked.first(),
                    // WATCH *and* TRY. Read from the repository rather than from the
                    // collected snapshot, which may predate the stage that was just
                    // finished three lines of navigation ago.
                    lessonComplete =
                        progressRepository.progress.first()[current.algorithm].percent == 100,
                    triedForCompletion = lastReviewCompletion == completionId,
                )
                if (reviewDecision !is ReviewDecision.Ask) return@LaunchedEffect

                // Recorded before the attempt, so this completion cannot get a
                // second one — the rule `lastAdCompletion` follows for the ad.
                lastReviewCompletion = completionId

                // Play decides whether anything is drawn, and never reports what
                // the learner did. The flag is set only if the flow was genuinely
                // handed over, so a failure leaves the ask unspent for next time.
                if (reviews.launch(reviewHost)) reviewStore.markAsked()
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

    // **Outside the `when`, on purpose.** By the time a purchase completes, the
    // effect on the paywall has already closed it into the lesson the learner
    // tapped — so the dialog belongs to the app rather than to a screen, and it
    // draws over whatever they landed on with the lessons already unlocked behind
    // it. Putting it inside the paywall branch would mean either holding that
    // screen open to show it, or showing it and then navigating out from under it.
    //
    // Dismissing only clears the local flag. It cannot reopen the paywall, because
    // nothing here touches `route`.
    if (proJustUnlocked) {
        ProUnlockedDialog(onStartLearning = { proJustUnlocked = false })
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
    // The structures hand over to the two free lessons that are not about finding
    // or ordering anything — short, self-contained ideas before the Advanced shelf.
    AlgorithmId.HASH_MAP -> AlgorithmId.CAESAR_CIPHER
    // Caesar first: it hides a message with arithmetic a learner can do in their
    // head. XOR then does the same job with one bitwise operation, and adds the
    // thing Caesar has no equivalent of — the key that undoes itself.
    AlgorithmId.CAESAR_CIPHER -> AlgorithmId.XOR_CIPHER
    // The two ciphers hand over to the lesson that is not one. Both of them turn a
    // message into something else and then turn it back; SHA-256 does not go back,
    // and that difference only reads as a difference once the learner has watched
    // the other two do it (ADR-048).
    AlgorithmId.XOR_CIPHER -> AlgorithmId.SHA_256
    // The shelf ends on the cipher a real system would actually use. AES comes
    // last of the four because it is the payoff: two hand-run ciphers and a hash
    // are what make "four steps, ten times over" read as a technique rather than
    // as a wall of names (ADR-049). It is also the only one of the four that is
    // Pro, so a free learner meets the paywall having just finished SHA-256.
    AlgorithmId.SHA_256 -> AlgorithmId.AES
    // AES hands over to the lesson that answers the question it cannot: every
    // cipher up to here shares one key between both sides, and RSA is where two
    // strangers get one without meeting (ADR-050).
    AlgorithmId.AES -> AlgorithmId.RSA
    // ...and then to the Advanced shelf, which is where the library stops teaching
    // named routines and starts teaching techniques.
    AlgorithmId.RSA -> AlgorithmId.TWO_POINTERS
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
    // The shelf ends on building answers rather than walking structures: a DP
    // lesson reads best once Prefix Sum's "build a table once" is behind the learner.
    //
    // Fibonacci first of the two, because it is the one that argues DP is worth
    // having — one rule, one row, and a naive recursion whose cost is watched
    // rather than asserted. Knapsack then spends that argument on a real choice.
    AlgorithmId.TREE_POSTORDER -> AlgorithmId.FIBONACCI
    AlgorithmId.FIBONACCI -> AlgorithmId.ZERO_ONE_KNAPSACK
    AlgorithmId.ZERO_ONE_KNAPSACK -> AlgorithmId.BINARY_SEARCH
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

/**
 * How long the app waits before asking for a review, measured from the point the
 * completion screen is the learner's own again.
 *
 * For a Pro learner that is right after [AD_SETTLE_MS], because nothing else
 * happens. For a free learner who was shown the one interstitial it starts when
 * that ad is **completely dismissed** — the wait is sequential rather than a second
 * timer racing the first, so the review can never be drawn over an ad and an ad can
 * only ever *delay* the ask.
 *
 * It also quietly selects who gets asked. A learner who taps straight on to the
 * next lesson is gone before this fires and is never interrupted; the ask reaches
 * someone who stayed to read how the run went, which is the moment it is most
 * honestly earned.
 */
private const val REVIEW_SETTLE_MS = 1_800L
