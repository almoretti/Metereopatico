package com.example.firstadroidapp.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface WeatherApiService {
    // wttr.in provides weather in plain text format
    @GET("{location}")
    suspend fun getWeatherText(
        @Path("location") location: String,
        @Query("format") format: String = "%C+%t+%h+%w"
    ): String
    
    // Get detailed weather with 3-day forecast
    @GET("{location}")
    suspend fun getDetailedWeather(
        @Path("location") location: String,
        @Query("format") format: String = "j1"  // JSON format
    ): String
}

