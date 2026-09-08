package com.neoludo.game.multiplayer.backend

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

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
            val id = fallbackUserId.ifBlank { "anon_" + UUID.randomUUID().toString().take(8) }
            return@withContext Result.success(id)
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
                val fallbackId = fallbackUserId.ifBlank { "anon_" + UUID.randomUUID().toString().take(8) }
                Result.success(fallbackId)
            }
        } catch (e: Throwable) {
            Log.e(tag, "Anonymous sign-in failed, using session id: ${e.message}")
            val fallbackId = fallbackUserId.ifBlank { "anon_" + UUID.randomUUID().toString().take(8) }
            Result.success(fallbackId)
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
