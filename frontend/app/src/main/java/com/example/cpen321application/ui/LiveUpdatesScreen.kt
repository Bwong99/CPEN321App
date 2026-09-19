package com.example.cpen321application.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.toColorInt
import com.example.cpen321application.BuildConfig
import com.example.cpen321application.R
import com.example.cpen321application.data.PixelStream
import kotlinx.coroutines.flow.catch

/**
 * Button 2. Paints a 16x16 grid one cell at a time as updates arrive from the
 * back-end relay, so the image assembles live rather than appearing at once.
 */
@Composable
fun LiveUpdatesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseUrl = BuildConfig.API_BASE_URL
    val cells = remember {
        mutableStateListOf<Color>().apply { repeat(CELL_COUNT) { add(Color.Unspecified) } }
    }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(baseUrl) {
        PixelStream.connect(baseUrl)
            .catch { cause -> error = cause.message ?: cause.javaClass.simpleName }
            .collect { update ->
                val index = update.y * GRID_SIZE + update.x
                if (index in 0 until CELL_COUNT) {
                    // Painted as it arrives; no buffering, so the picture
                    // emerges cell by cell.
                    cells[index] = parseColor(update.color)
                }
            }
    }

    ScreenContainer(
        title = stringResource(R.string.title_live_updates),
        onBack = onBack,
        modifier = modifier,
    ) {
        PixelGrid(cells = cells)

        error?.let { message ->
            Text(
                text = stringResource(R.string.status_stream_error),
                color = MaterialTheme.colorScheme.error,
            )
            Text(text = message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun parseColor(hex: String): Color =
    runCatching { Color(hex.toColorInt()) }.getOrDefault(Color.Unspecified)
