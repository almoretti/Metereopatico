package com.example.firstadroidapp.model

data class SavedLocation(
    val name: String,
    val displayName: String, // For showing "City, Country" format
    val coordinates: String? = null, // For GPS locations
    val isCurrentLocation: Boolean = false
)