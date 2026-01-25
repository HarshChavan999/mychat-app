package com.example.mychat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mychat.data.model.Booking
import com.example.mychat.data.model.TravelListing
import com.example.mychat.data.repository.TravelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TravelViewModel(private val travelRepository: TravelRepository) : ViewModel() {

    private val _listings = MutableStateFlow<List<TravelListing>>(emptyList())
    val listings: StateFlow<List<TravelListing>> = _listings.asStateFlow()

    private val _selectedListing = MutableStateFlow<TravelListing?>(null)
    val selectedListing: StateFlow<TravelListing?> = _selectedListing.asStateFlow()

    private val _isLoadingListings = MutableStateFlow(false)
    val isLoadingListings: StateFlow<Boolean> = _isLoadingListings.asStateFlow()

    private val _isCreatingBooking = MutableStateFlow(false)
    val isCreatingBooking: StateFlow<Boolean> = _isCreatingBooking.asStateFlow()

    private val _bookingResult = MutableStateFlow<Result<String>?>(null)
    val bookingResult: StateFlow<Result<String>?> = _bookingResult.asStateFlow()

    init {
        loadListings()
    }

    fun loadListings() {
        viewModelScope.launch {
            _isLoadingListings.value = true
            travelRepository.getTravelListings().collect { listings ->
                _listings.value = listings
                _isLoadingListings.value = false
            }
        }
    }

    fun loadListingById(id: String) {
        viewModelScope.launch {
            travelRepository.getListingById(id).collect { listing ->
                _selectedListing.value = listing
            }
        }
    }

    fun selectListing(listing: TravelListing) {
        _selectedListing.value = listing
    }

    fun clearSelectedListing() {
        _selectedListing.value = null
    }

    fun createBooking(booking: Booking) {
        viewModelScope.launch {
            _isCreatingBooking.value = true
            _bookingResult.value = null

            val result = travelRepository.createBooking(booking)
            _bookingResult.value = result
            _isCreatingBooking.value = false
        }
    }

    fun clearBookingResult() {
        _bookingResult.value = null
    }

    fun generateBookingReference(): String {
        return "BK${System.currentTimeMillis().toString().takeLast(6)}"
    }
}
