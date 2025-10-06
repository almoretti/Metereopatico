package com.example.firstadroidapp.api

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    // Using wttr.in - a free weather service that doesn't require API key
    private const val WEATHER_BASE_URL = "https://wttr.in/"

    // Using Nominatim - a free geocoding service for location search
    private const val GEOCODING_BASE_URL = "https://nominatim.openstreetmap.org/"

    val api: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(WEATHER_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    // OkHttp client with User-Agent header (required by Nominatim)
    private val geocodingHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", "WeatherApp/1.0 (Android)")
                .build()
            chain.proceed(request)
        }
        .build()

    val geocodingApi: GeocodingApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GEOCODING_BASE_URL)
            .client(geocodingHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodingApiService::class.java)
    }

    val gson = Gson()
}