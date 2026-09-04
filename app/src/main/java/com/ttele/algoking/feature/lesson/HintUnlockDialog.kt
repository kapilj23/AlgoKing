package com.ttele.algoking.feature.lesson

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.PrimaryButton
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing

/**
 * The offer, when a learner reaches for a hint they have already used the free
 * rung of.
 *
 * Three rules hold this screen to the product's side of the line
 * (PRODUCT_SPEC.md §9):
 *
 *  - **it is an offer, not a toll.** Declining is a plain, equal-weight button,
 *    never a greyed-out afterthought.
 *  - **the reward is exactly the hint.** No coins, no streak freeze, no bundle.
 *  - **the solution was never behind it.** The free rung and the guidance ladder
 *    already contain the route; what an ad buys is a shortcut through thinking
 *    the learner could do unaided.
 */
@Composable
fun HintUnlockDialog(
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AlgoColors.surface,
        shape = Radius.card,
        title = {
            Text(
                text = "Need another nudge?",
                style = AlgoType.titleLarge,
                color = AlgoColors.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = "Your first hint on each step is always free. " +
                        "Watch a short ad to unlock the next one.",
                    style = AlgoType.bodyLarge,
                    color = AlgoColors.textSecondary,
                )
                Gap(Spacing.xs)
                Text(
                    text = "It costs you nothing but time, and never affects whether " +
                        "you finish the mission.",
                    style = AlgoType.bodyMedium,
                    color = AlgoColors.textMuted,
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                label = "Watch ad",
                modifier = Modifier.fillMaxWidth(),
                icon = AlgoIcons.Bulb,
                onClick = onWatchAd,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Keep trying",
                    style = AlgoType.titleSmall,
                    color = AlgoColors.textSecondary,
                )
            }
        },
    )
}
