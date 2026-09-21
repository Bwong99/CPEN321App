package com.example.cpen321application.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * No Google account is present on the device, so Credential Manager has
 * nothing to offer. Recoverable: the screen points the user at Android's
 * add-account flow rather than leaving them stuck.
 */
class NoDeviceAccountException(cause: Throwable) : Exception(
    "No Google account on this device yet.",
    cause,
)

/**
 * Google sign-in via Credential Manager and Google Identity Services.
 *
 * Deliberately talks to Google directly rather than through Firebase
 * Authentication: the Project Description forbids third-party services such as
 * Firebase for major functionality, and authentication is named explicitly.
 */
object GoogleAuth {

    /**
     * Shows the Google account chooser and returns the chosen account's Google
     * ID token.
     *
     * Deliberately returns the token rather than the name Credential Manager
     * also hands back: anything read here has only the client's word behind
     * it. The name shown on screen comes from
     * [com.example.cpen321application.data.BackendClient.authenticate], which
     * is the server reporting what it verified this token to contain.
     *
     * @param activityContext must be an Activity context — Credential Manager
     *   needs one to present its UI.
     */
    suspend fun signIn(
        activityContext: Context,
        serverClientId: String,
    ): Result<String> {
        if (serverClientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "GOOGLE_CLIENT_ID is not set. Add it to frontend/local.properties."
                )
            )
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            // false so a first-time user can pick any account on the device,
            // not only ones that have already authorised this app.
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
            val credential = GoogleIdTokenCredential.createFrom(response.credential.data)

            Result.success(credential.idToken)
        } catch (e: NoCredentialException) {
            // Raised when the device has no Google account at all, which is
            // the usual state of a freshly created emulator. Reported as its
            // own type so the screen can offer to open the add-account flow.
            Result.failure(NoDeviceAccountException(e))
        } catch (e: GetCredentialException) {
            // Covers cancellation, a misconfigured client ID, and Play
            // Services problems; the message is surfaced to the user as-is.
            Result.failure(e)
        }
    }
}
