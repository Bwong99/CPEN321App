package com.example.cpen321application.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** The course pixel stream uses a fixed 16x16 grid. */
internal const val GRID_SIZE = 16
internal const val CELL_COUNT = GRID_SIZE * GRID_SIZE

/**
 * Draws a 16x16 grid of cells. Shared by Button 2's live pixel stream and
 * Button 3's bloom animation.
 *
 * Cells set to [Color.Unspecified] are drawn in the theme's blank colour.
 */
@Composable
internal fun PixelGrid(
    cells: List<Color>,
    modifier: Modifier = Modifier,
) {
    val blank = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio = 1f)
    ) {
        val cellSize = size.width / GRID_SIZE

        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val painted = cells[y * GRID_SIZE + x]
                drawRect(
                    color = if (painted == Color.Unspecified) blank else painted,
                    topLeft = Offset(x = x * cellSize, y = y * cellSize),
                    size = Size(width = cellSize, height = cellSize),
                )
            }
        }
    }
}
