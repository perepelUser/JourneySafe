package com.example.journeysafe.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.journeysafe.ui.screens.DriverScreen
import com.example.journeysafe.ui.screens.TaxiOrderScreen
import com.example.journeysafe.viewmodel.DriverViewModel
import com.example.journeysafe.viewmodel.TaxiViewModel

sealed class Screen(val route: String) {
    object TaxiOrder : Screen("taxi_order")
    object Driver : Screen("driver")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    taxiViewModel: TaxiViewModel,
    driverViewModel: DriverViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Driver.route // Временно изменим на экран водителя для тестирования
    ) {
        composable(Screen.TaxiOrder.route) {
            TaxiOrderScreen(
                viewModel = taxiViewModel,
                userId = "test_user_id"
            )
        }
        composable(Screen.Driver.route) {
            DriverScreen(
                viewModel = driverViewModel,
                driverId = "test_driver_id"
            )
        }
    }
} 