package com.example.firstadroidapp.api

import com.google.gson.Gson
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    // Using wttr.in - a free weather service that doesn't require API key
    private const val WEATHER_BASE_URL = "https://wttr.in/"
    
    val api: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(WEATHER_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }
    
    
    val gson = Gson()
}