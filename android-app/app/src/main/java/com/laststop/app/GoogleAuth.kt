package com.laststop.app

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

data class SignedInUser(val displayName: String, val email: String, val photoUrl: String?)

sealed interface SignInResult {
    data class Success(val user: SignedInUser) : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val message: String) : SignInResult
}

object GoogleAuth {
    suspend fun signIn(context: Context): SignInResult {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank() || clientId == "DEFAULT_CLIENT_ID") {
            return SignInResult.Failed("Google Sign-In isn't configured yet.")
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                SignInResult.Success(
                    SignedInUser(
                        displayName = googleIdTokenCredential.displayName ?: googleIdTokenCredential.id,
                        email = googleIdTokenCredential.id,
                        photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                    )
                )
            } else {
                SignInResult.Failed("Unexpected credential type from Google.")
            }
        } catch (e: GetCredentialException) {
            if (e.type.contains("Cancel", ignoreCase = true) || e.type.contains("NoCredential", ignoreCase = true)) {
                SignInResult.Cancelled
            } else {
                SignInResult.Failed(e.message ?: "Google Sign-In failed.")
            }
        }
    }
}
