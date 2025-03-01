package com.example.journeysafe.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.example.journeysafe.data.TaxiOrder
import com.example.journeysafe.data.OrderStatus
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch

class TaxiRepository {
    private val TAG = "TaxiRepository"
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val ordersCollection = firestore.collection("orders")

    suspend fun createOrder(order: TaxiOrder): Result<String> {
        return try {
            Log.d(TAG, "Creating new order: $order")
            val documentRef = ordersCollection.document()
            val orderWithId = order.copy(id = documentRef.id)
            documentRef.set(orderWithId).await()
            Log.d(TAG, "Order created successfully with ID: ${documentRef.id}")
            Result.success(documentRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating order", e)
            Result.failure(e)
        }
    }

    suspend fun getOrder(orderId: String): Result<TaxiOrder> {
        return try {
            Log.d(TAG, "Fetching order with ID: $orderId")
            val document = ordersCollection.document(orderId).get().await()
            val order = document.toObject(TaxiOrder::class.java)
            if (order != null) {
                Log.d(TAG, "Order found: $order")
                Result.success(order)
            } else {
                Log.w(TAG, "Order not found for ID: $orderId")
                Result.failure(Exception("Order not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching order", e)
            Result.failure(e)
        }
    }

    fun getUserOrders(userId: String): Flow<List<TaxiOrder>> = flow {
        Log.d(TAG, "Fetching orders for user: $userId")
        val snapshot = ordersCollection
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()
        
        val orders = snapshot.toObjects(TaxiOrder::class.java)
        Log.d(TAG, "Found ${orders.size} orders for user $userId: $orders")
        emit(orders)
    }.catch { e ->
        Log.e(TAG, "Error fetching user orders", e)
        emit(emptyList())
    }

    suspend fun updateOrderStatus(orderId: String, status: OrderStatus): Result<Unit> {
        return try {
            Log.d(TAG, "Updating order $orderId status to $status")
            ordersCollection.document(orderId)
                .update("status", status)
                .await()
            Log.d(TAG, "Order status updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating order status", e)
            Result.failure(e)
        }
    }
} 