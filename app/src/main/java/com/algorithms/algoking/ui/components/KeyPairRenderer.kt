package com.algorithms.algoking.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.ChoiceCard
import com.algorithms.algoking.engine.scene.DerivationStep
import com.algorithms.algoking.engine.scene.KeyCard
import com.algorithms.algoking.engine.scene.KeyPairScene
import com.algorithms.algoking.engine.scene.RoundTripStage
import com.algorithms.algoking.engine.scene.RoundTripView
import com.algorithms.algoking.ui.icons.AlgoIcons
import com.algorithms.algoking.ui.theme.AlgoColors
import com.algorithms.algoking.ui.theme.AlgoType
import com.algorithms.algoking.ui.theme.AlgoViz
import com.algorithms.algoking.ui.theme.Dimens
import com.algorithms.algoking.ui.theme.Radius
import com.algorithms.algoking.ui.theme.Spacing

/**
 * A derivation chain, the key pair it produces, and the round trip through them —
 * DESIGN_SYSTEM.md §6.16n.
 *
 * ### The shape of the screen
 *
 * ```
 * ┌ KEY GENERATION ─────────────────────────┐
 * │  p, q    two different primes    5, 11  │
 * │    ↓                                    │
 * │  n       p × q                     55   │   every value stays on screen,
 * │    ↓                                    │   because the next one reads it
 * │  φ(n)    (p − 1)(q − 1)            40   │
 * │    ↓                                    │
 * │  e       gcd(e, φ(n)) = 1           ?   │   ← being asked for
 * └─────────────────────────────────────────┘
 *
 * ┌ PUBLIC KEY ────────┐  ┌ PRIVATE KEY ───┐
 * │ (3, 55)            │  │ (27, 55)    🔒 │
 * │ Share it freely    │  │ Never share it │
 * └────────────────────┘  └────────────────┘
 * ```
 *
 * ### A `?` is the most important thing it draws
 *
 * A value the lesson has not produced is **absent**, never zero and never guessed,
 * so the picture can never answer the question being asked (ADR-030's rule, applied
 * to a dependency chain). The arrows between rows are what makes it a chain rather
 * than a list — each one points at the value it feeds.
 *
 * ### Nothing scrolls sideways
 *
 * Every row is `symbol · formula · value` across one line, and the formula is the
 * part that gives way — it wraps, and the symbol and value never shrink. The
 * numbers are two digits by construction (`RsaProblem.TOY_CEILING`), which is the
 * point of the lesson rather than a layout convenience.
 *
 * ### The bright language, not a dark one
 *
 * Same cards, same 20dp radii, same violet, same `surfaceVariant` grounds as every
 * other lesson. The private key carries a small lock glyph and nothing else — a
 * key that must be kept is a normal thing, not an alarm.
 */
@Composable
fun KeyPairStage(
    scene: KeyPairScene,
    modifier: Modifier = Modifier,
    /** Cards the learner may tap. Empty means the cards are read-only. */
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {

        if (scene.chain.isNotEmpty()) {
            DerivationCard(scene.chain)
        }

        if (scene.keys.isNotEmpty()) {
            Gap(Spacing.sm)
            KeyRow(scene.keys)
        }

        scene.roundTrip?.let {
            Gap(Spacing.sm)
            RoundTripCard(it)
        }

        if (scene.choices.isNotEmpty()) {
            Gap(Spacing.md)
            ChoiceList(scene.choices, selectableSlots, onSelectSlot)
        }

        scene.caveat?.let {
            Gap(Spacing.sm)
            Caveat(text = it, prominent = scene.caveatProminent)
        }
    }
}

/**
 * The chain, with an arrow between each row and the next.
 *
 * Every value that has been derived **stays** — later rows read it, and a learner
 * who cannot see where `d` came from has not learned the thing this lesson is for.
 */
@Composable
private fun DerivationCard(chain: List<DerivationStep>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(Spacing.sm),
    ) {
        Text(
            text = "KEY GENERATION",
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
            maxLines = 1,
        )
        Gap(Spacing.xs)
        chain.forEachIndexed { index, link ->
            if (index > 0) {
                Text(
                    text = "↓",
                    style = AlgoType.labelSmall,
                    color = AlgoColors.disabled,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            DerivationRow(link)
        }
    }
}

@Composable
private fun DerivationRow(link: DerivationStep) {
    val lit = link.state == CellState.COMPARING
    val fresh = link.state == CellState.CANDIDATE

    val fill by animateColorAsState(
        targetValue = when {
            lit -> AlgoColors.primarySoft
            fresh -> AlgoColors.secondarySoft
            link.known -> AlgoColors.surface
            else -> AlgoColors.surfaceMuted
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "rsaRowFill",
    )
    val outline = when {
        lit -> AlgoViz.comparing
        fresh -> AlgoViz.next
        else -> AlgoColors.border
    }
    val ink = when {
        lit -> AlgoColors.primary
        fresh -> AlgoColors.secondaryDark
        link.known -> AlgoColors.textPrimary
        else -> AlgoColors.textMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(fill, Radius.cell)
            .border(Dimens.hairline, outline, Radius.cell)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = link.symbol,
                style = AlgoType.titleSmall,
                color = ink,
                maxLines = 1,
                modifier = Modifier.width(Dimens.derivationSymbolWidth),
            )
            Text(
                text = link.formula,
                style = AlgoType.bodyMedium,
                color = AlgoColors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Gap(Spacing.xs)
            Text(
                // A value the lesson has not produced reads `?` — never a zero, and
                // never the answer to the question being asked.
                text = link.valueLabel ?: link.value?.toString() ?: "?",
                style = AlgoType.numeralMedium,
                color = if (link.known) ink else AlgoColors.textMuted,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
        link.note?.let {
            Text(
                text = it,
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
            )
        }
    }
}

/**
 * The two halves, side by side.
 *
 * Together, always — they share a modulus and differ in one number and one rule,
 * and a learner who meets them apart can read them as two unrelated keys.
 */
@Composable
private fun KeyRow(keys: List<KeyCard>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        keys.forEach { key ->
            KeyCardView(key, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeyCardView(key: KeyCard, modifier: Modifier = Modifier) {
    val fresh = key.state == CellState.CANDIDATE
    // The secret half is tinted with the ornament gold the app already uses for a
    // Pro crown and a streak — a key that must be kept is a normal thing, and an
    // alarm colour here would read as an error rather than as a rule.
    val accent = if (key.secret) AlgoColors.gold else AlgoColors.primary
    val ground = if (key.secret) AlgoColors.goldSoft else AlgoColors.primarySoft

    Column(
        modifier = modifier
            .background(ground, Radius.card)
            .border(
                if (fresh) Dimens.outline else Dimens.hairline,
                if (fresh) AlgoViz.next else AlgoColors.border,
                Radius.card,
            )
            .padding(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = key.label.uppercase(),
                style = AlgoType.labelSmall,
                color = accent,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (key.secret) {
                Gap(Spacing.xxs)
                AlgoIcon(AlgoIcons.Lock, accent, 14.dp)
            }
        }
        Gap(Spacing.xxs)
        Text(
            text = key.printed,
            style = AlgoType.numeralMedium,
            color = AlgoColors.textPrimary,
            maxLines = 1,
        )
        Gap(Spacing.xxs)
        Text(
            text = key.rule,
            style = AlgoType.labelSmall,
            color = AlgoColors.textSecondary,
        )
        Text(
            text = key.use,
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
        )
    }
}

/**
 * The message out and back: `4 → 9 → 4`.
 *
 * Drawn as one card rather than two, because the thing being shown is that the
 * number at the end is the number at the start. A value that has not been computed
 * reads `?`.
 */
@Composable
private fun RoundTripCard(trip: RoundTripView) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(Spacing.sm),
    ) {
        Text(
            text = "MESSAGE",
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
            maxLines = 1,
        )
        Gap(Spacing.xs)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            TripValue(trip.message.toString(), settled = true)
            TripArrow("encrypt")
            TripValue(
                trip.ciphertext?.toString() ?: "?",
                settled = trip.ciphertext != null,
                accent = trip.stage != RoundTripStage.READY,
            )
            TripArrow("decrypt")
            TripValue(
                trip.recovered?.toString() ?: "?",
                settled = trip.recovered != null,
                // The payoff: the number at the end matching the one at the start.
                matched = trip.recovered == trip.message,
            )
        }

        Gap(Spacing.xs)
        Text(
            text = if (trip.stage == RoundTripStage.READY) {
                trip.encryptFormula
            } else {
                trip.decryptFormula
            },
            style = AlgoType.bodyMedium,
            color = AlgoColors.textSecondary,
        )
    }
}


@Composable
private fun TripValue(
    text: String,
    settled: Boolean,
    accent: Boolean = false,
    matched: Boolean = false,
) {
    val ground = when {
        matched -> AlgoColors.successSoft
        accent && settled -> AlgoColors.primarySoft
        settled -> AlgoColors.surface
        else -> AlgoColors.surfaceMuted
    }
    val ink = when {
        matched -> AlgoColors.onSuccessSoft
        settled -> AlgoColors.textPrimary
        else -> AlgoColors.textMuted
    }
    Box(
        modifier = Modifier
            .background(ground, Radius.cell)
            .border(Dimens.hairline, AlgoColors.border, Radius.cell)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AlgoType.numeralMedium, color = ink, maxLines = 1)
    }
}

@Composable
private fun TripArrow(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("→", style = AlgoType.titleSmall, color = AlgoColors.primary)
        Text(label, style = AlgoType.labelSmall, color = AlgoColors.textMuted, maxLines = 1)
    }
}

/**
 * Full-width cards the learner taps.
 *
 * A `DecisionButton` is one line at `labelLarge` and four share a row, which leaves
 * about 76dp each — enough for `(3, 55)` and nothing like enough for "Asymmetric
 * cryptography". Stacking them gives each one the width it needs and the 48dp touch
 * minimum with room to spare, which is the same move AES made with its round strip
 * (ADR-049) and ADR-034 made when it refused a row of neighbour buttons.
 *
 * **No card is styled as the correct one.** The scene does not carry which is true,
 * so the renderer could not do it even by accident (PRODUCT_SPEC.md §5).
 */
@Composable
private fun ChoiceList(
    choices: List<ChoiceCard>,
    selectableSlots: Set<Int>,
    onSelectSlot: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        choices.forEach { choice ->
            ChoiceCardView(
                choice = choice,
                selectable = choice.slot in selectableSlots,
                onSelect = { onSelectSlot(choice.slot) },
            )
        }
    }
}

@Composable
private fun ChoiceCardView(
    choice: ChoiceCard,
    selectable: Boolean,
    onSelect: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surface, Radius.card)
            .border(
                if (selectable) Dimens.outline else Dimens.hairline,
                if (selectable) AlgoViz.pointer else AlgoColors.border,
                Radius.card,
            )
            .then(
                if (selectable) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onSelect,
                    )
                } else {
                    Modifier
                },
            )
            .heightIn(min = Dimens.minTouchTarget)
            .padding(Spacing.sm),
    ) {
        Text(
            text = choice.title,
            style = AlgoType.titleSmall,
            color = AlgoColors.textPrimary,
        )
        Gap(Spacing.xxs)
        Text(
            text = choice.detail,
            style = AlgoType.bodyMedium,
            color = AlgoColors.textSecondary,
        )
    }
}

/**
 * The standing reminder that these numbers are a demonstration.
 *
 * Quiet for the whole lesson — a small amber line that does not compete with the
 * arithmetic — and a full card on the one beat that is *about* the difference
 * between this and real RSA. A caveat attached to the picture cannot be skipped the
 * way a recap bullet can.
 */
@Composable
private fun Caveat(text: String, prominent: Boolean) {
    if (!prominent) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TOY EXAMPLE",
                style = AlgoType.labelSmall,
                color = AlgoColors.secondaryDark,
                maxLines = 1,
                modifier = Modifier
                    .background(AlgoColors.secondarySoft, Radius.pill)
                    .padding(horizontal = Spacing.xs, vertical = 2.dp),
            )
            Gap(Spacing.xs)
            Text(
                text = text,
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.secondarySoft, Radius.card)
            .border(Dimens.hairline, AlgoColors.secondary, Radius.card)
            .padding(Spacing.sm),
    ) {
        Text(
            text = "TOY EXAMPLE",
            style = AlgoType.labelSmall,
            color = AlgoColors.secondaryDark,
            maxLines = 1,
        )
        Gap(Spacing.xxs)
        Text(
            text = text,
            style = AlgoType.bodyLarge,
            color = AlgoColors.textSecondary,
        )
    }
}
