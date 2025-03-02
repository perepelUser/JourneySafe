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
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

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
    var acceptError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadDriverStats(driverId)
    }

    if (showMap && selectedOrder != null) {
        AlertDialog(
            onDismissRequest = { 
                showMap = false
                selectedOrder = null
                acceptError = null
            },
            title = { Text("Маршрут заказа #${selectedOrder?.id?.take(8)}") },
            text = {
                Column {
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
                    if (acceptError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = acceptError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                selectedOrder?.let { viewModel.acceptOrder(it.id) }
                                showMap = false
                                selectedOrder = null
                                acceptError = null
                            } catch (e: Exception) {
                                acceptError = e.message ?: "Ошибка при принятии заказа"
                            }
                        }
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
                        acceptError = null
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
    var startPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var endPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(order) {
        isLoading = true
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val geocoder = Geocoder(context, Locale("ru", "RU"))
                
                // Форматируем адреса для лучшего геокодирования
                val formattedPickupLocation = "${order.pickupLocation}, Москва"
                val formattedDestination = "${order.destination}, Москва"
                
                Log.d("DriverScreen", "Geocoding start: $formattedPickupLocation")
                startPoint = parseLocation(formattedPickupLocation, geocoder)
                
                Log.d("DriverScreen", "Geocoding end: $formattedDestination")
                endPoint = parseLocation(formattedDestination, geocoder)
            }
        } catch (e: Exception) {
            Log.e("DriverScreen", "Error during geocoding", e)
        } finally {
            isLoading = false
        }
    }
    
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    if (startPoint == null || endPoint == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Не удалось определить координаты адресов")
        }
        return
    }
    
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                setTileSource(TileSourceFactory.MAPNIK)
                controller.setZoom(13.0)
                setMultiTouchControls(true)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { mapView ->
            mapView.overlays.clear()

            val startMarker = Marker(mapView).apply {
                position = startPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Точка отправления"
                snippet = order.pickupLocation
                icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)
            }
            mapView.overlays.add(startMarker)

            val endMarker = Marker(mapView).apply {
                position = endPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Точка назначения"
                snippet = order.destination
                icon = context.getDrawable(android.R.drawable.ic_menu_myplaces)
            }
            mapView.overlays.add(endMarker)

            val routeLine = Polyline(mapView).apply {
                outlinePaint.color = android.graphics.Color.BLUE
                outlinePaint.strokeWidth = 5f
                addPoint(startPoint)
                addPoint(endPoint)
            }
            mapView.overlays.add(routeLine)

            val boundingBox = routeLine.bounds
            mapView.zoomToBoundingBox(boundingBox, true, 100)
            
            mapView.invalidate()
        }
    )
}

private fun parseLocation(address: String, geocoder: Geocoder): GeoPoint? {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val addresses = geocoder.getFromLocationName(address, 1)
            if (!addresses.isNullOrEmpty()) {
                val location = addresses[0]
                Log.d("DriverScreen", "Found location for address: $address -> ${location.latitude}, ${location.longitude}")
                Log.d("DriverScreen", "Full address: ${location.getAddressLine(0)}")
                GeoPoint(location.latitude, location.longitude)
            } else {
                Log.w("DriverScreen", "Address not found: $address")
                null
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(address, 1)
            if (!addresses.isNullOrEmpty()) {
                val location = addresses[0]
                Log.d("DriverScreen", "Found location for address: $address -> ${location.latitude}, ${location.longitude}")
                Log.d("DriverScreen", "Full address: ${location.getAddressLine(0)}")
                GeoPoint(location.latitude, location.longitude)
            } else {
                Log.w("DriverScreen", "Address not found: $address")
                null
            }
        }
    } catch (e: Exception) {
        Log.e("DriverScreen", "Error geocoding address: $address", e)
        null
    }
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