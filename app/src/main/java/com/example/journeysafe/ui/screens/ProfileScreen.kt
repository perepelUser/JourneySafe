package com.example.journeysafe.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.journeysafe.data.User
import com.example.journeysafe.data.UserRole
import com.example.journeysafe.data.TaxiOrder
import com.example.journeysafe.data.OrderStatus
import com.example.journeysafe.ui.components.*
import com.example.journeysafe.viewmodel.AuthViewModel
import com.example.journeysafe.viewmodel.TaxiViewModel
import com.example.journeysafe.viewmodel.UserViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userViewModel: UserViewModel,
    taxiViewModel: TaxiViewModel,
    onSignOut: () -> Unit
) {
    var selectedOrder by remember { mutableStateOf<TaxiOrder?>(null) }
    val userOrders by taxiViewModel.userOrders.collectAsState(initial = emptyList())
    val currentUser = userViewModel.currentUser.value

    LaunchedEffect(currentUser) {
        currentUser?.uid?.let { userId ->
            taxiViewModel.loadUserOrders(userId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                actions = {
                    IconButton(
                        onClick = {
                            Log.d("ProfileScreen", "Sign out button clicked")
                            onSignOut()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Выйти"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Информация о пользователе
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Email: ${currentUser?.email ?: ""}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // История заказов
            item {
                Text(
                    text = "История заказов",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (userOrders.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "История заказов пуста",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(
                    items = userOrders.sortedByDescending { it.timestamp },
                    key = { it.id }
                ) { order ->
                    OrderCard(
                        order = order,
                        expanded = selectedOrder?.id == order.id,
                        onClick = { 
                            selectedOrder = if (selectedOrder?.id == order.id) null else order
                        },
                        onCancel = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoItem(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ProfileOrderHistoryItem(order: TaxiOrder) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Заказ #${order.id.take(8)}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = formatTimestamp(order.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Откуда: ${order.pickupLocation}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Куда: ${order.destination}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Цена: ${order.price} ₽",
                    style = MaterialTheme.typography.bodyMedium
                )
                StatusChip(status = order.status)
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return format.format(date)
} 