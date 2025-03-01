package com.example.journeysafe.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import com.example.journeysafe.data.UserRole

@Composable
fun BottomBar(
    currentDestination: NavDestination?,
    onNavigateToTaxi: () -> Unit,
    onNavigateToDriver: () -> Unit,
    onNavigateToProfile: () -> Unit,
    userRole: UserRole?
) {
    NavigationBar {
        when (userRole) {
            UserRole.PASSENGER -> {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.LocalTaxi, contentDescription = "Заказ такси") },
                    label = { Text("Заказ") },
                    selected = currentDestination?.route == "taxi",
                    onClick = onNavigateToTaxi
                )
            }
            UserRole.DRIVER -> {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DirectionsCar, contentDescription = "Заказы") },
                    label = { Text("Заказы") },
                    selected = currentDestination?.route == "driver",
                    onClick = onNavigateToDriver
                )
            }
            null -> {}
        }

        NavigationBarItem(
            icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Профиль") },
            label = { Text("Профиль") },
            selected = currentDestination?.route == "profile",
            onClick = onNavigateToProfile
        )
    }
} 