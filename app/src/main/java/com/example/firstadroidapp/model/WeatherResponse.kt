package com.example.firstadroidapp.model

data class WeatherResponse(
    val main: Main,
    val weather: List<Weather>,
    val name: String
)

data class Main(
    val temp: Double,
    val humidity: Int,
    val temp_min: Double,
    val temp_max: Double
)

data class Weather(
    val description: String,
    val icon: String
)