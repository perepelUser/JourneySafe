package com.example.journeysafe

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.journeysafe.ui.screens.*
import com.example.journeysafe.ui.theme.JourneySafeTheme
import com.example.journeysafe.viewmodel.AuthViewModel
import com.example.journeysafe.viewmodel.TaxiViewModel
import com.example.journeysafe.viewmodel.UserViewModel
import com.example.journeysafe.viewmodel.DriverViewModel
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JourneySafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route

                    val authViewModel: AuthViewModel = viewModel()
                    val taxiViewModel: TaxiViewModel = viewModel()
                    val userViewModel: UserViewModel = viewModel()
                    val driverViewModel: DriverViewModel = viewModel()

                    Scaffold(
                        bottomBar = {
                            if (currentRoute != "auth") {
                                val isDriver = remember {
                                    mutableStateOf(false)
                                }
                                
                                LaunchedEffect(FirebaseAuth.getInstance().currentUser) {
                                    val user = FirebaseAuth.getInstance().currentUser
                                    if (user != null) {
                                        userViewModel.checkUserRole(user.uid) { role ->
                                            isDriver.value = role == "DRIVER"
                                            // Если мы на неправильном экране, перенаправляем на правильный
                                            if (isDriver.value && currentRoute == "taxi") {
                                                navController.navigate("driver") {
                                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                                }
                                            } else if (!isDriver.value && currentRoute == "driver") {
                                                navController.navigate("taxi") {
                                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
            }
        }
    }
}

                                NavigationBar {
                                    if (isDriver.value) {
                                        // Навигация для водителя
                                        NavigationBarItem(
                                            icon = { Icon(Icons.Default.DirectionsCar, contentDescription = "Заказы") },
                                            label = { Text("Заказы") },
                                            selected = currentRoute == "driver",
                                            onClick = { 
                                                if (currentRoute != "driver") {
                                                    navController.navigate("driver") {
                                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                                        launchSingleTop = true
                                                    }
                                                }
                                            }
                                        )
                                    } else {
                                        // Навигация для пассажира
                                        NavigationBarItem(
                                            icon = { Icon(Icons.Default.DirectionsCar, contentDescription = "Такси") },
                                            label = { Text("Такси") },
                                            selected = currentRoute == "taxi",
                                            onClick = { 
                                                if (currentRoute != "taxi") {
                                                    navController.navigate("taxi") {
                                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                                        launchSingleTop = true
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Person, contentDescription = "Профиль") },
                                        label = { Text("Профиль") },
                                        selected = currentRoute == "profile",
                                        onClick = { 
                                            if (currentRoute != "profile") {
                                                navController.navigate("profile") {
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = "auth",
                            modifier = Modifier.padding(paddingValues)
                        ) {
                            composable("auth") {
                                AuthScreen(
                                    viewModel = authViewModel,
                                    onAuthSuccess = { isDriver ->
                                        val destination = if (isDriver) "driver" else "taxi"
                                        navController.navigate(destination) {
                                            popUpTo("auth") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable("taxi") {
                                TaxiOrderScreen(
                                    viewModel = taxiViewModel,
                                    userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                )
                            }

                            composable("profile") {
                                ProfileScreen(
                                    userViewModel = userViewModel,
                                    taxiViewModel = taxiViewModel,
                                    onSignOut = {
                                        Log.d("MainActivity", "Sign out clicked")
                                        FirebaseAuth.getInstance().signOut()
                                        Log.d("MainActivity", "Firebase sign out completed")
                                        navController.navigate("auth") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                        Log.d("MainActivity", "Navigation to auth completed")
                                    }
                                )
                            }

                            composable("driver") {
                                DriverScreen(
                                    viewModel = driverViewModel,
                                    driverId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}