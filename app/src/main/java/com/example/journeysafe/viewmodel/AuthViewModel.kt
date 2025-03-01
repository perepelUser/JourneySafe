package com.example.journeysafe.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.journeysafe.data.User
import com.example.journeysafe.data.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "AuthViewModel"

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    init {
        auth.currentUser?.let { firebaseUser ->
            viewModelScope.launch {
                try {
                    val userDoc = db.collection("users").document(firebaseUser.uid).get().await()
                    val user = User(
                        id = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        name = userDoc.getString("name") ?: "",
                        role = UserRole.valueOf(userDoc.getString("role") ?: UserRole.PASSENGER.name)
                    )
                    _user.value = user
                    _authState.value = AuthState.Success(user)
                } catch (e: Exception) {
                    _authState.value = AuthState.Error(e.message ?: "Ошибка загрузки данных пользователя")
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user?.let { firebaseUser ->
                    val userDoc = db.collection("users").document(firebaseUser.uid).get().await()
                    val user = User(
                        id = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        name = userDoc.getString("name") ?: "",
                        role = UserRole.valueOf(userDoc.getString("role") ?: UserRole.PASSENGER.name)
                    )
                    _user.value = user
                    _authState.value = AuthState.Success(user)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Ошибка входа")
            }
        }
    }

    fun register(email: String, password: String, name: String, role: UserRole) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
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
                    
                    _user.value = user
                    _authState.value = AuthState.Success(user)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Ошибка регистрации")
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
        _authState.value = AuthState.Initial
    }

    fun updateProfile(updatedUser: User) {
        viewModelScope.launch {
            try {
                db.collection("users").document(updatedUser.id)
                    .set(mapOf(
                        "email" to updatedUser.email,
                        "name" to updatedUser.name,
                        "role" to updatedUser.role.name
                    )).await()
                _user.value = updatedUser
            } catch (e: Exception) {
                Log.e(TAG, "Error updating profile", e)
            }
        }
    }

    sealed class AuthState {
        object Initial : AuthState()
        object Loading : AuthState()
        data class Success(val user: User) : AuthState()
        data class Error(val message: String) : AuthState()
    }
} 