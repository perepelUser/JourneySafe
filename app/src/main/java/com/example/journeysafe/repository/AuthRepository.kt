package com.example.journeysafe.repository

import android.util.Log
import com.example.journeysafe.data.User
import com.example.journeysafe.data.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "AuthRepository"

    suspend fun signUp(
        email: String,
        password: String,
        name: String,
        role: UserRole
    ): Result<Unit> = try {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        result.user?.let { firebaseUser ->
            val user = User(
                id = firebaseUser.uid,
                email = email,
                name = name,
                role = role
            )
            db.collection("users").document(firebaseUser.uid)
                .set(mapOf(
                    "email" to email,
                    "name" to name,
                    "role" to role.name
                )).await()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Error during sign up", e)
        Result.failure(e)
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = try {
        auth.signInWithEmailAndPassword(email, password).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Error during sign in", e)
        Result.failure(e)
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return try {
            val userDoc = db.collection("users").document(firebaseUser.uid).get().await()
            User(
                id = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                name = userDoc.getString("name") ?: "",
                role = UserRole.valueOf(userDoc.getString("role") ?: UserRole.PASSENGER.name)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user", e)
            null
        }
    }

    suspend fun updateUserProfile(user: User): Result<Unit> = try {
        db.collection("users").document(user.id)
            .set(mapOf(
                "email" to user.email,
                "name" to user.name,
                "role" to user.role.name
            )).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Error updating user profile", e)
        Result.failure(e)
    }
} 