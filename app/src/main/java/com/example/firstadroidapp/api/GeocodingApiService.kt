package com.example.firstadroidapp.api

import com.example.firstadroidapp.model.LocationSuggestion
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApiService {
    @GET("search")
    suspend fun searchLocations(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 5,
        @Query("addressdetails") addressDetails: Int = 0
    ): List<LocationSuggestion>
}
