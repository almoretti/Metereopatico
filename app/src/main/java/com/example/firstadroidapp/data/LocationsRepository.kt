package com.example.firstadroidapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.firstadroidapp.model.SavedLocation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "locations")

class LocationsRepository(private val context: Context) {
    private val gson = Gson()
    
    companion object {
        private val SAVED_LOCATIONS_KEY = stringSetPreferencesKey("saved_locations")
    }
    
    val savedLocations: Flow<List<SavedLocation>> = context.dataStore.data
        .map { preferences ->
            val locationsJson = preferences[SAVED_LOCATIONS_KEY] ?: emptySet()
            locationsJson.map { json ->
                gson.fromJson(json, SavedLocation::class.java)
            }.sortedBy { it.displayName }
        }
    
    suspend fun saveLocation(location: SavedLocation) {
        context.dataStore.edit { preferences ->
            val currentLocations = preferences[SAVED_LOCATIONS_KEY]?.toMutableSet() ?: mutableSetOf()
            val locationJson = gson.toJson(location)
            currentLocations.add(locationJson)
            preferences[SAVED_LOCATIONS_KEY] = currentLocations
        }
    }
    
    suspend fun removeLocation(location: SavedLocation) {
        context.dataStore.edit { preferences ->
            val currentLocations = preferences[SAVED_LOCATIONS_KEY]?.toMutableSet() ?: mutableSetOf()
            val locationJson = gson.toJson(location)
            currentLocations.remove(locationJson)
            preferences[SAVED_LOCATIONS_KEY] = currentLocations
        }
    }
    
    suspend fun isLocationSaved(locationName: String): Boolean {
        val locations = context.dataStore.data.map { preferences ->
            val locationsJson = preferences[SAVED_LOCATIONS_KEY] ?: emptySet()
            locationsJson.map { json ->
                gson.fromJson(json, SavedLocation::class.java)
            }
        }
        return locations.map { list ->
            list.any { it.name == locationName }
        }.map { it }.toString().toBoolean()
    }
}