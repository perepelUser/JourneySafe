package com.example.journeysafe.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.journeysafe.data.OrderStatus
import com.example.journeysafe.data.TaxiOrder
import com.example.journeysafe.viewmodel.DriverViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverScreen(
    viewModel: DriverViewModel,
    driverId: String
) {
    val completedOrders by viewModel.completedOrders.collectAsState()
    val totalEarnings by viewModel.totalEarnings.collectAsState()
    val availableOrders by viewModel.availableOrders.collectAsState()
    val activeOrder by viewModel.activeOrder.collectAsState()
    var selectedOrder by remember { mutableStateOf<TaxiOrder?>(null) }
    var showMap by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadDriverStats(driverId)
    }

    if (showMap && selectedOrder != null) {
        AlertDialog(
            onDismissRequest = { 
                showMap = false
                selectedOrder = null
            },
            title = { Text("Маршрут заказа #${selectedOrder?.id?.take(8)}") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    MapView(
                        order = selectedOrder!!,
                        context = context
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedOrder?.let { viewModel.acceptOrder(it.id) }
                        showMap = false
                        selectedOrder = null
                    }
                ) {
                    Text("Принять заказ")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showMap = false
                        selectedOrder = null
                    }
                ) {
                    Text("Закрыть")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль водителя") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Statistics Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatisticCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CheckCircle,
                    title = "Выполнено заказов",
                    value = completedOrders.toString()
                )
                StatisticCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.AttachMoney,
                    title = "Заработано",
                    value = "${totalEarnings} ₽"
                )
            }

            // Active Order Section
            if (activeOrder != null) {
                Text(
                    text = "Текущий заказ",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ActiveOrderCard(
                    order = activeOrder!!,
                    onComplete = { viewModel.completeOrder(it) },
                    onShowRoute = { 
                        selectedOrder = activeOrder
                        showMap = true
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Available Orders Section
            Text(
                text = "Доступные заказы",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableOrders) { order ->
                    AvailableOrderCard(
                        order = order,
                        onAccept = { viewModel.acceptOrder(it) },
                        onShowRoute = {
                            selectedOrder = order
                            showMap = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MapView(
    order: TaxiOrder,
    context: Context
) {
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                setTileSource(TileSourceFactory.MAPNIK)
                controller.setZoom(15.0)
                setMultiTouchControls(true)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { mapView ->
            // Очищаем предыдущие маркеры
            mapView.overlays.clear()

            // Добавляем маркер точки отправления
            val startPoint = parseLocation(order.pickupLocation)
            val startMarker = Marker(mapView).apply {
                position = startPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Точка отправления"
                icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)
            }
            mapView.overlays.add(startMarker)

            // Добавляем маркер точки назначения
            val endPoint = parseLocation(order.destination)
            val endMarker = Marker(mapView).apply {
                position = endPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Точка назначения"
                icon = context.getDrawable(android.R.drawable.ic_menu_myplaces)
            }
            mapView.overlays.add(endMarker)

            // Центрируем карту на середине маршрута
            val centerPoint = GeoPoint(
                (startPoint.latitude + endPoint.latitude) / 2,
                (startPoint.longitude + endPoint.longitude) / 2
            )
            mapView.controller.setCenter(centerPoint)

            // Добавляем линию маршрута
            val routeLine = Polyline().apply {
                addPoint(startPoint)
                addPoint(endPoint)
                color = android.graphics.Color.BLUE
                width = 5.0f
            }
            mapView.overlays.add(routeLine)

            mapView.invalidate()
        }
    )
}

private fun parseLocation(location: String): GeoPoint {
    // В реальном приложении здесь будет парсинг реальных координат
    // Сейчас возвращаем тестовые координаты Москвы
    return GeoPoint(55.7558, 37.6173)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String
) {
    ElevatedCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveOrderCard(
    order: TaxiOrder,
    onComplete: (String) -> Unit,
    onShowRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
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
                    text = "#${order.id.take(8)}",
                    style = MaterialTheme.typography.titleSmall
                )
                StatusChip(status = order.status)
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
            
            if (order.driverComment.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Комментарий: ${order.driverComment}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            if (order.scheduledTime != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Запланировано на: ${formatTimestamp(order.scheduledTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onShowRoute) {
                        Text("Маршрут")
                    }
                    Button(onClick = { onComplete(order.id) }) {
                        Text("Завершить")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvailableOrderCard(
    order: TaxiOrder,
    onAccept: (String) -> Unit,
    onShowRoute: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
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
                    text = "#${order.id.take(8)}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (order.scheduledTime != null) {
                    Text(
                        text = formatTimestamp(order.scheduledTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
            
            if (order.driverComment.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Комментарий: ${order.driverComment}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onShowRoute) {
                        Text("Маршрут")
                    }
                    Button(onClick = { onAccept(order.id) }) {
                        Text("Принять")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: OrderStatus) {
    val (color, text) = when (status) {
        OrderStatus.PENDING -> MaterialTheme.colorScheme.primary to "Ожидание"
        OrderStatus.ACCEPTED -> MaterialTheme.colorScheme.secondary to "Принят"
        OrderStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary to "В пути"
        OrderStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant to "Завершен"
        OrderStatus.CANCELLED -> MaterialTheme.colorScheme.error to "Отменен"
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return format.format(date)
} 