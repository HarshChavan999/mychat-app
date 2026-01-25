package com.example.mychat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mychat.data.model.TravelListing
import com.example.mychat.data.model.User
import com.example.mychat.data.repository.AuthRepository
import com.example.mychat.data.repository.ChatRepository
import com.example.mychat.data.repository.TravelRepository
import com.example.mychat.ui.screens.*
import com.example.mychat.ui.theme.MychatTheme
import com.example.mychat.viewmodel.AuthViewModel
import com.example.mychat.viewmodel.ChatViewModel
import com.example.mychat.viewmodel.TravelViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private var onGoogleSignInResult: ((com.google.android.gms.auth.api.signin.GoogleSignInAccount?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Google Sign-In launcher
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            android.util.Log.d("GoogleSignIn", "Activity result received - resultCode: ${result.resultCode}, data: ${result.data}")

            if (result.resultCode == RESULT_OK) {
                android.util.Log.d("GoogleSignIn", "RESULT_OK received, processing Google Sign-In result")
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    if (account != null) {
                        android.util.Log.d("GoogleSignIn", "Google Sign-In successful! Account: ${account.email}")
                        // Success - call the callback
                        onGoogleSignInResult?.invoke(account)
                    } else {
                        android.util.Log.e("GoogleSignIn", "Account is null after successful result")
                        onGoogleSignInResult?.invoke(null)
                    }
                } catch (e: ApiException) {
                    // Google Sign-In failed
                    android.util.Log.e("GoogleSignIn", "Google sign-in failed: ${e.statusCode} - ${e.message}")
                    android.util.Log.e("GoogleSignIn", "Exception details: ${e.localizedMessage}")
                    onGoogleSignInResult?.invoke(null)
                }
            } else if (result.resultCode == RESULT_CANCELED) {
                android.util.Log.d("GoogleSignIn", "User cancelled Google Sign-In")
                onGoogleSignInResult?.invoke(null)
            } else {
                android.util.Log.d("GoogleSignIn", "Google sign-in failed with result code: ${result.resultCode}")
                onGoogleSignInResult?.invoke(null)
            }
        }

        enableEdgeToEdge()
        setContent {
            MychatTheme {
                var authRepository: AuthRepository? = null

                TravelApp(
                    onGoogleSignIn = {
                        android.util.Log.d("GoogleSignIn", "Google Sign-In button clicked, starting sign-in flow")
                        try {
                            // Use the authRepository from the composable to get consistent GoogleSignInClient
                            authRepository?.let { repo ->
                                val googleSignInClient = repo.getGoogleSignInClient(this@MainActivity)
                                android.util.Log.d("GoogleSignIn", "GoogleSignInClient created successfully")
                                val signInIntent = googleSignInClient.signInIntent
                                android.util.Log.d("GoogleSignIn", "Sign-in intent obtained, launching...")
                                googleSignInLauncher.launch(signInIntent)
                            } ?: run {
                                android.util.Log.e("GoogleSignIn", "AuthRepository not initialized yet")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GoogleSignIn", "Error starting Google Sign-In: ${e.message}")
                            e.printStackTrace()
                        }
                    },
                    onSetGoogleSignInResultCallback = { callback ->
                        onGoogleSignInResult = callback
                    },
                    onAuthRepositoryReady = { repo ->
                        authRepository = repo
                    }
                )
            }
        }
    }
}

enum class Screen {
    DASHBOARD,
    LISTING_DETAIL,
    BOOKING,
    CHAT
}

@Composable
fun TravelNavigation(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    travelViewModel: TravelViewModel,
    currentUser: User
) {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var selectedListing by remember { mutableStateOf<TravelListing?>(null) }
    var chatWithAgency by remember { mutableStateOf<String?>(null) }

    // Firestore automatically handles authentication, no manual connect/disconnect needed
    // Just ensure chat history is loaded when entering chat

    val listings by travelViewModel.listings.collectAsState()
    val isLoadingListings by travelViewModel.isLoadingListings.collectAsState()
    val selectedListingState by travelViewModel.selectedListing.collectAsState()
    val isCreatingBooking by travelViewModel.isCreatingBooking.collectAsState()
    val bookingResult by travelViewModel.bookingResult.collectAsState()

    val chatMessages by chatViewModel.chatMessages.collectAsState()
    val isLoadingHistory by chatViewModel.isLoadingHistory.collectAsState()
    val hasMoreHistory by chatViewModel.hasMoreHistory.collectAsState()
    val historyError by chatViewModel.historyError.collectAsState()

    when (currentScreen) {
        Screen.DASHBOARD -> {
            TravelDashboard(
                currentUser = currentUser,
                listings = listings,
                isLoading = isLoadingListings,
                onListingClick = { listing ->
                    travelViewModel.selectListing(listing)
                    currentScreen = Screen.LISTING_DETAIL
                },
                onChatClick = { listing ->
                    chatWithAgency = listing.agencyId
                    // For demo, we'll use the agency ID as the user ID to chat with
                    val agencyUser = User(
                        id = listing.agencyId,
                        email = "${listing.agencyId}@agency.com",
                        displayName = listing.agencyName,
                        isOnline = true
                    )
                    chatViewModel.setCurrentChatUser(agencyUser)
                    currentScreen = Screen.CHAT
                },
                onSignOut = {
                    authViewModel.signOut()
                }
            )
        }

        Screen.LISTING_DETAIL -> {
            selectedListingState?.let { listing ->
                ListingDetailScreen(
                    listing = listing,
                    onBack = {
                        travelViewModel.clearSelectedListing()
                        currentScreen = Screen.DASHBOARD
                    },
                    onChatClick = {
                        chatWithAgency = listing.agencyId
                        val agencyUser = User(
                            id = listing.agencyId,
                            email = "${listing.agencyId}@agency.com",
                            displayName = listing.agencyName,
                            isOnline = true
                        )
                        chatViewModel.setCurrentChatUser(agencyUser)
                        currentScreen = Screen.CHAT
                    },
                    onBookNow = {
                        currentScreen = Screen.BOOKING
                    }
                )
            }
        }

        Screen.BOOKING -> {
            selectedListingState?.let { listing ->
                BookingScreen(
                    listing = listing,
                    currentUser = currentUser,
                    onBack = {
                        currentScreen = Screen.LISTING_DETAIL
                    },
                    onBookingComplete = {
                        travelViewModel.clearBookingResult()
                        travelViewModel.clearSelectedListing()
                        currentScreen = Screen.DASHBOARD
                    },
                    isCreatingBooking = isCreatingBooking,
                    bookingResult = bookingResult,
                    onCreateBooking = { booking ->
                        travelViewModel.createBooking(booking)
                    },
                    generateBookingReference = {
                        travelViewModel.generateBookingReference()
                    }
                )
            }
        }

        Screen.CHAT -> {
            chatWithAgency?.let { agencyId ->
                val agencyUser = User(
                    id = agencyId,
                    email = "$agencyId@agency.com",
                    displayName = selectedListingState?.agencyName ?: "Travel Agency",
                    isOnline = true
                )

                // Initialize chat history when entering chat
                LaunchedEffect(agencyUser) {
                    chatViewModel.initializeChatHistory()
                }

                ChatScreen(
                    currentUser = currentUser,
                    chatUser = agencyUser,
                    messages = chatMessages,
                    isLoadingHistory = isLoadingHistory,
                    hasMoreHistory = hasMoreHistory,
                    historyError = historyError,
                    onSendMessage = { content ->
                        chatViewModel.sendMessage(agencyUser.id, content)
                    },
                    onBack = {
                        chatWithAgency = null
                        currentScreen = if (selectedListingState != null) Screen.LISTING_DETAIL else Screen.DASHBOARD
                    },
                    onLoadMoreHistory = { chatViewModel.loadMoreHistory() },
                    onClearHistoryError = { chatViewModel.clearHistoryError() }
                )
            }
        }
    }
}

@Composable
fun TravelApp(
    onGoogleSignIn: () -> Unit,
    onSetGoogleSignInResultCallback: ((com.google.android.gms.auth.api.signin.GoogleSignInAccount?) -> Unit) -> Unit,
    onAuthRepositoryReady: (AuthRepository) -> Unit
) {
    // Get application context for network monitoring
    val context = androidx.compose.ui.platform.LocalContext.current

    // Initialize dependencies - use remember to prevent recreation on recomposition
    val authRepository = remember { AuthRepository() }
    val chatRepository = remember { ChatRepository(authRepository) }
    val travelRepository = remember { TravelRepository(authRepository) }

    // Initialize ViewModels
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(authRepository) }
    val chatViewModel: ChatViewModel = viewModel { ChatViewModel(chatRepository) }
    val travelViewModel: TravelViewModel = viewModel { TravelViewModel(travelRepository) }

    // Notify MainActivity that AuthRepository is ready
    LaunchedEffect(authRepository) {
        onAuthRepositoryReady(authRepository)
    }

    // Set up Google Sign-In result callback
    onSetGoogleSignInResultCallback { account ->
        if (account != null) {
            android.util.Log.d("GoogleSignIn", "Account received, starting Firebase sign-in: ${account.email}")
            authViewModel.signInWithGoogle(account)
        } else {
            android.util.Log.d("GoogleSignIn", "No account received (user cancelled or error)")
            // Don't change auth state here - let user try again
        }
    }

    // Main app navigation based on auth state
    val currentUser by authViewModel.currentUser.collectAsState()
    val authState by authViewModel.authState.collectAsState()

    when {
        currentUser != null -> {
            // User is authenticated, show travel interface
            TravelNavigation(
                authViewModel = authViewModel,
                chatViewModel = chatViewModel,
                travelViewModel = travelViewModel,
                currentUser = currentUser!!
            )
        }
        else -> {
            // User not authenticated, show login screen
            LoginScreen(
                authState = authState,
                onSignIn = { email, password ->
                    authViewModel.signIn(email, password)
                },
                onSignUp = { email, password ->
                    authViewModel.signUp(email, password)
                },
                onAnonymousSignIn = {
                    authViewModel.signInAnonymously()
                },
                onGoogleSignIn = onGoogleSignIn
            )
        }
    }
}
