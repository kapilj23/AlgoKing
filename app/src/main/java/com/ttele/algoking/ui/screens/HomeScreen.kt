package com.ttele.algoking.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.ttele.algoking.billing.ProAccess
import com.ttele.algoking.engine.progress.LearningProgress
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoScreen
import com.ttele.algoking.ui.components.AlgoWordmark
import com.ttele.algoking.ui.components.AlgorithmCard
import com.ttele.algoking.ui.components.CategoryChip
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.IconTileButton
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoKingTheme
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Spacing

/**
 * SCREEN 1 — Home / Algorithm Library.
 *
 * Composition, per DESIGN_SYSTEM.md §7:
 * header → hero → category chips → algorithm list.
 * Content follows PRODUCT_SPEC.md §10 and §15; the visual language is the
 * reference's, unchanged.
 *
 * There is no bottom navigation. The MVP has exactly one destination — the
 * library — and a bar advertising three tabs that do not exist is a promise the
 * app cannot keep.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    /**
     * The learner's real state, from `ProgressRepository`. Home derives every ring
     * from this and from nothing else — not from visits, taps, or time spent.
     */
    progress: LearningProgress = LearningProgress.EMPTY,
    onOpenAlgorithm: (AlgorithmEntry) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    // The highlighted card is the one the learner is in the middle of — started,
    // not yet mastered. Nothing is hardcoded, so an untouched install highlights
    // nothing rather than pretending at a favourite.
    val inProgress = remember(progress) {
        algorithmLibrary.firstOrNull { progress[it.id].started && !progress[it.id].finished }
    }
    var selectedCategory by remember { mutableStateOf(algorithmCategories.first()) }
    val visible = remember(selectedCategory) {
        if (selectedCategory == "All") {
            algorithmLibrary
        } else {
            algorithmLibrary.filter { it.category == selectedCategory }
        }
    }
    val gutter = Modifier.padding(horizontal = Dimens.screenPadding)

    AlgoScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            // The wordmark, and one gear. The crown tile and the search tile were
            // affordances for a profile and a search that the MVP does not have;
            // the gear is the settings entry PRODUCT_SPEC.md §10 puts behind the
            // Progress tab, parked in the only destination there is.
            AlgoHeader(
                modifier = Modifier.statusBarsPadding(),
                center = { AlgoWordmark() },
                trailing = {
                    IconTileButton(
                        icon = AlgoIcons.Settings,
                        tint = AlgoColors.textSecondary,
                        onClick = onOpenSettings,
                    )
                },
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                item { Hero(gutter.fillMaxWidth()) }

                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = Dimens.screenPadding),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        items(algorithmCategories) { category ->
                            CategoryChip(
                                label = category,
                                selected = category == selectedCategory,
                                onClick = { selectedCategory = category },
                            )
                        }
                    }
                }

                items(visible) { entry ->
                    AlgorithmCard(
                        title = entry.title,
                        description = entry.description,
                        category = entry.category,
                        accent = entry.accent,
                        glyph = entry.glyph,
                        status = statusFor(progress[entry.id]),
                        selected = entry.id == inProgress?.id,
                        // Derived from the category, never stored per entry —
                        // there is one definition of what Pro covers (ADR-041).
                        pro = ProAccess.requiresPro(entry.category),
                        modifier = gutter,
                        onClick = { onOpenAlgorithm(entry) },
                    )
                }
            }
        }
    }
}

/**
 * The hero — DESIGN_SYSTEM.md §7.
 * Two 28sp/800 lines: a statement in ink, then the promise in the three brand
 * hues (violet · blue · orange), and a muted one-line subtitle.
 */
@Composable
private fun Hero(modifier: Modifier = Modifier) {
    Column(modifier) {
        Gap(Spacing.xxs)
        Text(
            text = "Master Algorithms.",
            style = AlgoType.displayLarge,
            color = AlgoColors.textPrimary,
        )
        Text(text = promiseLine(), style = AlgoType.displayLarge)
        Gap(Spacing.xs)
        Text(
            text = "Choose an algorithm to start your journey",
            style = AlgoType.bodyMedium,
            color = AlgoColors.textMuted,
        )
        Gap(Spacing.xs)
    }
}

@Composable
private fun promiseLine(): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = AlgoColors.primary)) { append("Think. ") }
    withStyle(SpanStyle(color = AlgoColors.accent)) { append("Visualize. ") }
    withStyle(SpanStyle(color = AlgoColors.secondary)) { append("Conquer.") }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    AlgoKingTheme { HomeScreen() }
}
