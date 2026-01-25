package com.example.mychat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mychat.data.model.Booking
import com.example.mychat.data.model.TravelListing
import com.example.mychat.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(
    listing: TravelListing,
    currentUser: User,
    onBack: () -> Unit,
    onBookingComplete: () -> Unit,
    isCreatingBooking: Boolean,
    bookingResult: Result<String>?,
    onCreateBooking: (Booking) -> Unit,
    generateBookingReference: () -> String
) {
    var currentStep by remember { mutableStateOf(1) }
    var travelers by remember { mutableStateOf(1) }
    var travelDate by remember { mutableStateOf("") }
    var specialRequests by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf(currentUser.displayName) }
    var contactEmail by remember { mutableStateOf(currentUser.email) }
    var contactPhone by remember { mutableStateOf("") }
    var preferences by remember { mutableStateOf(setOf<String>()) }

    // Reset booking result when screen opens
    LaunchedEffect(Unit) {
        // Any initial setup if needed
    }

    // Handle booking result
    LaunchedEffect(bookingResult) {
        bookingResult?.fold(
            onSuccess = {
                // Show success and navigate back
                onBookingComplete()
            },
            onFailure = {
                // Error handled in UI
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Your Trip - Step $currentStep of 4") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Progress Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (step in 1..4) {
                    val isCompleted = step < currentStep
                    val isCurrent = step == currentStep

                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Green, shape = androidx.compose.foundation.shape.CircleShape)
                                    .padding(8.dp)
                            )
                        } else {
                            Text(
                                text = step.toString(),
                                color = if (isCurrent) Color.White else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .wrapContentSize()
                            )
                        }
                    }

                    if (step < 4) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(if (step < currentStep) Color.Green else Color.LightGray)
                        )
                    }
                }
            }

            when (currentStep) {
                1 -> Step1PackageDetails(
                    listing = listing,
                    travelers = travelers,
                    travelDate = travelDate,
                    specialRequests = specialRequests,
                    onTravelersChange = { travelers = it },
                    onTravelDateChange = { travelDate = it },
                    onSpecialRequestsChange = { specialRequests = it }
                )
                2 -> Step2Preferences(
                    preferences = preferences,
                    onPreferencesChange = { preferences = it }
                )
                3 -> Step3ContactInfo(
                    contactName = contactName,
                    contactEmail = contactEmail,
                    contactPhone = contactPhone,
                    onContactNameChange = { contactName = it },
                    onContactEmailChange = { contactEmail = it },
                    onContactPhoneChange = { contactPhone = it }
                )
                4 -> Step4Summary(
                    listing = listing,
                    travelers = travelers,
                    travelDate = travelDate,
                    specialRequests = specialRequests,
                    contactName = contactName,
                    contactEmail = contactEmail,
                    contactPhone = contactPhone,
                    preferences = preferences,
                    isCreatingBooking = isCreatingBooking,
                    bookingResult = bookingResult
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentStep > 1) {
                            currentStep--
                        } else {
                            onBack()
                        }
                    }
                ) {
                    Text(if (currentStep == 1) "Cancel" else "Previous")
                }

                if (currentStep < 4) {
                    Button(
                        onClick = { currentStep++ }
                    ) {
                        Text("Next")
                    }
                } else {
                    Button(
                        onClick = {
                            val booking = Booking(
                                id = generateBookingReference(),
                                userId = currentUser.id,
                                userName = contactName,
                                userEmail = contactEmail,
                                userPhone = contactPhone,
                                listingId = listing.id,
                                listingTitle = listing.title,
                                agencyId = listing.agencyId,
                                agencyName = listing.agencyName,
                                travelers = travelers,
                                travelDate = travelDate,
                                specialRequests = specialRequests,
                                preferences = preferences.toList(),
                                totalAmount = listing.price * travelers,
                                status = "pending",
                                createdAt = System.currentTimeMillis(),
                                bookingReference = generateBookingReference()
                            )
                            onCreateBooking(booking)
                        },
                        enabled = !isCreatingBooking
                    ) {
                        if (isCreatingBooking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White
                            )
                        } else {
                            Text("Confirm Booking")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Step1PackageDetails(
    listing: TravelListing,
    travelers: Int,
    travelDate: String,
    specialRequests: String,
    onTravelersChange: (Int) -> Unit,
    onTravelDateChange: (String) -> Unit,
    onSpecialRequestsChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Package Details",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = listing.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "By ${listing.agencyName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = travelers.toString(),
            onValueChange = { onTravelersChange(it.toIntOrNull() ?: 1) },
            label = { Text("Number of Travelers") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = travelDate,
            onValueChange = onTravelDateChange,
            label = { Text("Preferred Travel Date") },
            placeholder = { Text("Select date") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = specialRequests,
            onValueChange = onSpecialRequestsChange,
            label = { Text("Special Requests or Notes") },
            placeholder = { Text("Any special requirements...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
    }
}

@Composable
fun Step2Preferences(
    preferences: Set<String>,
    onPreferencesChange: (Set<String>) -> Unit
) {
    val availablePreferences = listOf(
        "Adventure", "Culture", "Food", "Relaxation",
        "Shopping", "Nightlife"
    )

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Travel Preferences",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select your interests (optional)",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        availablePreferences.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { preference ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = preferences.contains(preference),
                            onCheckedChange = { checked ->
                                val newPrefs = if (checked) {
                                    preferences + preference
                                } else {
                                    preferences - preference
                                }
                                onPreferencesChange(newPrefs)
                            }
                        )
                        Text(
                            text = preference,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun Step3ContactInfo(
    contactName: String,
    contactEmail: String,
    contactPhone: String,
    onContactNameChange: (String) -> Unit,
    onContactEmailChange: (String) -> Unit,
    onContactPhoneChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Contact Information",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = contactName,
            onValueChange = onContactNameChange,
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = contactEmail,
            onValueChange = onContactEmailChange,
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = contactPhone,
            onValueChange = onContactPhoneChange,
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun Step4Summary(
    listing: TravelListing,
    travelers: Int,
    travelDate: String,
    specialRequests: String,
    contactName: String,
    contactEmail: String,
    contactPhone: String,
    preferences: Set<String>,
    isCreatingBooking: Boolean,
    bookingResult: Result<String>?
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Booking Summary",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Package:", fontWeight = FontWeight.Bold)
                    Text(listing.title)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Travelers:")
                    Text("$travelers")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Travel Date:")
                    Text(travelDate.ifEmpty { "Not specified" })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Price per person:")
                    Text("$${listing.price.toInt()}")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Amount:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "$${(listing.price * travelers).toInt()}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show booking result
        bookingResult?.fold(
            onSuccess = {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "✅ Booking Submitted Successfully!",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "You will receive a confirmation email shortly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            },
            onFailure = {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "❌ Booking Failed",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFC62828)
                        )
                        Text(
                            text = it.message ?: "Please try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }
        )
    }
}
