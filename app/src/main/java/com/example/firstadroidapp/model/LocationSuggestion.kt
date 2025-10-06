package com.example.firstadroidapp.model

data class LocationSuggestion(
    val display_name: String,
    val lat: String,
    val lon: String,
    val name: String? = null,
    val type: String? = null
)
