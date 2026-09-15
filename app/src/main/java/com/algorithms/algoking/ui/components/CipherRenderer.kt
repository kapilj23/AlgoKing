package com.algorithms.algoking.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.CipherPair
import com.algorithms.algoking.engine.scene.CipherScene
import com.algorithms.algoking.engine.scene.CipherStep
import com.algorithms.algoking.ui.theme.AlgoColors
import com.algorithms.algoking.ui.theme.AlgoType
import com.algorithms.algoking.ui.theme.AlgoViz
import com.algorithms.algoking.ui.theme.Dimens
import com.algorithms.algoking.ui.theme.Radius
import com.algorithms.algoking.ui.theme.Spacing

/**
 * A message, an alphabet mapping, and the message it becomes —
 * DESIGN_SYSTEM.md §6.16j.
 *
 * ### The mapping wraps onto rows; it never shrinks
 *
 * Twenty-six tiles in one row is about 7dp each at 360dp, which is not a letter —
 * it is a smudge. The rule Dijkstra's layout spike set (ADR-039) is that the
 * *layout* gives way, never the thing the learner has to read, so the alphabet
 * wraps onto two rows of thirteen and each tile keeps a legible width.
 *
 * It is drawn **in full, always**. The wrap is the half of this cipher a learner
 * gets wrong, and it only becomes a picture when `X Y Z` are visibly sitting above
 * `A B C`. A mapping showing only the letters in play would remove the lesson.
 *
 * ### The tile is the mapping
 *
 * Each tile carries the plain letter over the letter it becomes, so one lit tile
 * *is* the answer rather than pointing at it — the treatment Counting Sort gives a
 * bucket, which prints the count inside and the value it counts underneath.
 */
@Composable
fun CipherTable(
    scene: CipherScene,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {

        RowCaption(scene.plaintextLabel)
        CellRow(scene.plaintext)

        Gap(Spacing.md)

        RowCaption(scene.alphabetLabel)
        AlphabetGrid(scene.alphabet)

        Gap(Spacing.md)

        RowCaption(scene.ciphertextLabel)
        CellRow(scene.ciphertext)

        scene.step?.let {
            Gap(Spacing.md)
            CipherStrip(it)
        }
    }
}

/**
 * The message, as ordinary cells.
 *
 * `SceneCell` already draws `cell.label ?: cell.value`, so a letter needs no new
 * component — the same boxes, the same states, the same colours every other lesson
 * uses. A ciphertext position not produced yet is a `GHOST`: a hole, never a
 * placeholder letter standing in for an answer that does not exist.
 */
@Composable
private fun CellRow(cells: List<Cell>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
    ) {
        cells.sortedBy { it.slot }.forEach { cell ->
            SceneCell(
                cell = cell,
                inRange = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * All 26 letters and what each becomes, wrapped onto rows of [PER_ROW].
 *
 * Thirteen per row gives about 22dp a tile at 360dp — narrow, but a single capital
 * letter at `labelMedium` fits it with room to spare, and nothing here is tappable
 * so the 48dp touch minimum does not apply. The learner *reads* this row; they
 * answer with the buttons below the card.
 */
@Composable
private fun AlphabetGrid(alphabet: List<CipherPair>) {
    alphabet.chunked(PER_ROW).forEach { rowPairs ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
        ) {
            rowPairs.forEach { pair ->
                LetterPair(pair, Modifier.weight(1f))
            }
            // A short final row keeps its tiles the same width as a full one, so
            // the two rows read as one alphabet rather than as two lists.
            repeat(PER_ROW - rowPairs.size) {
                Column(Modifier.weight(1f)) {}
            }
        }
    }
}

/** One letter over the letter it becomes. The lit one is the answer. */
@Composable
private fun LetterPair(pair: CipherPair, modifier: Modifier = Modifier) {
    val current = pair.state == CellState.COMPARING

    val outline by animateColorAsState(
        targetValue = if (current) AlgoViz.comparing else Color.Transparent,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "pairOutline",
    )
    val fill by animateColorAsState(
        targetValue = if (current) AlgoColors.primarySoft else AlgoColors.surfaceVariant,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "pairFill",
    )

    Column(
        modifier = modifier
            .background(fill, Radius.sceneCell)
            .border(Dimens.outline, outline, Radius.sceneCell)
            .padding(vertical = Spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = pair.from.toString(),
            style = AlgoType.labelMedium,
            color = if (current) AlgoViz.comparing else AlgoColors.textSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        // The arrow is implied by the stacking rather than drawn: at this width a
        // glyph between the two letters costs more room than it buys.
        Text(
            text = pair.to.toString(),
            style = AlgoType.labelMedium,
            color = if (current) AlgoColors.primary else AlgoColors.textPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The working, on one line: `H (7) + 3 = 10 → K`.
 *
 * When the letter runs off the end of the alphabet the subtraction `mod 26`
 * actually performs is shown — `Z (25) + 3 = 28 − 26 = 2 → C` — because a learner
 * told only "it wraps" has a word, while one who watches 28 become 2 has the rule.
 *
 * The result reads `?` until it is known: an answer already on screen is not a
 * question (ADR-030).
 */
@Composable
private fun CipherStrip(step: CipherStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Operand(step.from.toString(), "index ${step.fromIndex}")
        Symbol("+")
        Operand(step.shift.toString(), "shift")
        Symbol("=")
        if (step.wrapped) {
            // The wrap, spelled out. This is the only place the cipher does
            // something a learner cannot do by counting forwards on their fingers.
            Operand(step.sum.toString(), null)
            Symbol("−")
            Operand("26", "wrap")
            Symbol("=")
        }
        Operand(
            value = step.toIndex?.toString() ?: "?",
            caption = null,
        )
        Symbol("→")
        Operand(
            value = step.to?.toString() ?: "?",
            caption = null,
            emphasised = true,
        )
    }
}

@Composable
private fun Operand(value: String, caption: String?, emphasised: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        caption?.let {
            Text(
                text = it,
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                maxLines = 1,
            )
        }
        Text(
            text = value,
            style = AlgoType.numeralMedium,
            color = if (emphasised) AlgoColors.primary else AlgoColors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun Symbol(text: String) {
    Text(
        text = text,
        style = AlgoType.titleSmall,
        color = AlgoColors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.xxs),
    )
}

/** The row's name, so the three rows are never confused for one. */
@Composable
private fun RowCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = AlgoType.labelSmall,
        color = AlgoColors.textMuted,
        modifier = Modifier.padding(bottom = Spacing.xxs),
    )
}

/**
 * Thirteen: the only divisor of 26 that gives two equal rows and keeps a tile wide
 * enough to read at 360dp. Nine would be wider still but leaves a ragged third row,
 * and the alphabet stops looking like one object.
 */
private const val PER_ROW = 13
