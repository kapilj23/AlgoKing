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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.algorithms.algoking.engine.scene.BlockCipherScene
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.CipherClaim
import com.algorithms.algoking.engine.scene.CipherStage
import com.algorithms.algoking.engine.scene.KeyScheduleView
import com.algorithms.algoking.engine.scene.RoundStepState
import com.algorithms.algoking.engine.scene.RoundStepView
import com.algorithms.algoking.engine.scene.VariantRow
import com.algorithms.algoking.ui.theme.AlgoColors
import com.algorithms.algoking.ui.theme.AlgoType
import com.algorithms.algoking.ui.theme.AlgoViz
import com.algorithms.algoking.ui.theme.Dimens
import com.algorithms.algoking.ui.theme.Radius
import com.algorithms.algoking.ui.theme.Spacing

/**
 * A block cipher's State, the round it is in, and the key schedule behind it —
 * DESIGN_SYSTEM.md §6.16m.
 *
 * ### The shape of the screen
 *
 * ```
 * Plaintext → Block → State → Rounds → Ciphertext        the concept, as a flow
 *
 * ┌ Round 3 / 10 ─────────────────────────────┐
 * │  ✓SubBytes  ✓ShiftRows  *MixColumns  ·Add │          what this round is made of
 * └───────────────────────────────────────────┘
 *
 *        ┌────┬────┬────┬────┐
 *        │ 3b │ 9c │ 7f │ b4 │                           the State: 4 × 4 bytes,
 *        ├────┼────┼────┼────┤                           filled column by column,
 *        │ c7 │ 38 │ 7e │ e4 │                           with this step's changes
 *        ├────┼────┼────┼────┤                           marked
 *        │ b8 │ 96 │ 99 │ 25 │
 *        ├────┼────┼────┼────┤
 *        │ 05 │ 1f │ 79 │ 84 │
 *        └────┴────┴────┴────┘
 * ```
 *
 * ### The final round's omission is drawn, not described
 *
 * MixColumns keeps its place in the round strip and is **struck through**. A step
 * that simply vanished would say nothing; a step crossed out says *this one is left
 * out*, which is the single thing this lesson is built to land.
 *
 * ### Nothing scrolls sideways
 *
 * Four byte cells across a phone is the least cramped grid in the app, and the
 * round strip wraps rather than shrinking its labels — ADR-039's rule from
 * Dijkstra's graph and ADR-046's from Caesar's alphabet: *the layout gives way,
 * never the thing the learner has to read.*
 *
 * ### The bright language, not a dark one
 *
 * Same cards, same 20dp radii, same violet, same `surfaceVariant` grounds as every
 * other lesson. A cipher is not a licence for a terminal aesthetic — the bytes are
 * drawn by the same `SceneCell` that draws Caesar's letters and Bubble Sort's
 * numbers, so the visual language cannot fork.
 */
@Composable
fun BlockCipherStage(
    scene: BlockCipherScene,
    modifier: Modifier = Modifier,
    /** Round steps the learner may tap. Empty means the strip is read-only. */
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {

        PipelineFlow(scene.pipeline)

        if (scene.roundLabel != null || scene.roundSteps.isNotEmpty()) {
            Gap(Spacing.md)
            RoundCard(
                label = scene.roundLabel,
                transformation = scene.transformation,
                detail = scene.transformationDetail,
                steps = scene.roundSteps,
                selectableSlots = selectableSlots,
                onSelectSlot = onSelectSlot,
            )
        }

        if (scene.state.isNotEmpty()) {
            Gap(Spacing.md)
            StateMatrix(scene.state, scene.changed)
        }

        scene.keySchedule?.let {
            Gap(Spacing.sm)
            KeyScheduleCard(it)
        }

        if (scene.variants.isNotEmpty()) {
            Gap(Spacing.sm)
            VariantTable(scene.variants)
        }

        if (scene.claims.isNotEmpty()) {
            Gap(Spacing.md)
            ClaimStrip(scene.claims)
        }
    }
}

/**
 * The five stages, always all five.
 *
 * On screen whole from the first frame, for the reason the SHA-256 pipeline and the
 * XOR truth table are: it is the shape of the operation, and a diagram that came
 * and went would read as a hint (ADR-047, ADR-048).
 *
 * The **Rounds stage is one pill and not ten**. Ten identical boxes would make this
 * a progress bar; what a round is made of is drawn properly in the card beneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PipelineFlow(stages: List<CipherStage>) {
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
private fun StagePill(stage: CipherStage) {
    val lit = stage.state == CellState.COMPARING
    val done = stage.state == CellState.FINALIZED

    val fill by animateColorAsState(
        targetValue = when {
            lit -> AlgoColors.primarySoft
            done -> AlgoColors.successSoft
            else -> AlgoColors.surfaceVariant
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "aesStageFill",
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
 * The round, and the steps it is made of.
 *
 * This card is both the picture and, when the learner is being asked which
 * transformation comes next, the control. Tapping a step is the gesture the graph,
 * counting and hash-map lessons already use, and it is here for ADR-034's reason
 * rather than for a layout one: pointing at the next step is what understanding an
 * order looks like, while picking its name off a list is what recognising a word
 * looks like.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoundCard(
    label: String?,
    transformation: String?,
    detail: String?,
    steps: List<RoundStepView>,
    selectableSlots: Set<Int>,
    onSelectSlot: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(Spacing.sm),
    ) {
        if (label != null || transformation != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                label?.let {
                    Text(
                        text = it,
                        style = AlgoType.labelSmall,
                        color = AlgoColors.textMuted,
                        maxLines = 1,
                    )
                }
                Box(Modifier.weight(1f))
                transformation?.let {
                    Text(
                        text = it,
                        style = AlgoType.titleSmall,
                        color = AlgoColors.primary,
                        maxLines = 1,
                    )
                }
            }
        }

        if (steps.isNotEmpty()) {
            Gap(Spacing.xs)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                steps.forEach { step ->
                    RoundStepChip(
                        step = step,
                        selectable = step.slot in selectableSlots,
                        onSelect = { onSelectSlot(step.slot) },
                    )
                }
            }
        }

        detail?.let {
            Gap(Spacing.xs)
            Text(
                text = it,
                style = AlgoType.bodyMedium,
                color = AlgoColors.textSecondary,
            )
        }
    }
}

/**
 * One transformation in the round.
 *
 * A [RoundStepState.SKIPPED] step is drawn in its place and **struck through** —
 * the final round's MixColumns, and the whole reason this strip exists.
 */
@Composable
private fun RoundStepChip(
    step: RoundStepView,
    selectable: Boolean,
    onSelect: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val skipped = step.state == RoundStepState.SKIPPED

    val fill = when (step.state) {
        RoundStepState.CURRENT -> AlgoColors.primarySoft
        RoundStepState.DONE -> AlgoColors.successSoft
        RoundStepState.SKIPPED -> AlgoColors.surfaceMuted
        RoundStepState.SELECTABLE, RoundStepState.UPCOMING -> AlgoColors.surface
    }
    val ink = when (step.state) {
        RoundStepState.CURRENT -> AlgoColors.primary
        RoundStepState.DONE -> AlgoColors.onSuccessSoft
        RoundStepState.SKIPPED -> AlgoColors.disabled
        RoundStepState.SELECTABLE -> AlgoColors.textPrimary
        RoundStepState.UPCOMING -> AlgoColors.textMuted
    }
    val outline = when {
        selectable -> AlgoViz.pointer
        step.state == RoundStepState.CURRENT -> AlgoViz.comparing
        else -> AlgoColors.border
    }

    Text(
        text = step.label,
        style = AlgoType.labelMedium,
        color = ink,
        maxLines = 1,
        textDecoration = if (skipped) TextDecoration.LineThrough else null,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(fill, Radius.cell)
            .border(
                if (selectable) Dimens.outlineStrong else Dimens.hairline,
                outline,
                Radius.cell,
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
            // The 48dp touch minimum every interactive element holds
            // (DESIGN_SYSTEM.md §3). The chip grows; the label never shrinks.
            .heightIn(min = if (selectable) Dimens.minTouchTarget else 32.dp)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

/**
 * The State: sixteen bytes as a 4 × 4 square.
 *
 * **Laid out column by column**, which is how AES fills it — slot `row + 4 ×
 * column`, so byte 4 is the top of the second column rather than the start of a
 * second row. The lesson's third beat says so out loud, and the grid has to agree
 * with it or the sentence is a lie about the picture.
 *
 * Cells are the shared `SceneCell`, carrying a hex label — the call Caesar made for
 * its letters, and what keeps a cryptography lesson inside the one visual language.
 */
@Composable
private fun StateMatrix(cells: List<Cell>, changed: Set<Int>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
    ) {
        (0 until ROWS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
            ) {
                (0 until COLUMNS).forEach { column ->
                    val slot = row + ROWS * column
                    val cell = cells.getOrNull(slot)
                    if (cell == null) {
                        Box(Modifier.weight(1f))
                    } else {
                        SceneCell(
                            cell = cell,
                            // "In range" is the sequence renderer's phrase for a
                            // cell still in play. Every byte of the State always
                            // is, so this is never the thing that varies here —
                            // what varies is whether this step touched it.
                            inRange = slot in changed,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The key, and what expansion made of it.
 *
 * Only the first few round keys are drawn and the rest are counted. Eleven keys of
 * thirty-two hex characters is a wall, and what the learner has to take away is
 * *one key in, one per round out* — never the values, which they are not asked for.
 */
@Composable
private fun KeyScheduleCard(schedule: KeyScheduleView) {
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
                text = "KEY",
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                maxLines = 1,
            )
            Box(Modifier.weight(1f))
            Text(
                text = "${schedule.keyBits} bits · ${schedule.roundKeys.size} round keys",
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                maxLines = 1,
            )
        }
        Gap(Spacing.xxs)
        Text(
            text = schedule.keyHex,
            style = AlgoType.digest,
            color = AlgoColors.textPrimary,
        )

        Gap(Spacing.xs)
        Text(
            text = "↓  Key Expansion",
            style = AlgoType.labelSmall,
            color = AlgoColors.primary,
        )
        Gap(Spacing.xxs)

        schedule.roundKeys.take(schedule.shown).forEachIndexed { index, key ->
            val active = index == schedule.activeRoundKey
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "K$index",
                    style = AlgoType.labelSmall,
                    color = if (active) AlgoColors.primary else AlgoColors.textMuted,
                    modifier = Modifier.width(Dimens.roundKeyLabelWidth),
                )
                Text(
                    text = key,
                    style = AlgoType.digest,
                    color = if (active) AlgoColors.primary else AlgoColors.textSecondary,
                    maxLines = 1,
                )
            }
        }

        val hidden = schedule.roundKeys.size - schedule.shown
        if (hidden > 0) {
            Text(
                text = "… and $hidden more, one per remaining round",
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
            )
        }

        schedule.activeRoundKey?.takeIf { it >= schedule.shown }?.let {
            Text(
                text = "K$it is in use",
                style = AlgoType.labelSmall,
                color = AlgoColors.primary,
            )
        }
    }
}

/**
 * The three variants, side by side.
 *
 * **The block size is on every row and is identical on every row**, which is the
 * whole point of drawing them together: the number in the name is the key size, and
 * a learner who reads "AES-256" as a 256-bit block has misread the one thing this
 * table exists to settle.
 *
 * A round count reads `?` until it has been settled — an answer already on screen
 * is not a question (ADR-030).
 */
@Composable
private fun VariantTable(variants: List<VariantRow>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "VARIANT",
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                modifier = Modifier.weight(1.2f),
            )
            Text(
                text = "KEY",
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "BLOCK",
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "ROUNDS",
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }

        variants.forEach { row ->
            val lit = row.state == CellState.COMPARING
            val ink = if (lit) AlgoColors.primary else AlgoColors.textPrimary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (lit) AlgoColors.primarySoft else Color.Transparent,
                        Radius.cell,
                    )
                    .padding(horizontal = Spacing.xxs, vertical = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    style = AlgoType.labelMedium,
                    color = ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = "${row.keyBits}",
                    style = AlgoType.labelMedium,
                    color = AlgoColors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${row.blockBits}",
                    style = AlgoType.labelMedium,
                    color = AlgoColors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.rounds?.toString() ?: "?",
                    style = AlgoType.labelMedium,
                    color = if (row.rounds == null) AlgoColors.textMuted else ink,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The statements, above the buttons that pick them.
 *
 * `HashScene`'s arrangement reused (ADR-048): a `DecisionButton` is one line at
 * `labelLarge`, so a statement long enough to be unambiguous cannot live on it. The
 * card carries the claim and the button carries the word, and the card prints the
 * word too so the pairing cannot be misread.
 *
 * **Both cards are identical in every state.** The scene does not carry which claim
 * is true, so the renderer could not style the right answer differently even by
 * accident (PRODUCT_SPEC.md §5).
 */
@Composable
private fun ClaimStrip(claims: List<CipherClaim>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        claims.forEachIndexed { index, claim ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(AlgoColors.surface, Radius.card)
                    .border(Dimens.hairline, AlgoColors.border, Radius.card)
                    .padding(Spacing.sm),
            ) {
                Text(
                    text = claim.choice,
                    style = AlgoType.labelSmall,
                    // The same tone the button beneath it will take, so the pairing
                    // is colour as well as position.
                    color = DecisionTone.forIndex(index).hue,
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
    }
}

private const val ROWS = 4
private const val COLUMNS = 4
