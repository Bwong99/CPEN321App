package com.example.cpen321application.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R

// Declared via a const so detekt's MagicNumber rule (which does not exempt
// property declarations) stays satisfied without suppressions.
private const val SCREEN_PADDING_DP = 24
private const val ITEM_SPACING_DP = 12

internal val ScreenPadding = SCREEN_PADDING_DP.dp
internal val ItemSpacing = ITEM_SPACING_DP.dp

/**
 * Shared frame for the three feature screens: a title, the screen's content,
 * and a back action returning to the home screen.
 */
@Composable
internal fun ScreenContainer(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(all = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(space = ItemSpacing),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )

        content()

        TextButton(onClick = onBack) {
            Text(text = stringResource(R.string.action_back))
        }
    }
}
