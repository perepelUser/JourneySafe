package com.example.journeysafe.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class UserViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    
    private val _currentUser = mutableStateOf<FirebaseUser?>(auth.currentUser)
    val currentUser: State<FirebaseUser?> = _currentUser

    init {
        // Слушаем изменения состояния аутентификации
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    fun checkUserRole(userId: String, onResult: (String) -> Unit) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val role = document.getString("role") ?: "PASSENGER"
                    onResult(role)
                } else {
                    onResult("PASSENGER")
                }
            }
            .addOnFailureListener {
                onResult("PASSENGER")
            }
    }

    fun signOut() {
        auth.signOut()
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener { }
    }
} 