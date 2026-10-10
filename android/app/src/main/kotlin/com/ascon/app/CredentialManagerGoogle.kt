package com.ascon.app

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.ascon.core.data.GoogleAccounts
import com.ascon.core.data.GoogleCredential
import com.ascon.core.data.GoogleUnreachable
import com.ascon.core.data.NoGoogleAccount
import com.ascon.core.data.SignInCancelled
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Sign in with Google through Credential Manager. [serverClientId] is the OAuth Web client
 * ID: Google issues the ID token to it, and the backend checks that it did. The Android
 * client, matched by package name and signing certificate, needs no ID here.
 */
internal class CredentialManagerGoogle(private val serverClientId: String) : GoogleAccounts {
    override suspend fun pick(context: Context): GoogleCredential {
        // The Sign in with Google option always shows the account picker, as a button should.
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        val credential = try {
            CredentialManager.create(context).getCredential(context, request).credential
        } catch (e: GetCredentialException) {
            throw e.asSignInFailure()
        }
        val google = credential.asGoogleIdToken()
        return GoogleCredential(google.idToken, google.displayName, google.id)
    }

    private fun GetCredentialException.asSignInFailure(): Exception = when (this) {
        is GetCredentialCancellationException -> SignInCancelled()
        is NoCredentialException -> NoGoogleAccount()
        else -> GoogleUnreachable(this)
    }

    private fun Credential.asGoogleIdToken(): GoogleIdTokenCredential {
        if (this !is CustomCredential || type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw GoogleUnreachable()
        }
        return try {
            GoogleIdTokenCredential.createFrom(data)
        } catch (e: GoogleIdTokenParsingException) {
            throw GoogleUnreachable(e)
        }
    }
}
