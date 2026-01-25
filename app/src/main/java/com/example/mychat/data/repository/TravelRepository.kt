package com.example.mychat.data.repository

import com.example.mychat.data.model.Booking
import com.example.mychat.data.model.TravelListing
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class TravelRepository(private val authRepository: AuthRepository) {

    private val db = FirebaseFirestore.getInstance()

    fun getTravelListings(): Flow<List<TravelListing>> = authRepository.currentUser.flatMapLatest { currentUser ->
        android.util.Log.d("TravelRepository", "getTravelListings: Auth state changed - Current user = $currentUser")
        android.util.Log.d("TravelRepository", "getTravelListings: User role = ${currentUser?.role}, approved = ${currentUser?.approved}")

        if (currentUser?.role == "user") {
            // User has access, proceed with Firestore listener
            android.util.Log.d("TravelRepository", "User has access, querying approved listings")
            callbackFlow {
                        val listener = db.collection("listings")
                    .whereEqualTo("approved", true)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.e("TravelRepository", "Error fetching listings", error)
                            trySend(emptyList())
                            return@addSnapshotListener
                        }

                        android.util.Log.d("TravelRepository", "Query returned ${snapshot?.documents?.size ?: 0} documents")

                        // Process listings asynchronously to fetch agency names
                        val documents = snapshot?.documents ?: emptyList()
                        android.util.Log.d("TravelRepository", "Processing ${documents.size} documents")

                        // Create a list to hold all listings
                        val listings = mutableListOf<TravelListing>()

                        // Use a counter to track when all listings are processed
                        var processedCount = 0
                        val totalCount = documents.size

                        if (totalCount == 0) {
                            trySend(emptyList())
                            return@addSnapshotListener
                        }

                        for (document in documents) {
                            try {
                                val data = document.data ?: continue
                                android.util.Log.d("TravelRepository", "Processing listing: ${data["title"]}, approved: ${data["approved"]}")

                                val agencyId = data["agencyId"] as? String ?: ""
                                var agencyName = "Unknown Agency"
                                var agencyData: com.example.mychat.data.model.Agency? = null

                                if (agencyId.isNotEmpty()) {
                                    // Fetch agency data from users collection
                                    db.collection("users").document(agencyId).get()
                                        .addOnSuccessListener { agencyDoc ->
                                            if (agencyDoc.exists()) {
                                                val agencyDocData = agencyDoc.data
                                                if (agencyDocData != null) {
                                                    agencyName = agencyDocData["companyName"] as? String ?: "Unknown Agency"
                                                    agencyData = com.example.mychat.data.model.Agency(
                                                        id = agencyDoc.id,
                                                        companyName = agencyDocData["companyName"] as? String ?: "",
                                                        name = agencyDocData["name"] as? String ?: "",
                                                        email = agencyDocData["email"] as? String ?: "",
                                                        approved = agencyDocData["approved"] as? Boolean ?: false
                                                    )
                                                }
                                            }

                                            // Create the listing with the fetched agency data
                                            val listing = TravelListing(
                                                id = document.id,
                                                title = data["title"] as? String ?: "",
                                                description = data["description"] as? String ?: "",
                                                price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                                                duration = (data["duration"] as? Number)?.toInt() ?: 1,
                                                destination = data["destination"] as? String ?: "",
                                                type = data["type"] as? String ?: "adventure",
                                                photos = (data["photos"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                                rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
                                                reviewsCount = (data["reviewsCount"] as? Number)?.toInt() ?: 0,
                                                agencyId = agencyId,
                                                agencyName = agencyName,
                                                agencyData = agencyData,
                                                approved = data["approved"] as? Boolean ?: false
                                            )

                                            listings.add(listing)
                                            processedCount++

                                            // Send the updated list when all documents are processed
                                            if (processedCount == totalCount) {
                                                android.util.Log.d("TravelRepository", "All listings processed, sending ${listings.size} listings")
                                                trySend(listings)
                                            }
                                        }
                                        .addOnFailureListener { exception ->
                                            android.util.Log.e("TravelRepository", "Error fetching agency data for $agencyId", exception)

                                            // Create listing with default agency name
                                            val listing = TravelListing(
                                                id = document.id,
                                                title = data["title"] as? String ?: "",
                                                description = data["description"] as? String ?: "",
                                                price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                                                duration = (data["duration"] as? Number)?.toInt() ?: 1,
                                                destination = data["destination"] as? String ?: "",
                                                type = data["type"] as? String ?: "adventure",
                                                photos = (data["photos"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                                rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
                                                reviewsCount = (data["reviewsCount"] as? Number)?.toInt() ?: 0,
                                                agencyId = agencyId,
                                                agencyName = agencyName,
                                                agencyData = agencyData,
                                                approved = data["approved"] as? Boolean ?: false
                                            )

                                            listings.add(listing)
                                            processedCount++

                                            // Send the updated list when all documents are processed
                                            if (processedCount == totalCount) {
                                                android.util.Log.d("TravelRepository", "All listings processed, sending ${listings.size} listings")
                                                trySend(listings)
                                            }
                                        }
                                } else {
                                    // No agency ID, create listing with default values
                                    val listing = TravelListing(
                                        id = document.id,
                                        title = data["title"] as? String ?: "",
                                        description = data["description"] as? String ?: "",
                                        price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                                        duration = (data["duration"] as? Number)?.toInt() ?: 1,
                                        destination = data["destination"] as? String ?: "",
                                        type = data["type"] as? String ?: "adventure",
                                                photos = (data["photos"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                                rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
                                                reviewsCount = (data["reviewsCount"] as? Number)?.toInt() ?: 0,
                                                agencyId = agencyId,
                                                agencyName = agencyName,
                                                agencyData = agencyData,
                                                approved = data["approved"] as? Boolean ?: false
                                            )

                                            listings.add(listing)
                                            processedCount++

                                            // Send the updated list when all documents are processed
                                            if (processedCount == totalCount) {
                                                android.util.Log.d("TravelRepository", "All listings processed, sending ${listings.size} listings")
                                                trySend(listings)
                                            }
                                        }
                            } catch (e: Exception) {
                                android.util.Log.e("TravelRepository", "Error processing document ${document.id}", e)
                                processedCount++
                                if (processedCount == totalCount) {
                                    trySend(listings)
                                }
                            }
                        }
                    }

                awaitClose {
                    listener.remove()
                }
            }
        } else {
            // User doesn't have access or is not authenticated, emit empty list
            android.util.Log.d("TravelRepository", "User not authenticated or no access, emitting empty list")
            flowOf(emptyList())
        }
    }

    fun getListingById(id: String): Flow<TravelListing?> = callbackFlow {
        // Get current user synchronously to check permissions
        val currentUser = runBlocking { authRepository.currentUser.first() }

        if (currentUser?.role == "user") {
            // User has access, proceed with Firestore listener
            val listener = db.collection("listings").document(id)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val data = snapshot.data
                        if (data != null) {
                            try {
                                val agencyId = data["agencyId"] as? String ?: ""
                                var agencyName = "Unknown Agency"
                                var agencyData: com.example.mychat.data.model.Agency? = null

                                // Try to get agency data
                                if (agencyId.isNotEmpty()) {
                                    agencyName = data["agencyName"] as? String ?: "Unknown Agency"
                                    // Fetch agency data asynchronously
                                    db.collection("users").document(agencyId).get()
                                        .addOnSuccessListener { agencyDoc ->
                                            if (agencyDoc.exists()) {
                                                val agencyDocData = agencyDoc.data
                                                if (agencyDocData != null) {
                                                    agencyData = com.example.mychat.data.model.Agency(
                                                        id = agencyDoc.id,
                                                        companyName = agencyDocData["companyName"] as? String ?: "",
                                                        name = agencyDocData["name"] as? String ?: "",
                                                        email = agencyDocData["email"] as? String ?: "",
                                                        approved = agencyDocData["approved"] as? Boolean ?: false
                                                    )
                                                    // Emit updated listing with agency data
                                                    val listing = createTravelListing(snapshot.id, data, agencyName, agencyData)
                                                    trySend(listing)
                                                }
                                            }
                                        }
                                }

                                val listing = createTravelListing(snapshot.id, data, agencyName, agencyData)
                                trySend(listing)
                            } catch (e: Exception) {
                                trySend(null)
                            }
                        } else {
                            trySend(null)
                        }
                    } else {
                        trySend(null)
                    }
                }

            awaitClose {
                listener.remove()
            }
        } else {
            // User doesn't have access, emit null
            trySend(null)
            awaitClose { }
        }
    }

    private fun createTravelListing(
        id: String,
        data: Map<String, Any>,
        agencyName: String,
        agencyData: com.example.mychat.data.model.Agency?
    ): TravelListing {
        return TravelListing(
            id = id,
            title = data["title"] as? String ?: "",
            description = data["description"] as? String ?: "",
            price = (data["price"] as? Number)?.toDouble() ?: 0.0,
            duration = (data["duration"] as? Number)?.toInt() ?: 1,
            destination = data["destination"] as? String ?: "",
            type = data["type"] as? String ?: "adventure",
            photos = (data["photos"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
            reviewsCount = (data["reviewsCount"] as? Number)?.toInt() ?: 0,
            agencyId = data["agencyId"] as? String ?: "",
            agencyName = agencyName,
            agencyData = agencyData,
            approved = data["approved"] as? Boolean ?: false
        )
    }

    suspend fun createBooking(booking: Booking): Result<String> {
        // Check if user has permission to create bookings
        val currentUser = runBlocking { authRepository.currentUser.first() }
        if (currentUser?.role != "user" || !currentUser.approved) {
            return Result.failure(Exception("Access denied: Only approved users can create bookings"))
        }

        return try {
            val bookingData = hashMapOf(
                "userId" to booking.userId,
                "userName" to booking.userName,
                "userEmail" to booking.userEmail,
                "userPhone" to booking.userPhone,
                "listingId" to booking.listingId,
                "listingTitle" to booking.listingTitle,
                "agencyId" to booking.agencyId,
                "agencyName" to booking.agencyName,
                "travelers" to booking.travelers,
                "travelDate" to booking.travelDate,
                "specialRequests" to booking.specialRequests,
                "preferences" to booking.preferences,
                "totalAmount" to booking.totalAmount,
                "status" to booking.status,
                "createdAt" to System.currentTimeMillis(),
                "bookingReference" to booking.bookingReference
            )

            db.collection("bookings").add(bookingData).await()
            Result.success("Booking created successfully")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUserBookings(userId: String): Flow<List<Booking>> = callbackFlow {
        val listener = db.collection("bookings")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val bookings = snapshot?.documents?.mapNotNull { document ->
                    try {
                        val data = document.data ?: return@mapNotNull null
                        Booking(
                            id = document.id,
                            userId = data["userId"] as? String ?: "",
                            userName = data["userName"] as? String ?: "",
                            userEmail = data["userEmail"] as? String ?: "",
                            userPhone = data["userPhone"] as? String,
                            listingId = data["listingId"] as? String ?: "",
                            listingTitle = data["listingTitle"] as? String ?: "",
                            agencyId = data["agencyId"] as? String ?: "",
                            agencyName = data["agencyName"] as? String ?: "",
                            travelers = (data["travelers"] as? Number)?.toInt() ?: 1,
                            travelDate = data["travelDate"] as? String,
                            specialRequests = data["specialRequests"] as? String,
                            preferences = (data["preferences"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                            totalAmount = (data["totalAmount"] as? Number)?.toDouble() ?: 0.0,
                            status = data["status"] as? String ?: "pending",
                            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                            bookingReference = data["bookingReference"] as? String ?: ""
                        )
                    } catch (e: Exception) {
                        null // Skip malformed documents
                    }
                } ?: emptyList()

                trySend(bookings)
            }

        awaitClose {
            listener.remove()
        }
    }
}
