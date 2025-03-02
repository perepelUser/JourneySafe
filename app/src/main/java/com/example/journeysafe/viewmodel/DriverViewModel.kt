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
                        val order = TaxiOrder(
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
                        Log.d(TAG, "Active order found: ${order.id}, status: ${order.status}, driverId: ${order.driverId}")
                        order
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing active order", e)
                        null
                    }
                } ?: emptyList()

                _activeOrder.value = activeOrders.firstOrNull()
                Log.d(TAG, "Active orders updated. Count: ${activeOrders.size}, Current active order: ${_activeOrder.value?.id}")
            }
    }

    private fun startAvailableOrdersListener() {
        ordersListener?.remove()
        ordersListener = db.collection("orders")
            .whereEqualTo("status", OrderStatus.PENDING.name)
            .whereEqualTo("driverId", null)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening to available orders", e)
                    return@addSnapshotListener
                }
                
                val orders = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val order = TaxiOrder(
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
                        
                        if (order.status == OrderStatus.PENDING && order.driverId == null) {
                            Log.d(TAG, "Available order found: ${order.id}, status: ${order.status}")
                            order
                        } else {
                            Log.d(TAG, "Skipping order ${order.id}: status=${order.status}, driverId=${order.driverId}")
                            null
                        }
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
                    ?: throw Exception("Не удалось получить ID пользователя")

                // Сначала проверяем, нет ли у водителя активных заказов
                val activeOrdersSnapshot = db.collection("orders")
                    .whereEqualTo("driverId", currentUserId)
                    .whereIn("status", listOf(OrderStatus.ACCEPTED.name, OrderStatus.IN_PROGRESS.name))
                    .get()
                    .await()

                if (!activeOrdersSnapshot.isEmpty) {
                    throw Exception("У вас уже есть активный заказ")
                }

                // Проверяем существование заказа
                val orderRef = db.collection("orders").document(orderId)
                val orderDoc = orderRef.get().await()

                if (!orderDoc.exists()) {
                    throw Exception("Заказ не найден")
                }

                val status = orderDoc.getString("status")
                val driverId = orderDoc.getString("driverId")

                if (status != OrderStatus.PENDING.name) {
                    throw Exception("Заказ уже не доступен (статус: $status)")
                }

                if (driverId != null) {
                    throw Exception("Заказ уже принят другим водителем")
                }

                // Если все проверки пройдены, обновляем заказ
                orderRef.update(mapOf(
                    "status" to OrderStatus.ACCEPTED.name,
                    "driverId" to currentUserId
                )).await()

                Log.d(TAG, "Order $orderId accepted by driver $currentUserId")

                // После успешного обновления перезапускаем слушатели
                startActiveOrderListener(currentUserId)
                startAvailableOrdersListener()
            } catch (e: Exception) {
                Log.e(TAG, "Error accepting order: ${e.message}", e)
                throw e // Пробрасываем ошибку дальше для обработки в UI
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