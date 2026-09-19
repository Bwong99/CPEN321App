package com.example.cpen321application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cpen321application.ui.LiveUpdatesScreen
import com.example.cpen321application.ui.ScreenPadding
import com.example.cpen321application.ui.ServerInfoScreen
import com.example.cpen321application.ui.TimerScreen
import com.example.cpen321application.ui.theme.CPEN321ApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CPEN321ApplicationTheme {
                CpenApp()
            }
        }
    }
}

/**
 * The destinations reachable from the home screen. M1 requires the three
 * buttons to work independently of one another, so each is a separate screen
 * with no shared state.
 */
enum class Screen {
    HOME,
    SERVER_INFO,
    LIVE_UPDATES,
    TIMER,
}

@Composable
fun CpenApp(modifier: Modifier = Modifier) {
    // rememberSaveable so the open screen survives rotation and process death.
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val goHome = { screen = Screen.HOME }

    BackHandler(enabled = screen != Screen.HOME, onBack = goHome)

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        val screenModifier = Modifier
            .padding(paddingValues = innerPadding)
            .fillMaxSize()

        when (screen) {
            Screen.HOME -> HomeScreen(
                onSelect = { selected -> screen = selected },
                modifier = screenModifier,
            )

            Screen.SERVER_INFO -> ServerInfoScreen(
                onBack = goHome,
                modifier = screenModifier,
            )

            Screen.LIVE_UPDATES -> LiveUpdatesScreen(
                onBack = goHome,
                modifier = screenModifier,
            )

            Screen.TIMER -> TimerScreen(
                onBack = goHome,
                modifier = screenModifier,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(all = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )

        HomeButton(
            label = stringResource(R.string.home_button_server),
            onClick = { onSelect(Screen.SERVER_INFO) },
        )
        HomeButton(
            label = stringResource(R.string.home_button_live_updates),
            onClick = { onSelect(Screen.LIVE_UPDATES) },
        )
        HomeButton(
            label = stringResource(R.string.home_button_timer),
            onClick = { onSelect(Screen.TIMER) },
        )
    }
}

@Composable
private fun HomeButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Text(text = label)
    }
}
