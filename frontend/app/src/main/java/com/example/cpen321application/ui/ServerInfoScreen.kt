package com.example.cpen321application.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cpen321application.BuildConfig
import com.example.cpen321application.R
import com.example.cpen321application.data.AuthenticatedUser
import com.example.cpen321application.data.BackendClient
import com.example.cpen321application.data.DeviceInfo
import com.example.cpen321application.data.GoogleAuth
import com.example.cpen321application.data.NoDeviceAccountException
import com.example.cpen321application.data.ServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CARD_PADDING_DP = 16
private const val ROW_SPACING_DP = 14

private val CardPadding = CARD_PADDING_DP.dp
private val RowSpacing = ROW_SPACING_DP.dp

/**
 * A completed sign-in: the ID token every later request is authenticated with,
 * and the account the back-end verified that token to belong to.
 */
private data class Session(
    val idToken: String,
    val user: AuthenticatedUser,
)

private sealed interface DetailsUiState {
    data object Loading : DetailsUiState

    data class Failed(val message: String) : DetailsUiState

    data class Loaded(
        val server: ServerInfo,
        val clientIp: String?,
        val clientTime: String,
    ) : DetailsUiState
}

/**
 * Button 1. The user signs in with Google first; only then does the app call
 * the back-end and show the six values M1 requires.
 */
@Composable
fun ServerInfoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var session by remember { mutableStateOf<Session?>(null) }

    ScreenContainer(
        title = stringResource(R.string.title_server),
        onBack = onBack,
        modifier = modifier,
    ) {
        val signedIn = session
        if (signedIn == null) {
            SignInSection(onSignedIn = { session = it })
        } else {
            DetailsSection(session = signedIn)
        }
    }
}

@Composable
private fun SignInSection(
    onSignedIn: (Session) -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseUrl = BuildConfig.API_BASE_URL
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsAccount by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = ItemSpacing),
    ) {
        Text(text = stringResource(R.string.prompt_sign_in))

        Button(
            enabled = !busy && activity != null,
            onClick = {
                val host = activity ?: return@Button
                busy = true
                needsAccount = false
                error = null
                scope.launch {
                    // Two steps, and the second is the one that counts: Google
                    // issues a token, then the back-end verifies it and says
                    // who it belongs to. Sign-in has not happened until the
                    // server agrees, so a rejected token fails the whole thing.
                    val result = GoogleAuth.signIn(host, BuildConfig.GOOGLE_CLIENT_ID)
                        .mapCatching { idToken ->
                            val user = BackendClient
                                .authenticate(baseUrl, idToken)
                                .getOrThrow()
                            Session(idToken = idToken, user = user)
                        }
                    busy = false
                    result.fold(
                        onSuccess = onSignedIn,
                        onFailure = { cause ->
                            needsAccount = cause is NoDeviceAccountException
                            error = cause.message ?: cause.javaClass.simpleName
                        },
                    )
                }
            },
        ) {
            Text(text = stringResource(R.string.action_sign_in))
        }

        if (busy) {
            Text(text = stringResource(R.string.status_signing_in))
        }

        if (needsAccount) {
            // Android has no account to offer, so send the user straight to
            // the add-account flow rather than leaving them at a dead end.
            Text(text = stringResource(R.string.status_no_account))
            Button(onClick = { activity?.startActivity(addGoogleAccountIntent()) }) {
                Text(text = stringResource(R.string.action_add_account))
            }
        } else {
            error?.let { message ->
                Text(
                    text = stringResource(R.string.status_sign_in_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DetailsSection(
    session: Session,
    modifier: Modifier = Modifier,
) {
    val baseUrl = BuildConfig.API_BASE_URL
    var reloadKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<DetailsUiState>(DetailsUiState.Loading) }

    LaunchedEffect(baseUrl, reloadKey) {
        state = DetailsUiState.Loading
        val result = BackendClient.fetchServerInfo(baseUrl, session.idToken)
        // Enumerating network interfaces blocks, so keep it off the main thread.
        val clientIp = withContext(Dispatchers.IO) { DeviceInfo.ipAddress() }

        state = result.fold(
            onSuccess = { server ->
                DetailsUiState.Loaded(server, clientIp, DeviceInfo.localTime())
            },
            onFailure = { error ->
                DetailsUiState.Failed(error.message ?: error.javaClass.simpleName)
            },
        )
    }

    when (val current = state) {
        DetailsUiState.Loading -> LoadingContent(modifier = modifier)

        is DetailsUiState.Failed -> FailedContent(
            baseUrl = baseUrl,
            detail = current.message,
            onRetry = { reloadKey++ },
            modifier = modifier,
        )

        is DetailsUiState.Loaded -> InfoCard(
            state = current,
            user = session.user,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = ItemSpacing),
    ) {
        CircularProgressIndicator()
        Text(text = stringResource(R.string.status_loading))
    }
}

@Composable
private fun FailedContent(
    baseUrl: String,
    detail: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = ItemSpacing),
    ) {
        Text(
            text = stringResource(R.string.status_error, baseUrl),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(text = detail, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun InfoCard(
    state: DetailsUiState.Loaded,
    user: AuthenticatedUser,
    modifier: Modifier = Modifier,
) {
    val unavailable = stringResource(R.string.value_unavailable)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(all = CardPadding),
            verticalArrangement = Arrangement.spacedBy(space = RowSpacing),
        ) {
            InfoRow(
                label = stringResource(R.string.label_server_ip),
                value = state.server.ipAddress,
            )
            InfoRow(
                label = stringResource(R.string.label_client_ip),
                value = state.clientIp ?: unavailable,
            )

            HorizontalDivider()

            InfoRow(
                label = stringResource(R.string.label_server_time),
                value = state.server.localTime,
            )
            InfoRow(
                label = stringResource(R.string.label_client_time),
                value = state.clientTime,
            )

            HorizontalDivider()

            InfoRow(
                label = stringResource(R.string.label_developer_name),
                value = "${state.server.firstName} ${state.server.lastName}",
            )
            InfoRow(
                label = stringResource(R.string.label_signed_in_user),
                value = "${user.firstName} ${user.lastName}".trim()
                    .ifEmpty { unavailable },
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Opens Android's add-account screen, filtered to Google. Used when the device
 * has no account for Credential Manager to offer.
 */
private fun addGoogleAccountIntent(): Intent =
    Intent(Settings.ACTION_ADD_ACCOUNT)
        .putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
