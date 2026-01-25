package com.example.mychat.data.model

data class TravelListing(
    val id: String,
    val title: String,
    val description: String,
    val price: Double,
    val duration: Int, // in days
    val destination: String,
    val type: String, // adventure, luxury, budget, cultural, family, romantic
    val photos: List<String> = emptyList(),
    val rating: Double = 0.0,
    val reviewsCount: Int = 0,
    val agencyId: String,
    val agencyName: String,
    val agencyData: Agency? = null,
    val approved: Boolean = false
)

data class Agency(
    val id: String,
    val companyName: String,
    val name: String, // contact person's name
    val email: String,
    val approved: Boolean = false
)

data class Booking(
    val id: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val userPhone: String? = null,
    val listingId: String,
    val listingTitle: String,
    val agencyId: String,
    val agencyName: String,
    val travelers: Int,
    val travelDate: String? = null,
    val specialRequests: String? = null,
    val preferences: List<String> = emptyList(),
    val totalAmount: Double,
    val status: String = "pending", // pending, confirmed, cancelled
    val createdAt: Long = System.currentTimeMillis(),
    val bookingReference: String
)
