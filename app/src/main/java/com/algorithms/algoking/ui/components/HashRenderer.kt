package com.algorithms.algoking.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.HashClaim
import com.algorithms.algoking.engine.scene.HashDigest
import com.algorithms.algoking.engine.scene.HashRow
import com.algorithms.algoking.engine.scene.HashScene
import com.algorithms.algoking.engine.scene.HashStage
import com.algorithms.algoking.ui.theme.AlgoColors
import com.algorithms.algoking.ui.theme.AlgoType
import com.algorithms.algoking.ui.theme.AlgoViz
import com.algorithms.algoking.ui.theme.Dimens
import com.algorithms.algoking.ui.theme.Radius
import com.algorithms.algoking.ui.theme.Spacing

/**
 * A hash pipeline, a digest, and messages compared against each other —
 * DESIGN_SYSTEM.md §6.16l.
 *
 * ### The shape of the screen
 *
 * ```
 * Input → Preprocess → SHA-256 → Hash → Hex        the concept, as a flow
 *
 * ┌──────────────────────────────┐
 * │ INPUT                        │
 * │ "hello" · 5 characters       │                 the demonstration, as
 * └──────────────┬───────────────┘                 the brief draws it:
 *             SHA-256                              one arrow, one way
 * ┌──────────────┴───────────────┐
 * │ HASH · 256 bits · 64 chars   │
 * │ 2cf24dba 5fb0a30e 26e83b2a … │
 * └──────────────────────────────┘
 * ```
 *
 * ### Nothing here scrolls sideways
 *
 * Sixty-four characters is wider than a phone, so the digest **wraps into groups of
 * eight** and the pipeline wraps its stages. Neither ever shrinks a glyph to fit —
 * the rule ADR-039 set on Dijkstra's graph and ADR-046 applied to Caesar's alphabet:
 * *the layout gives way, never the thing the learner has to read.* Grouping is a
 * reading aid only, and the characters and their order are untouched, so the exact
 * digest is always on screen.
 *
 * ### The bright language, not a dark one
 *
 * Same cards, same radii, same violet, same `surfaceVariant` grounds as every other
 * lesson. A cryptography lesson is not an excuse for a terminal aesthetic — the one
 * thing that is not Plus Jakarta Sans is the digest itself, and only so that two
 * digests line up column for column.
 */
@Composable
fun HashTable(
    scene: HashScene,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {

        PipelineFlow(scene.pipeline)

        scene.digest?.let {
            Gap(Spacing.md)
            DigestCard(it)
        }

        if (scene.comparisons.isNotEmpty()) {
            Gap(Spacing.md)
            scene.comparisons.forEachIndexed { index, row ->
                if (index > 0) Gap(Spacing.xs)
                ComparisonRow(row)
            }
        }

        if (scene.claims.isNotEmpty()) {
            Gap(Spacing.md)
            ClaimStrip(scene.claims)
        }
    }
}

/**
 * The conceptual pipeline, as a wrapped flow of stages.
 *
 * Always **all five**, including while a judgement is live: it is the shape of the
 * operation rather than a progress bar, and a diagram that came and went would read
 * as a hint (the rule the XOR truth table follows, ADR-047).
 *
 * The **SHA-256 stage is one stage**, and the lesson is deliberate about that: real
 * SHA-256 pads the message, builds a message schedule and runs 64 compression
 * rounds over eight working variables. Drawing sixty-four rounds would bury a
 * beginner, and drawing invented ones would be worse — so the box is labelled with
 * what is actually inside it and the copy says this lesson is not about it
 * (ADR-048).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PipelineFlow(stages: List<HashStage>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        stages.forEachIndexed { index, stage ->
            if (index > 0) {
                Text(
                    text = "→",
                    style = AlgoType.labelSmall,
                    color = AlgoColors.disabled,
                    modifier = Modifier.padding(horizontal = Spacing.xxs),
                )
            }
            StagePill(stage)
        }
    }
}

@Composable
private fun StagePill(stage: HashStage) {
    val lit = stage.state == CellState.COMPARING
    val done = stage.state == CellState.FINALIZED

    val fill by animateColorAsState(
        targetValue = when {
            lit -> AlgoColors.primarySoft
            done -> AlgoColors.successSoft
            else -> AlgoColors.surfaceVariant
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "stageFill",
    )
    val ink = when {
        lit -> AlgoColors.primary
        done -> AlgoColors.onSuccessSoft
        else -> AlgoColors.textSecondary
    }

    Text(
        text = stage.label,
        style = AlgoType.labelMedium,
        color = ink,
        maxLines = 1,
        modifier = Modifier
            .background(fill, Radius.pill)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    )
}

/**
 * The demonstration: what went in, and what came out.
 *
 * Drawn as two cards with **one arrow between them, pointing one way**. That arrow
 * is the lesson's whole distinction from the two cipher lessons on the same shelf —
 * Caesar and XOR both draw a message and the message it becomes, and both go back.
 * This one does not, and the picture should be the first place a learner notices.
 */
@Composable
private fun DigestCard(digest: HashDigest) {
    Column(Modifier.fillMaxWidth()) {

        FieldCard(
            caption = "INPUT",
            trailing = "${digest.inputLength} characters",
        ) {
            Text(
                text = "“${digest.input}”",
                style = AlgoType.titleSmall,
                color = AlgoColors.textPrimary,
            )
        }

        // The one-way arrow, with the function that does the work named on it.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = Spacing.xxs),
            ) {
                Text("↓", style = AlgoType.titleSmall, color = AlgoColors.primary)
                Text(
                    text = "SHA-256",
                    style = AlgoType.labelSmall,
                    color = AlgoColors.primary,
                )
                Text("↓", style = AlgoType.titleSmall, color = AlgoColors.primary)
            }
        }

        FieldCard(
            caption = "HASH",
            trailing = "${digest.bits} bits · ${digest.bytes} bytes · " +
                "${digest.hex.length} chars",
        ) {
            DigestText(groups = digest.groups, differing = emptySet())
        }
    }
}

/**
 * One row of a comparison: the message, its length, and its digest.
 *
 * The **length is printed beside every message**, because the fixed-length beat is
 * a claim about two numbers and a learner should not have to count characters to
 * check it.
 */
@Composable
private fun ComparisonRow(row: HashRow) {
    val outline = when (row.state) {
        CellState.COMPARING -> AlgoViz.comparing
        CellState.CANDIDATE -> AlgoViz.next
        else -> AlgoColors.border
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .border(Dimens.outline, outline, Radius.card)
            .padding(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.label,
                style = AlgoType.titleSmall,
                color = AlgoColors.textPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Box(Modifier.weight(1f))
            Text(
                // Both numbers, on every row: "the input length varied, the output
                // length did not" is the entire fixed-length lesson, and it should
                // be readable without counting anything.
                text = "${row.inputLength} in · ${row.hexLength} out",
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                maxLines = 1,
            )
        }
        Gap(Spacing.xxs)
        DigestText(groups = row.groups, differing = row.differing)
    }
}

/**
 * A digest, wrapped into groups of eight, with the changed characters marked.
 *
 * Marking is per **character position**, which is the only honest way to show the
 * avalanche: 61 of 64 characters changing is a picture, and "the hash changes a
 * lot" is an adjective. It is equally the right picture for determinism, where
 * [differing] is empty and nothing is marked at all — the same input, and visibly
 * nothing to point at.
 *
 * The groups are a reading aid: `groups.joinToString("")` is the exact digest, so
 * nothing on screen is an abbreviation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DigestText(groups: List<String>, differing: Set<Int>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        var offset = 0
        groups.forEach { group ->
            val start = offset
            Text(
                text = markedGroup(group, start, differing),
                style = AlgoType.digest,
                maxLines = 1,
            )
            offset += group.length
        }
    }
}

/** One group, with any changed characters in the amber the legend calls "Changed". */
private fun markedGroup(
    group: String,
    start: Int,
    differing: Set<Int>,
): AnnotatedString = buildAnnotatedString {
    group.forEachIndexed { index, char ->
        val changed = (start + index) in differing
        withStyle(
            SpanStyle(
                color = if (changed) AlgoViz.next else AlgoColors.textSecondary,
                fontWeight = if (changed) FontWeight.Bold else FontWeight.Medium,
            ),
        ) {
            append(char)
        }
    }
}

/**
 * The two statements, above the two buttons that pick them.
 *
 * `DpTableScene`'s choice strip, reused (DESIGN_SYSTEM.md §6.16i): a
 * `DecisionButton` is one line at `labelLarge`, so a statement long enough to be
 * unambiguous cannot live on it — Two Pointers hit the same wall and solved it the
 * same way (ADR-032). The card carries the claim and the button carries the word,
 * and the card prints the word too so the pairing cannot be misread.
 *
 * **Both cards are identical in every state.** Neither is tinted, outlined or
 * weighted differently, because styling the true one would answer the question
 * (PRODUCT_SPEC.md §5) — and the scene does not carry which one is true, so the
 * renderer could not do it even by accident.
 */
@Composable
private fun ClaimStrip(claims: List<HashClaim>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        claims.forEachIndexed { index, claim ->
            ClaimCard(
                claim = claim,
                // The same tone the button beneath it will take, so the pairing is
                // colour as well as position.
                accent = DecisionTone.forIndex(index).hue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ClaimCard(
    claim: HashClaim,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(AlgoColors.surface, Radius.card)
            .border(Dimens.outline, AlgoColors.border, Radius.card)
            .padding(Spacing.sm),
    ) {
        Text(
            text = claim.choice,
            style = AlgoType.labelSmall,
            color = accent,
            maxLines = 1,
        )
        Gap(Spacing.xxs)
        Text(
            text = claim.text,
            style = AlgoType.bodyMedium,
            color = AlgoColors.textSecondary,
        )
    }
}

/** A captioned white card — the input and hash halves of the demonstration. */
@Composable
private fun FieldCard(
    caption: String,
    trailing: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = caption,
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                maxLines = 1,
            )
            Box(Modifier.weight(1f))
            Text(
                text = trailing,
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                maxLines = 1,
            )
        }
        Gap(Spacing.xxs)
        content()
    }
}
