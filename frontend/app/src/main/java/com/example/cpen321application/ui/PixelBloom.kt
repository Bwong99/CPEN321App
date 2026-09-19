package com.example.cpen321application.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.math.hypot
import kotlin.random.Random
import kotlinx.coroutines.delay

private const val ONE_SECOND_MS = 1_000L
private const val BLOOM_STEP_MS = 18L
private const val HUE_DEGREES = 360f
private const val HUE_SPREAD = 14f
private const val SATURATION = 0.75f
private const val VALUE_FLOOR = 0.55f
private const val VALUE_RANGE = 0.45f

/**
 * Drives the countdown. Re-keyed on [runId] so starting or cancelling
 * cancels any previous run.
 */
@Composable
internal fun CountdownEffect(
    runId: Int,
    total: Int,
    onTick: (Int) -> Unit,
    onFinished: () -> Unit,
) {
    LaunchedEffect(runId) {
        if (total <= 0) {
            return@LaunchedEffect
        }

        var left = total
        while (left > 0) {
            delay(ONE_SECOND_MS)
            left -= 1
            onTick(left)
        }
        onFinished()
    }
}

/**
 * The Button 3 surprise: a procedurally generated artwork that blooms outward
 * from the centre of a 16x16 grid, one cell at a time.
 *
 * Generated on the device, with a fresh random palette each run, so it is
 * different every time and never depends on an external service being up.
 */
@Composable
internal fun PixelBloom(modifier: Modifier = Modifier) {
    val cells = remember {
        mutableStateListOf<Color>().apply { repeat(CELL_COUNT) { add(Color.Unspecified) } }
    }

    LaunchedEffect(Unit) {
        val baseHue = Random.nextFloat() * HUE_DEGREES
        val centre = (GRID_SIZE - 1) / 2f

        // Reveal order: nearest the centre first, so the artwork blooms.
        val order = (0 until CELL_COUNT).sortedBy { index ->
            val x = index % GRID_SIZE
            val y = index / GRID_SIZE
            hypot(x - centre, y - centre)
        }

        for (index in order) {
            val x = index % GRID_SIZE
            val y = index / GRID_SIZE
            val distance = hypot(x - centre, y - centre)

            cells[index] = Color.hsv(
                hue = (baseHue + distance * HUE_SPREAD) % HUE_DEGREES,
                saturation = SATURATION,
                value = VALUE_FLOOR + Random.nextFloat() * VALUE_RANGE,
            )
            delay(BLOOM_STEP_MS)
        }
    }

    PixelGrid(cells = cells, modifier = modifier)
}
