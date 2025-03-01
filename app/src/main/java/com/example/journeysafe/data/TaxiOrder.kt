package com.example.journeysafe.data

data class TaxiOrder(
    val id: String,
    val userId: String,
    val pickupLocation: String,
    val destination: String,
    val driverComment: String,
    val price: Double,
    val status: OrderStatus,
    val timestamp: Long,
    val scheduledTime: Long? = null,
    val driverId: String? = null
)

enum class OrderStatus {
    PENDING,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}