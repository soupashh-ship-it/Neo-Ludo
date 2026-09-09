package com.neoludo.game.multiplayer.backend

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class FirebaseAuthDataSource {

    private val tag = "FirebaseAuthDataSource"

    private val auth: FirebaseAuth?
        get() = try {
            if (FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else null
        } catch (e: Throwable) {
            Log.w(tag, "Firebase Auth not available: ${e.message}")
            null
        }

    suspend fun ensureAuthenticated(fallbackUserId: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            return@withContext Result.failure(
                IllegalStateException("Firebase Authentication is not configured")
            )
        }

        try {
            val current = firebaseAuth.currentUser
            if (current != null) {
                return@withContext Result.success(current.uid)
            }

            val result = firebaseAuth.signInAnonymously().await()
            val user = result.user
            if (user != null) {
                Result.success(user.uid)
            } else {
                Result.failure(IllegalStateException("Firebase anonymous sign-in returned no user"))
            }
        } catch (e: Throwable) {
            // A local fallback id cannot satisfy RTDB rules that require
            // auth.uid, so pretending authentication succeeded only turns a
            // clear configuration problem into mysterious permission errors.
            Log.e(tag, "Anonymous sign-in failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun getCurrentUid(): String? {
        return try {
            auth?.currentUser?.uid
        } catch (e: Throwable) {
            null
        }
    }
}
