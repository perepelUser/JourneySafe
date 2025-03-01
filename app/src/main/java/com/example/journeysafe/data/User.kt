package com.example.journeysafe.data

enum class UserRole {
    PASSENGER,
    DRIVER
}

data class User(
    val id: String,
    val email: String,
    val name: String,
    val role: UserRole
) 