package com.example.cpen321application.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.cpen321application.R

private const val SECONDS_PER_MINUTE = 60
private const val MAX_FIELD_LENGTH = 3

/**
 * Button 3. Counts down from a user-supplied minutes/seconds value, then
 * reveals the surprise.
 */
@Composable
fun TimerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var minutes by rememberSaveable { mutableStateOf("0") }
    var seconds by rememberSaveable { mutableStateOf("10") }
    var runId by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(0) }
    var fired by remember { mutableStateOf(false) }

    CountdownEffect(
        runId = runId,
        total = total,
        onTick = { remaining = it },
        onFinished = { fired = true },
    )

    ScreenContainer(
        title = stringResource(R.string.title_timer),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (fired) {
            Text(
                text = stringResource(R.string.surprise_title),
                style = MaterialTheme.typography.titleMedium,
            )
            PixelBloom()
            TextButton(onClick = { fired = false; remaining = 0; total = 0 }) {
                Text(text = stringResource(R.string.action_reset))
            }
        } else {
            DurationFields(
                minutes = minutes,
                seconds = seconds,
                enabled = remaining == 0,
                onMinutes = { minutes = it.filter(Char::isDigit).take(MAX_FIELD_LENGTH) },
                onSeconds = { seconds = it.filter(Char::isDigit).take(MAX_FIELD_LENGTH) },
            )

            if (remaining > 0) {
                Text(
                    text = stringResource(R.string.status_countdown, format(remaining)),
                    style = MaterialTheme.typography.headlineMedium,
                )
                TextButton(onClick = { total = 0; remaining = 0; runId++ }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            } else {
                Button(
                    enabled = durationOf(minutes, seconds) > 0,
                    onClick = {
                        total = durationOf(minutes, seconds)
                        remaining = total
                        runId++
                    },
                ) {
                    Text(text = stringResource(R.string.action_start))
                }
            }
        }
    }
}

@Composable
private fun DurationFields(
    minutes: String,
    seconds: String,
    enabled: Boolean,
    onMinutes: (String) -> Unit,
    onSeconds: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = ItemSpacing),
    ) {
        OutlinedTextField(
            value = minutes,
            onValueChange = onMinutes,
            enabled = enabled,
            singleLine = true,
            label = { Text(text = stringResource(R.string.label_minutes)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(weight = 1f),
        )
        OutlinedTextField(
            value = seconds,
            onValueChange = onSeconds,
            enabled = enabled,
            singleLine = true,
            label = { Text(text = stringResource(R.string.label_seconds)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(weight = 1f),
        )
    }
}

private fun durationOf(minutes: String, seconds: String): Int =
    (minutes.toIntOrNull() ?: 0) * SECONDS_PER_MINUTE + (seconds.toIntOrNull() ?: 0)

private fun format(totalSeconds: Int): String {
    val mins = totalSeconds / SECONDS_PER_MINUTE
    val secs = totalSeconds % SECONDS_PER_MINUTE
    return "%02d:%02d".format(mins, secs)
}
