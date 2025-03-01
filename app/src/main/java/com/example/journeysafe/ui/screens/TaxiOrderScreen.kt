package com.example.journeysafe.ui.screens

import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.journeysafe.data.OrderStatus
import com.example.journeysafe.data.TaxiOrder
import com.example.journeysafe.ui.components.*
import com.example.journeysafe.viewmodel.OrderState
import com.example.journeysafe.viewmodel.TaxiViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.bonuspack.routing.Road
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxiOrderScreen(
    viewModel: TaxiViewModel,
    userId: String
) {
    var pickupLocation by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var driverComment by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<Calendar?>(null) }
    var selectedTime by remember { mutableStateOf<Calendar?>(null) }
    var selectedOrder by remember { mutableStateOf<TaxiOrder?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val orderState by viewModel.orderState.collectAsState()
    val currentOrder by viewModel.currentOrder.collectAsState()
    val userOrders by viewModel.userOrders.collectAsState()
    
    val startPoint by viewModel.startPoint.collectAsState()
    val endPoint by viewModel.endPoint.collectAsState()
    val route by viewModel.route.collectAsState()
    
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val geocoder = remember { Geocoder(context) }
    
    var mapView by remember { mutableStateOf<MapView?>(null) }
    
    // Load user orders when screen is launched
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            viewModel.loadUserOrders(userId)
        }
    }
    
    // Update text fields when points change
    LaunchedEffect(startPoint) {
        startPoint?.let { point ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(point.latitude, point.longitude, 1) { addresses ->
                        if (addresses.isNotEmpty()) {
                            val address = addresses[0]
                            val addressText = buildString {
                                address.thoroughfare?.let { append(it) }
                                address.subThoroughfare?.let { append(", ").append(it) }
                                address.locality?.let { append(", ").append(it) }
                            }
                            pickupLocation = addressText
                        } else {
                            pickupLocation = "%.6f, %.6f".format(point.latitude, point.longitude)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val addressText = buildString {
                            address.thoroughfare?.let { append(it) }
                            address.subThoroughfare?.let { append(", ").append(it) }
                            address.locality?.let { append(", ").append(it) }
                        }
                        pickupLocation = addressText
                    } else {
                        pickupLocation = "%.6f, %.6f".format(point.latitude, point.longitude)
                    }
                }
            } catch (e: Exception) {
                pickupLocation = "%.6f, %.6f".format(point.latitude, point.longitude)
            }
        }
    }
    
    LaunchedEffect(endPoint) {
        endPoint?.let { point ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(point.latitude, point.longitude, 1) { addresses ->
                        if (addresses.isNotEmpty()) {
                            val address = addresses[0]
                            val addressText = buildString {
                                address.thoroughfare?.let { append(it) }
                                address.subThoroughfare?.let { append(", ").append(it) }
                                address.locality?.let { append(", ").append(it) }
                            }
                            destination = addressText
                        } else {
                            destination = "%.6f, %.6f".format(point.latitude, point.longitude)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val addressText = buildString {
                            address.thoroughfare?.let { append(it) }
                            address.subThoroughfare?.let { append(", ").append(it) }
                            address.locality?.let { append(", ").append(it) }
                        }
                        destination = addressText
                    } else {
                        destination = "%.6f, %.6f".format(point.latitude, point.longitude)
                    }
                }
            } catch (e: Exception) {
                destination = "%.6f, %.6f".format(point.latitude, point.longitude)
            }
        }
    }
    
    // Initialize OSMDroid and ViewModel
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        viewModel.initialize(context)
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate?.timeInMillis
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate = datePickerState.selectedDateMillis?.let { Calendar.getInstance().apply { timeInMillis = it } }
                    showDatePicker = false
                    showTimePicker = true
                }) {
                    Text("Далее")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState()
        
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Выберите время") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val calendar = selectedDate?.clone() as Calendar?
                    calendar?.apply { 
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    selectedTime = calendar
                    showTimePicker = false
                }) {
                    Text("ОК")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заказ такси") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Map view
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(300.dp)
                ) {
                    AndroidView(
                        factory = { context ->
                            MapView(context).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                controller.setZoom(15.0)
                                controller.setCenter(GeoPoint(55.7558, 37.6173))
                                setMultiTouchControls(true)
                                
                                setOnTouchListener { view, event ->
                                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                                        val projection = view as MapView
                                        val geoPoint = projection.getProjection()
                                            .fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                                        
                                        if (startPoint == null) {
                                            viewModel.setStartPoint(geoPoint)
                                        } else if (endPoint == null) {
                                            viewModel.setEndPoint(geoPoint)
                                        }
                                    }
                                    false
                                }
                                mapView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.overlays.clear()
                            
                            startPoint?.let { point ->
                                val marker = Marker(view)
                                marker.position = point
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                marker.title = "Точка отправления"
                                view.overlays.add(marker)
                            }
                            
                            endPoint?.let { point ->
                                val marker = Marker(view)
                                marker.position = point
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                marker.title = "Точка назначения"
                                view.overlays.add(marker)
                            }
                            
                            route?.let { road ->
                                val routeOverlay = Polyline()
                                routeOverlay.setPoints(road.mRouteHigh)
                                routeOverlay.color = android.graphics.Color.BLUE
                                routeOverlay.width = 5.0f
                                view.overlays.add(routeOverlay)
                            }
                            
                            view.invalidate()
                        }
                    )
                }
            }

            // Instructions
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = when {
                                startPoint == null -> "Нажмите на карту, чтобы выбрать точку отправления"
                                endPoint == null -> "Нажмите на карту, чтобы выбрать точку назначения"
                                else -> "Маршрут построен"
                            }
                        )
                        
                        if (startPoint != null || endPoint != null) {
                            Button(
                                onClick = {
                                    viewModel.clearRoute()
                                    mapView?.overlays?.clear()
                                    mapView?.invalidate()
                                    pickupLocation = ""
                                    destination = ""
                                    selectedDate = null
                                    selectedTime = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text("Очистить маршрут")
                            }
                        }
                    }
                }
            }

            // Order form
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    val state = orderState
                    when (state) {
                        OrderState.Initial, OrderState.Success -> OrderFormContent(
                            startPoint = startPoint,
                            endPoint = endPoint,
                            pickupAddress = pickupLocation,
                            destinationAddress = destination,
                            driverComment = driverComment,
                            onPickupLocationChange = { pickupLocation = it },
                            onDestinationChange = { destination = it },
                            onDriverCommentChange = { driverComment = it },
                            onCreateOrder = {
                                isLoading = true
                                viewModel.createOrder(
                                    pickupLocation = pickupLocation,
                                    destination = destination,
                                    driverComment = driverComment,
                                    price = calculatePrice(pickupLocation, destination),
                                    scheduledTime = selectedDate?.timeInMillis
                                )
                                // Очищаем форму после создания заказа
                                viewModel.clearRoute()
                                mapView?.overlays?.clear()
                                mapView?.invalidate()
                                pickupLocation = ""
                                destination = ""
                                driverComment = ""
                                selectedDate = null
                                selectedTime = null
                                isLoading = false
                            },
                            isLoading = isLoading,
                            selectedDate = selectedDate,
                            selectedTime = selectedTime,
                            onShowDatePicker = { showDatePicker = true },
                            onShowTimePicker = { showTimePicker = true }
                        )
                        OrderState.Loading -> LoadingSpinner()
                        is OrderState.Error -> {
                            Column {
                                ErrorMessage(state.message)
                                OrderFormContent(
                                    startPoint = startPoint,
                                    endPoint = endPoint,
                                    pickupAddress = pickupLocation,
                                    destinationAddress = destination,
                                    driverComment = driverComment,
                                    onPickupLocationChange = { pickupLocation = it },
                                    onDestinationChange = { destination = it },
                                    onDriverCommentChange = { driverComment = it },
                                    onCreateOrder = {
                                        isLoading = true
                                        viewModel.createOrder(
                                            pickupLocation = pickupLocation,
                                            destination = destination,
                                            driverComment = driverComment,
                                            price = calculatePrice(pickupLocation, destination),
                                            scheduledTime = selectedDate?.timeInMillis
                                        )
                                        // Очищаем форму после создания заказа
                                        viewModel.clearRoute()
                                        mapView?.overlays?.clear()
                                        mapView?.invalidate()
                                        pickupLocation = ""
                                        destination = ""
                                        driverComment = ""
                                        selectedDate = null
                                        selectedTime = null
                                        isLoading = false
                                    },
                                    isLoading = isLoading,
                                    selectedDate = selectedDate,
                                    selectedTime = selectedTime,
                                    onShowDatePicker = { showDatePicker = true },
                                    onShowTimePicker = { showTimePicker = true }
                                )
                            }
                        }
                    }
                }
            }

            // Current orders
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Ваши заказы",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (userOrders.isEmpty()) {
                        Text(
                            text = "У вас пока нет заказов",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            items(userOrders) { order ->
                                Card(
                                    modifier = Modifier
                                        .width(300.dp)
                                        .animateContentSize()
                                ) {
                                    OrderCard(
                                        order = order,
                                        onCancelOrder = { orderId ->
                                            viewModel.cancelOrder(orderId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderFormContent(
    startPoint: GeoPoint?,
    endPoint: GeoPoint?,
    pickupAddress: String,
    destinationAddress: String,
    driverComment: String,
    onPickupLocationChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onDriverCommentChange: (String) -> Unit,
    onCreateOrder: () -> Unit,
    isLoading: Boolean,
    selectedDate: Calendar?,
    selectedTime: Calendar?,
    onShowDatePicker: () -> Unit,
    onShowTimePicker: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        CustomTextField(
            value = pickupAddress,
            onValueChange = onPickupLocationChange,
            label = "Точка отправления",
            modifier = Modifier.padding(bottom = 16.dp),
            readOnly = true
        )

        CustomTextField(
            value = destinationAddress,
            onValueChange = onDestinationChange,
            label = "Точка назначения",
            modifier = Modifier.padding(bottom = 16.dp),
            readOnly = true
        )

        OutlinedTextField(
            value = driverComment,
            onValueChange = onDriverCommentChange,
            label = { Text("Комментарий водителю") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Comment,
                    contentDescription = "Комментарий"
                )
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedDate != null) {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Запланировано на: ${dateFormat.format(selectedDate.time)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    IconButton(onClick = { onShowDatePicker() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Очистить время"
                        )
                    }
                }
            } else {
                Button(
                    onClick = { onShowDatePicker() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Запланировать"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Запланировать поездку")
                }
            }
        }

        Text(
            text = "Стоимость поездки: ${calculatePrice(pickupAddress, destinationAddress).toInt()} ₽",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Button(
            onClick = onCreateOrder,
            modifier = Modifier.fillMaxWidth(),
            enabled = startPoint != null && endPoint != null && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Заказать такси")
            }
        }
    }
}

@Composable
fun LoadingSpinner() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorMessage(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

fun calculatePrice(pickupLocation: String, destination: String): Double {
    // Базовая цена 100 рублей + случайная сумма от 50 до 200 рублей
    return (100.0 + (50..200).random()).roundToInt().toDouble()
}

fun calculateDistance(start: GeoPoint, end: GeoPoint): Double {
    val earthRadius = 6371.0 // Радиус Земли в километрах
    
    val lat1 = Math.toRadians(start.latitude)
    val lat2 = Math.toRadians(end.latitude)
    val lon1 = Math.toRadians(start.longitude)
    val lon2 = Math.toRadians(end.longitude)
    
    val dLat = lat2 - lat1
    val dLon = lon2 - lon1
    
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    
    return earthRadius * c
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        readOnly = readOnly,
        singleLine = true
    )
}

@Composable
fun OrderCard(
    order: TaxiOrder,
    onCancelOrder: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Заказ #${order.id.takeLast(4)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatTimestamp(order.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = order.status)
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Откуда: ${order.pickupLocation}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Куда: ${order.destination}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (order.driverComment.isNotEmpty()) {
                    Text(
                        text = "Комментарий: ${order.driverComment}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                order.scheduledTime?.let { time ->
                    Text(
                        text = "Запланировано на: ${formatTimestamp(time)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = "Стоимость: ${order.price} ₽",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (order.status == OrderStatus.PENDING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onCancelOrder(order.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Отменить заказ")
                    }
                }
            }
            
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть"
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return format.format(date)
} 