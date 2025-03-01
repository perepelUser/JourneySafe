package com.example.journeysafe.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.journeysafe.data.TaxiOrder
import com.example.journeysafe.data.OrderStatus
import com.example.journeysafe.repository.TaxiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class TaxiViewModel : ViewModel() {
    private val TAG = "TaxiViewModel"
    private val repository = TaxiRepository()
    private var applicationContext: Context? = null
    private val db = FirebaseFirestore.getInstance()

    private val _orderState = MutableStateFlow<OrderState>(OrderState.Initial)
    val orderState: StateFlow<OrderState> = _orderState

    private val _currentOrder = MutableStateFlow<TaxiOrder?>(null)
    val currentOrder: StateFlow<TaxiOrder?> = _currentOrder

    private val _userOrders = MutableStateFlow<List<TaxiOrder>>(emptyList())
    val userOrders: StateFlow<List<TaxiOrder>> = _userOrders

    // Map state
    private val _startPoint = MutableStateFlow<GeoPoint?>(null)
    val startPoint: StateFlow<GeoPoint?> = _startPoint

    private val _endPoint = MutableStateFlow<GeoPoint?>(null)
    val endPoint: StateFlow<GeoPoint?> = _endPoint

    private val _route = MutableStateFlow<Road?>(null)
    val route: StateFlow<Road?> = _route

    private val _orderHistory = MutableStateFlow<List<TaxiOrder>>(emptyList())
    val orderHistory: StateFlow<List<TaxiOrder>> = _orderHistory

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        startCurrentOrderListener()
    }

    private fun startCurrentOrderListener() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.e(TAG, "No current user found")
            return
        }

        db.collection("orders")
            .whereEqualTo("userId", currentUser.uid)
            .whereIn("status", listOf(
                OrderStatus.PENDING.name,
                OrderStatus.ACCEPTED.name,
                OrderStatus.IN_PROGRESS.name
            ))
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening to current order", e)
                    return@addSnapshotListener
                }

                val orders = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        TaxiOrder(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            pickupLocation = doc.getString("pickupLocation") ?: "",
                            destination = doc.getString("destination") ?: "",
                            driverComment = doc.getString("driverComment") ?: "",
                            price = doc.getDouble("price") ?: 0.0,
                            status = OrderStatus.valueOf(doc.getString("status") ?: OrderStatus.PENDING.name),
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            scheduledTime = doc.getLong("scheduledTime"),
                            driverId = doc.getString("driverId")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing current order", e)
                        null
                    }
                } ?: emptyList()

                _currentOrder.value = orders.firstOrNull()
                Log.d(TAG, "Current order updated: ${_currentOrder.value}")
            }
    }

    fun setStartPoint(point: GeoPoint) {
        _startPoint.value = point
        calculateRoute()
    }

    fun setEndPoint(point: GeoPoint) {
        _endPoint.value = point
        calculateRoute()
    }

    fun clearRoute() {
        _startPoint.value = null
        _endPoint.value = null
        _route.value = null
    }

    private fun calculateRoute() {
        val start = _startPoint.value
        val end = _endPoint.value
        val context = applicationContext

        if (start != null && end != null && context != null) {
            viewModelScope.launch {
                try {
                    val road = withContext(Dispatchers.IO) {
                        val roadManager = OSRMRoadManager(context, "JourneySafe")
                        val waypoints = ArrayList<GeoPoint>()
                        waypoints.add(start)
                        waypoints.add(end)
                        roadManager.getRoad(waypoints)
                    }
                    _route.value = road
                } catch (e: Exception) {
                    // Handle route calculation error
                    _route.value = null
                }
            }
        }
    }

    fun createOrder(
        pickupLocation: String,
        destination: String,
        driverComment: String = "",
        price: Double,
        scheduledTime: Long? = null
    ) {
        viewModelScope.launch {
            try {
                _orderState.value = OrderState.Loading
                Log.d(TAG, "Starting order creation process...")

                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    Log.e(TAG, "No current user found")
                    _orderState.value = OrderState.Error("Пользователь не авторизован")
                    return@launch
                }
                Log.d(TAG, "Current user ID: ${currentUser.uid}")

                val orderData = hashMapOf(
                    "userId" to currentUser.uid,
                    "pickupLocation" to pickupLocation,
                    "destination" to destination,
                    "driverComment" to driverComment,
                    "price" to price,
                    "status" to OrderStatus.PENDING.name,
                    "timestamp" to System.currentTimeMillis(),
                    "scheduledTime" to scheduledTime,
                    "driverId" to null
                )
                Log.d(TAG, "Prepared order data: $orderData")

                try {
                    val docRef = db.collection("orders").document()
                    Log.d(TAG, "Created document reference with ID: ${docRef.id}")
                    
                    docRef.set(orderData).await()
                    Log.d(TAG, "Successfully saved order to Firestore")

                    val order = TaxiOrder(
                        id = docRef.id,
                        userId = currentUser.uid,
                        pickupLocation = pickupLocation,
                        destination = destination,
                        driverComment = driverComment,
                        price = price,
                        status = OrderStatus.PENDING,
                        timestamp = System.currentTimeMillis(),
                        scheduledTime = scheduledTime,
                        driverId = null
                    )

                    _currentOrder.value = order
                    loadUserOrders(currentUser.uid)
                    _orderState.value = OrderState.Success
                    Log.d(TAG, "Order creation completed successfully: $order")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving order to Firestore", e)
                    _orderState.value = OrderState.Error("Ошибка при сохранении заказа: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating order", e)
                _orderState.value = OrderState.Error("Ошибка при создании заказа: ${e.message}")
            }
        }
    }

    private fun calculatePrice(): Double {
        // Простой расчет цены на основе расстояния маршрута
        return route.value?.let { road ->
            // Базовая цена 100 рублей + 30 рублей за километр
            100.0 + (road.mLength * 30.0)
        } ?: 100.0 // Если маршрут не построен, возвращаем базовую цену
    }

    fun loadUserOrders(userId: String) {
        Log.d(TAG, "Starting to load orders for user: $userId")
        viewModelScope.launch {
            try {
                val snapshot = db.collection("orders")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                val orders = snapshot.documents.mapNotNull { doc ->
                    try {
                        TaxiOrder(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            pickupLocation = doc.getString("pickupLocation") ?: "",
                            destination = doc.getString("destination") ?: "",
                            driverComment = doc.getString("driverComment") ?: "",
                            price = doc.getDouble("price") ?: 0.0,
                            status = OrderStatus.valueOf(doc.getString("status") ?: OrderStatus.PENDING.name),
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            scheduledTime = doc.getLong("scheduledTime"),
                            driverId = doc.getString("driverId")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing order document", e)
                        null
                    }
                }

                Log.d(TAG, "Successfully loaded ${orders.size} orders")
                orders.forEach { order ->
                    Log.d(TAG, "Order details - ID: ${order.id}, Status: ${order.status}, Price: ${order.price}, Timestamp: ${formatTimestamp(order.timestamp)}")
                }
                _userOrders.value = orders
            } catch (e: Exception) {
                Log.e(TAG, "Error loading orders: ${e.message}", e)
                _userOrders.value = emptyList()
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }

    fun updateOrderStatus(orderId: String, status: OrderStatus) {
        viewModelScope.launch {
            try {
                val result = repository.updateOrderStatus(orderId, status)
                if (result.isSuccess) {
                    if (status == OrderStatus.CANCELLED) {
                        _currentOrder.value = null
                    }
                    // Получаем ID пользователя из текущего заказа
                    val userId = _currentOrder.value?.userId
                    if (userId != null) {
                        loadUserOrders(userId)
                    } else {
                        Log.e(TAG, "Cannot update orders: userId is null")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating order status: ${e.message}", e)
            }
        }
    }

    fun loadOrderHistory(userId: String) {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("orders")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                val orders = snapshot.documents.mapNotNull { doc ->
                    try {
                        TaxiOrder(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            pickupLocation = doc.getString("pickupLocation") ?: "",
                            destination = doc.getString("destination") ?: "",
                            driverComment = doc.getString("driverComment") ?: "",
                            price = doc.getDouble("price") ?: 0.0,
                            status = OrderStatus.valueOf(doc.getString("status") ?: OrderStatus.PENDING.name),
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            scheduledTime = doc.getLong("scheduledTime"),
                            driverId = doc.getString("driverId")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing order document", e)
                        null
                    }
                }

                _orderHistory.value = orders
                Log.d(TAG, "Order history loaded: ${orders.size} orders")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading order history", e)
            }
        }
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            try {
                _orderState.value = OrderState.Loading
                db.collection("orders")
                    .document(orderId)
                    .delete()
                    .await()
                _orderState.value = OrderState.Success
                
                // Обновляем список заказов
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    loadUserOrders(currentUser.uid)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling order", e)
                _orderState.value = OrderState.Error("Ошибка при отмене заказа")
            }
        }
    }
}

sealed class OrderState {
    object Initial : OrderState()
    object Loading : OrderState()
    object Success : OrderState()
    data class Error(val message: String) : OrderState()
} 