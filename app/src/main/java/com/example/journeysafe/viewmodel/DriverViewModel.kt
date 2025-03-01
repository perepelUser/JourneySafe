package com.example.journeysafe.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.journeysafe.data.OrderStatus
import com.example.journeysafe.data.TaxiOrder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DriverViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "DriverViewModel"
    
    private val _completedOrders = MutableStateFlow(0)
    val completedOrders: StateFlow<Int> = _completedOrders
    
    private val _totalEarnings = MutableStateFlow(0.0)
    val totalEarnings: StateFlow<Double> = _totalEarnings
    
    private val _availableOrders = MutableStateFlow<List<TaxiOrder>>(emptyList())
    val availableOrders: StateFlow<List<TaxiOrder>> = _availableOrders
    
    private val _activeOrder = MutableStateFlow<TaxiOrder?>(null)
    val activeOrder: StateFlow<TaxiOrder?> = _activeOrder
    
    private var ordersListener: ListenerRegistration? = null
    private var activeOrderListener: ListenerRegistration? = null

    fun loadDriverStats(driverId: String) {
        viewModelScope.launch {
            try {
                val completedOrdersSnapshot = db.collection("orders")
                    .whereEqualTo("driverId", driverId)
                    .whereEqualTo("status", OrderStatus.COMPLETED.name)
                    .get()
                    .await()
                
                _completedOrders.value = completedOrdersSnapshot.size()
                
                var total = 0.0
                completedOrdersSnapshot.documents.forEach { doc ->
                    total += doc.getDouble("price") ?: 0.0
                }
                _totalEarnings.value = total
                
                // Начинаем слушать активный заказ
                startActiveOrderListener(driverId)
                
                // Начинаем слушать доступные заказы
                startAvailableOrdersListener()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading driver stats", e)
            }
        }
    }

    private fun startActiveOrderListener(driverId: String) {
        activeOrderListener?.remove()
        activeOrderListener = db.collection("orders")
            .whereEqualTo("driverId", driverId)
            .whereIn("status", listOf(
                OrderStatus.ACCEPTED.name,
                OrderStatus.IN_PROGRESS.name
            ))
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening to active orders", e)
                    return@addSnapshotListener
                }
                
                val activeOrders = snapshot?.documents?.mapNotNull { doc ->
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
                        Log.e(TAG, "Error parsing active order", e)
                        null
                    }
                } ?: emptyList()

                // Обновляем активный заказ только если он действительно изменился
                val currentActiveOrder = _activeOrder.value
                val newActiveOrder = activeOrders.firstOrNull()
                
                if (currentActiveOrder?.id != newActiveOrder?.id || 
                    currentActiveOrder?.status != newActiveOrder?.status) {
                    _activeOrder.value = newActiveOrder
                    Log.d(TAG, "Active order updated: ${newActiveOrder?.id}, status: ${newActiveOrder?.status}")
                }
            }
    }

    private fun startAvailableOrdersListener() {
        ordersListener?.remove()
        ordersListener = db.collection("orders")
            .whereEqualTo("status", OrderStatus.PENDING.name)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening to available orders", e)
                    return@addSnapshotListener
                }
                
                val orders = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        // Проверяем, что у заказа нет назначенного водителя
                        if (doc.getString("driverId") != null) {
                            return@mapNotNull null
                        }
                        
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
                            driverId = null
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing available order", e)
                        null
                    }
                } ?: emptyList()
                
                _availableOrders.value = orders
                Log.d(TAG, "Available orders updated: ${orders.size} orders")
            }
    }

    fun acceptOrder(orderId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                if (currentUserId == null) {
                    Log.e(TAG, "No current user found")
                    return@launch
                }

                // Обновляем заказ в базе данных
                db.collection("orders")
                    .document(orderId)
                    .update(
                        mapOf(
                            "status" to OrderStatus.ACCEPTED.name,
                            "driverId" to currentUserId
                        )
                    )
                    .await()

                // Перезапускаем слушатель активных заказов
                startActiveOrderListener(currentUserId)
                
                Log.d(TAG, "Order $orderId accepted by driver $currentUserId")
            } catch (e: Exception) {
                Log.e(TAG, "Error accepting order", e)
            }
        }
    }

    fun completeOrder(orderId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                if (currentUserId == null) {
                    Log.e(TAG, "No current user found")
                    return@launch
                }

                // Обновляем заказ в базе данных
                db.collection("orders")
                    .document(orderId)
                    .update("status", OrderStatus.COMPLETED.name)
                    .await()

                // Перезагружаем статистику водителя
                loadDriverStats(currentUserId)
                
                Log.d(TAG, "Order $orderId completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error completing order", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ordersListener?.remove()
        activeOrderListener?.remove()
    }
} 