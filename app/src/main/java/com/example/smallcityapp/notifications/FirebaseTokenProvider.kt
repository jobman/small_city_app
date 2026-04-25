package com.example.smallcityapp.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseTokenProvider(
    context: Context,
) {
    private val pushStore = LocalPushStore(context)
    private val appContext = context.applicationContext

    suspend fun getToken(): Result<String> {
        pushStore.getSavedToken()?.takeIf { it.isNotBlank() }?.let { savedToken ->
            return Result.success(savedToken)
        }

        if (!isFirebaseConfigured()) {
            return Result.failure(
                IllegalStateException(
                    "Firebase ще не підключений. Додай google-services.json у app/ і синхронізуй проект.",
                ),
            )
        }

        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    pushStore.saveToken(token)
                    if (continuation.isActive) {
                        continuation.resume(Result.success(token))
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(error))
                    }
                }
        }
    }

    private fun isFirebaseConfigured(): Boolean {
        return runCatching {
            FirebaseApp.getApps(appContext).isNotEmpty()
        }.getOrDefault(false)
    }
}
