package com.example.firstadroidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.firstadroidapp.ui.theme.FirstAdroidAppTheme
import com.example.firstadroidapp.api.RetrofitInstance
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.firstadroidapp.model.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import android.Manifest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.clickable
import com.example.firstadroidapp.data.LocationsRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirstAdroidAppTheme {
                WeatherApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherApp() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val locationsRepository = remember { LocationsRepository(context) }
    var selectedLocation by remember { mutableStateOf<SavedLocation?>(null) }
    
    NavHost(
        navController = navController,
        startDestination = "weather"
    ) {
        composable("weather") {
            WeatherScreen(
                onNavigateToSavedLocations = { navController.navigate("saved_locations") },
                locationsRepository = locationsRepository,
                selectedLocation = selectedLocation,
                onLocationProcessed = { selectedLocation = null }
            )
        }
        composable("saved_locations") {
            SavedLocationsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLocationSelected = { location ->
                    selectedLocation = location
                    navController.popBackStack()
                },
                locationsRepository = locationsRepository
            )
        }
    }
}

data class WeatherData(
    val city: String,
    val condition: String,
    val temperature: String,
    val humidity: String,
    val wind: String,
    val weatherCode: String = ""
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    onNavigateToSavedLocations: () -> Unit,
    locationsRepository: LocationsRepository,
    selectedLocation: SavedLocation? = null,
    onLocationProcessed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var cityName by remember { mutableStateOf("") }
    var weatherData by remember { mutableStateOf<WeatherData?>(null) }
    var detailedWeather by remember { mutableStateOf<DetailedWeatherResponse?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Autocomplete state
    var locationSuggestions by remember { mutableStateOf<List<LocationSuggestion>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // Location permissions
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Debounced search function for autocomplete
    fun searchLocationSuggestions(query: String) {
        // Cancel previous search job
        searchJob?.cancel()

        if (query.isBlank()) {
            locationSuggestions = emptyList()
            showSuggestions = false
            return
        }

        // Start new search with 300ms delay
        searchJob = coroutineScope.launch {
            kotlinx.coroutines.delay(300)
            try {
                android.util.Log.d("Autocomplete", "Searching for: $query")
                val suggestions = RetrofitInstance.geocodingApi.searchLocations(query)
                android.util.Log.d("Autocomplete", "Found ${suggestions.size} suggestions")
                locationSuggestions = suggestions
                showSuggestions = suggestions.isNotEmpty()
            } catch (e: Exception) {
                android.util.Log.e("Autocomplete", "Search error: ${e.message}", e)
                locationSuggestions = emptyList()
                showSuggestions = false
            }
        }
    }

    // Auto-search when a location is selected from favorites
    LaunchedEffect(selectedLocation) {
        selectedLocation?.let { location ->
            cityName = location.displayName
            isLoading = true
            hasError = false
            
            try {
                val searchQuery = if (location.coordinates != null) {
                    location.coordinates!!
                } else {
                    location.name
                }
                
                val response = RetrofitInstance.api.getWeatherText(searchQuery)
                val parts = response.trim().split("|")

                // DEBUG: Log what API returns
                android.util.Log.d("WeatherAPI", "Raw response: $response")
                android.util.Log.d("WeatherAPI", "Parts: ${parts.joinToString(" | ")}")
                parts.forEachIndexed { index, part ->
                    android.util.Log.d("WeatherAPI", "Part[$index]: '$part'")
                }

                val detailedJson = RetrofitInstance.api.getDetailedWeather(searchQuery)
                detailedWeather = RetrofitInstance.gson.fromJson(detailedJson, DetailedWeatherResponse::class.java)

                weatherData = WeatherData(
                    city = location.displayName,
                    condition = parts.getOrNull(0)?.trim() ?: "N/A",
                    temperature = parts.getOrNull(1)?.trim() ?: "N/A",
                    humidity = parts.getOrNull(2)?.trim() ?: "N/A",
                    wind = parts.getOrNull(3)?.trim() ?: "N/A",
                    weatherCode = detailedWeather?.current_condition?.firstOrNull()?.weatherCode ?: ""
                )
                
            } catch (e: Exception) {
                hasError = true
                weatherData = null
                detailedWeather = null
            } finally {
                isLoading = false
                onLocationProcessed()
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1976D2),
                            Color(0xFF42A5F5),
                            Color(0xFF90CAF9)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with title and saved locations button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Metereopatico",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Row {
                        // Save current location button
                        weatherData?.let { weather ->
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val savedLocation = SavedLocation(
                                            name = weather.city,
                                            displayName = weather.city,
                                            isCurrentLocation = weather.city.contains("Current Location", true)
                                        )
                                        locationsRepository.saveLocation(savedLocation)
                                        
                                        snackbarHostState.showSnackbar(
                                            message = "📍 ${weather.city} saved to favorites",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = "Save location",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        
                        // View saved locations button
                        IconButton(
                            onClick = onNavigateToSavedLocations
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = "Saved locations",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                
                // Search Bar with Autocomplete
                Column {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = cityName,
                                onValueChange = { newValue ->
                                    cityName = newValue
                                    searchLocationSuggestions(newValue)
                                },
                                placeholder = { Text("Enter city name...") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )
                            
                            // Location button for GPS
                            IconButton(
                                onClick = {
                                    if (locationPermissionsState.allPermissionsGranted) {
                                        coroutineScope.launch {
                                            isLoading = true
                                            hasError = false
                                            try {
                                                fusedLocationClient.getCurrentLocation(
                                                    Priority.PRIORITY_HIGH_ACCURACY,
                                                    CancellationTokenSource().token
                                                ).addOnSuccessListener { location ->
                                                    location?.let {
                                                        coroutineScope.launch {
                                                            try {
                                                                val coords = "${it.latitude},${it.longitude}"

                                                                val response = RetrofitInstance.api.getWeatherText(coords)
                                                                val parts = response.trim().split("|")

                                                                // DEBUG: Log what API returns
                                                                android.util.Log.d("WeatherAPI", "Raw response: $response")
                                                                android.util.Log.d("WeatherAPI", "Parts: ${parts.joinToString(" | ")}")
                                                                parts.forEachIndexed { index, part ->
                                                                    android.util.Log.d("WeatherAPI", "Part[$index]: '$part'")
                                                                }

                                                                val detailedJson = RetrofitInstance.api.getDetailedWeather(coords)
                                                                detailedWeather = RetrofitInstance.gson.fromJson(detailedJson, DetailedWeatherResponse::class.java)

                                                                val locationName = detailedWeather?.nearest_area?.firstOrNull()?.let { area ->
                                                                    area.areaName.firstOrNull()?.value ?: "Current Location"
                                                                } ?: "Current Location"

                                                                cityName = locationName

                                                                weatherData = WeatherData(
                                                                    city = locationName,
                                                                    condition = parts.getOrNull(0)?.trim() ?: "N/A",
                                                                    temperature = parts.getOrNull(1)?.trim() ?: "N/A",
                                                                    humidity = parts.getOrNull(2)?.trim() ?: "N/A",
                                                                    wind = parts.getOrNull(3)?.trim() ?: "N/A",
                                                                    weatherCode = detailedWeather?.current_condition?.firstOrNull()?.weatherCode ?: ""
                                                                )
                                                            } catch (e: Exception) {
                                                                hasError = true
                                                            } finally {
                                                                isLoading = false
                                                            }
                                                        }
                                                    }
                                                }.addOnFailureListener {
                                                    isLoading = false
                                                    hasError = true
                                                }
                                            } catch (e: SecurityException) {
                                                isLoading = false
                                                hasError = true
                                            }
                                        }
                                    } else {
                                        locationPermissionsState.launchMultiplePermissionRequest()
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Use current location",
                                    tint = Color(0xFF1976D2)
                                )
                            }
                            
                            // Search button
                            IconButton(
                                onClick = {
                                    if (cityName.isNotBlank()) {
                                        coroutineScope.launch {
                                            isLoading = true
                                            hasError = false
                                            try {
                                                val response = RetrofitInstance.api.getWeatherText(cityName)
                                                val parts = response.trim().split("|")

                                                // DEBUG: Log what API returns
                                                android.util.Log.d("WeatherAPI", "Raw response: $response")
                                                android.util.Log.d("WeatherAPI", "Parts: ${parts.joinToString(" | ")}")
                                                parts.forEachIndexed { index, part ->
                                                    android.util.Log.d("WeatherAPI", "Part[$index]: '$part'")
                                                }

                                                val detailedJson = RetrofitInstance.api.getDetailedWeather(cityName)
                                                detailedWeather = RetrofitInstance.gson.fromJson(detailedJson, DetailedWeatherResponse::class.java)

                                                weatherData = WeatherData(
                                                    city = cityName,
                                                    condition = parts.getOrNull(0)?.trim() ?: "N/A",
                                                    temperature = parts.getOrNull(1)?.trim() ?: "N/A",
                                                    humidity = parts.getOrNull(2)?.trim() ?: "N/A",
                                                    wind = parts.getOrNull(3)?.trim() ?: "N/A",
                                                    weatherCode = detailedWeather?.current_condition?.firstOrNull()?.weatherCode ?: ""
                                                )
                                            } catch (e: Exception) {
                                                hasError = true
                                                weatherData = null
                                                detailedWeather = null
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFF1976D2)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color(0xFF1976D2)
                                    )
                                }
                            }
                        }
                    }

                    // Autocomplete Dropdown
                    if (showSuggestions && locationSuggestions.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 250.dp)
                            ) {
                                items(locationSuggestions) { suggestion ->
                                    LocationSuggestionItem(
                                        suggestion = suggestion,
                                        onClick = {
                                            // Extract just the city name (first part before comma)
                                            cityName = suggestion.display_name.split(",").firstOrNull()?.trim() ?: suggestion.display_name
                                            showSuggestions = false
                                            locationSuggestions = emptyList()

                                            // Trigger automatic search
                                            coroutineScope.launch {
                                                isLoading = true
                                                hasError = false
                                                try {
                                                    val response = RetrofitInstance.api.getWeatherText(cityName)
                                                    val parts = response.trim().split("|")

                                                    // DEBUG: Log what API returns
                                                    android.util.Log.d("WeatherAPI", "Raw response: $response")
                                                    android.util.Log.d("WeatherAPI", "Parts: ${parts.joinToString(" | ")}")
                                                    parts.forEachIndexed { index, part ->
                                                        android.util.Log.d("WeatherAPI", "Part[$index]: '$part'")
                                                    }

                                                    val detailedJson = RetrofitInstance.api.getDetailedWeather(cityName)
                                                    detailedWeather = RetrofitInstance.gson.fromJson(detailedJson, DetailedWeatherResponse::class.java)

                                                    weatherData = WeatherData(
                                                        city = cityName,
                                                        condition = parts.getOrNull(0)?.trim() ?: "N/A",
                                                        temperature = parts.getOrNull(1)?.trim() ?: "N/A",
                                                        humidity = parts.getOrNull(2)?.trim() ?: "N/A",
                                                        wind = parts.getOrNull(3)?.trim() ?: "N/A",
                                                        weatherCode = detailedWeather?.current_condition?.firstOrNull()?.weatherCode ?: ""
                                                    )
                                                } catch (e: Exception) {
                                                    hasError = true
                                                    weatherData = null
                                                    detailedWeather = null
                                                } finally {
                                                    isLoading = false
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Weather Display
                AnimatedVisibility(
                    visible = weatherData != null || hasError,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    if (hasError) {
                        ErrorCard()
                    } else {
                        weatherData?.let { data ->
                            WeatherCard(data)
                        }
                    }
                }
                
                if (weatherData == null && !hasError) {
                    EmptyStateCard()
                }
                
                // Detailed Weather Info
                detailedWeather?.current_condition?.firstOrNull()?.let { current ->
                    Spacer(modifier = Modifier.height(16.dp))
                    DetailedWeatherInfoCard(current)
                }
                
                // Hourly Forecast
                detailedWeather?.let { detailed ->
                    detailed.weather.firstOrNull()?.let { todayWeather ->
                        Spacer(modifier = Modifier.height(16.dp))
                        HourlyForecastCard(todayWeather.hourly)
                    }
                    
                    // 3-Day Forecast
                    if (detailed.weather.size > 1) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ThreeDayForecastCard(detailed.weather)
                    }
                    
                    // Astronomy Info
                    detailed.weather.firstOrNull()?.astronomy?.firstOrNull()?.let { astro ->
                        Spacer(modifier = Modifier.height(16.dp))
                        AstronomyCard(astro)
                    }
                }
            }
        }
    }
}


@Composable
fun WeatherCard(weatherData: WeatherData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Location
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = weatherData.city,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Temperature Display - 100% bigger
            Text(
                text = weatherData.temperature,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2),
                maxLines = 1,
                modifier = Modifier.wrapContentHeight()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Weather Condition
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    getWeatherIcon(weatherData.condition, weatherData.weatherCode),
                    contentDescription = "Weather",
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = weatherData.condition,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF424242)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Humidity and Wind Row
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = Color(0xFFE3F2FD)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeatherMetric(
                    icon = Icons.Default.WaterDrop,
                    label = "Humidity",
                    value = weatherData.humidity,
                    color = Color(0xFF42A5F5)
                )

                VerticalDivider(
                    modifier = Modifier
                        .height(100.dp)
                        .width(1.dp),
                    color = Color(0xFFE3F2FD)
                )

                WeatherMetric(
                    icon = Icons.Default.Air,
                    label = "Wind",
                    value = weatherData.wind,
                    color = Color(0xFF66BB6A)
                )
            }
        }
    }
}

@Composable
fun WeatherMetric(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF757575)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF212121)
        )
    }
}

@Composable
fun ErrorCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = "Error",
                tint = Color(0xFFE53E3E),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Oops! Something went wrong",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE53E3E)
            )
            Text(
                text = "Please check your connection and try again",
                fontSize = 14.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = "Weather",
                tint = Color(0xFF1976D2),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Welcome to Metereopatico!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter a city name or use your current location to get started",
                fontSize = 16.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DetailedWeatherInfoCard(current: CurrentCondition) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Details",
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Weather Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompactDetailMetric(
                    icon = Icons.Default.Thermostat,
                    title = "Feels Like",
                    value = "${current.FeelsLikeC}°",
                    color = Color(0xFFFF6B6B)
                )
                CompactDetailMetric(
                    icon = Icons.Default.WbSunny,
                    title = "UV Index",
                    value = current.uvIndex,
                    color = Color(0xFFFFA726)
                )
                CompactDetailMetric(
                    icon = Icons.Default.Speed,
                    title = "Pressure",
                    value = current.pressure,
                    color = Color(0xFF7E57C2)
                )
                CompactDetailMetric(
                    icon = Icons.Default.Visibility,
                    title = "Visibility",
                    value = "${current.visibility}km",
                    color = Color(0xFF26A69A)
                )
            }
        }
    }
}

@Composable
fun CompactDetailMetric(
    icon: ImageVector,
    title: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF212121),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun DetailMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = Modifier.width(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HourlyForecastCard(hourlyData: List<HourlyWeather>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = "Hourly",
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hourly Forecast",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(hourlyData.take(12)) { hourly ->
                    HourlyItem(hourly)
                }
            }
        }
    }
}

@Composable
fun HourlyItem(hourly: HourlyWeather) {
    Card(
        modifier = Modifier.width(85.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    hourly.time.toIntOrNull() != null && hourly.time.toInt() < 100 -> "${hourly.time.padStart(2, '0')}:00"
                    hourly.time.length >= 3 -> "${hourly.time.substring(0, hourly.time.length - 2).padStart(2, '0')}:00"
                    else -> "${hourly.time}:00"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1976D2)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Icon(
                getWeatherIcon(
                    hourly.weatherDesc.firstOrNull()?.value ?: "",
                    hourly.weatherCode
                ),
                contentDescription = "Weather",
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${hourly.tempC}°",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.WaterDrop,
                    contentDescription = "Humidity",
                    tint = Color(0xFF42A5F5),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${hourly.humidity}%",
                    fontSize = 11.sp,
                    color = Color(0xFF616161)
                )
            }
        }
    }
}

@Composable
fun ThreeDayForecastCard(weatherData: List<WeatherDay>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "Forecast",
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "3-Day Forecast",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            weatherData.take(3).forEachIndexed { index, weather ->
                val dayLabel = when (index) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> "Day After Tomorrow"
                }

                DayForecastItem(
                    day = dayLabel,
                    condition = weather.hourly.firstOrNull()?.weatherDesc?.firstOrNull()?.value ?: "Clear",
                    high = "${weather.maxtempC}°",
                    low = "${weather.mintempC}°",
                    weatherCode = weather.hourly.firstOrNull()?.weatherCode ?: ""
                )

                if (index < 2) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 1.dp,
                        color = Color(0xFFE3F2FD)
                    )
                }
            }
        }
    }
}

@Composable
fun DayForecastItem(
    day: String,
    condition: String,
    high: String,
    low: String,
    weatherCode: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Day and condition
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                getWeatherIcon(condition, weatherCode),
                contentDescription = condition,
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = day,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF212121)
                )
                Text(
                    text = condition,
                    fontSize = 13.sp,
                    color = Color(0xFF757575)
                )
            }
        }

        // Temperature range
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = "High",
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = high,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = "Low",
                    tint = Color(0xFF42A5F5),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = low,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF757575)
                )
            }
        }
    }
}

@Composable
fun AstronomyCard(astronomy: AstroData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.WbTwilight,
                    contentDescription = "Astronomy",
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sun & Moon",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Moon Phase (at top, larger)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = getMoonPhaseEmoji(astronomy.moon_phase),
                            fontSize = 56.sp
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text(
                                text = "Moon Phase",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF757575)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = astronomy.moon_phase,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sun Times
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SunMoonItem(
                    icon = Icons.Default.WbSunny,
                    label = "Sunrise",
                    value = astronomy.sunrise,
                    color = Color(0xFFFFA726),
                    backgroundColor = Color(0xFFFFF3E0)
                )

                SunMoonItem(
                    icon = Icons.Default.WbTwilight,
                    label = "Sunset",
                    value = astronomy.sunset,
                    color = Color(0xFFFF7043),
                    backgroundColor = Color(0xFFFFE0D5)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Moon Times
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SunMoonItem(
                    icon = Icons.Default.NightsStay,
                    label = "Moonrise",
                    value = astronomy.moonrise,
                    color = Color(0xFF7E57C2),
                    backgroundColor = Color(0xFFF3E5F5)
                )

                SunMoonItem(
                    icon = Icons.Default.Bedtime,
                    label = "Moonset",
                    value = astronomy.moonset,
                    color = Color(0xFF5C6BC0),
                    backgroundColor = Color(0xFFE8EAF6)
                )
            }
        }
    }
}

fun getWeatherIcon(condition: String, weatherCode: String? = null): ImageVector {
    // Use weather code if available (more accurate)
    weatherCode?.let { code ->
        return when (code) {
            "113" -> Icons.Default.WbSunny  // Sunny/Clear
            "116" -> Icons.Default.Cloud  // Partly cloudy
            "119", "122" -> Icons.Default.CloudQueue  // Cloudy/Overcast
            "143", "248", "260" -> Icons.Default.Cloud  // Mist/Fog
            "176", "263", "266", "293", "296" -> Icons.Default.WaterDrop  // Light rain/drizzle
            "179", "182", "185", "281", "284", "311", "314", "317", "350", "362", "365", "374", "377" -> Icons.Default.AcUnit  // Snow/Ice
            "200", "386", "389", "392", "395" -> Icons.Default.Thunderstorm  // Thunder
            "227", "230", "323", "326", "329", "332", "335", "338", "371" -> Icons.Default.AcUnit  // Heavy Snow
            "299", "302", "305", "308", "356", "359" -> Icons.Default.WaterDrop  // Heavy rain
            else -> Icons.Default.Cloud
        }
    }

    // Fallback to condition text
    return when {
        condition.contains("sunny", ignoreCase = true) ||
        condition.contains("clear", ignoreCase = true) -> Icons.Default.WbSunny

        condition.contains("partly cloudy", ignoreCase = true) ||
        condition.contains("partly cloud", ignoreCase = true) -> Icons.Default.CloudQueue

        condition.contains("cloudy", ignoreCase = true) ||
        condition.contains("overcast", ignoreCase = true) -> Icons.Default.Cloud

        condition.contains("rain", ignoreCase = true) ||
        condition.contains("drizzle", ignoreCase = true) ||
        condition.contains("shower", ignoreCase = true) -> Icons.Default.WaterDrop

        condition.contains("thunder", ignoreCase = true) ||
        condition.contains("storm", ignoreCase = true) -> Icons.Default.Thunderstorm

        condition.contains("snow", ignoreCase = true) ||
        condition.contains("sleet", ignoreCase = true) ||
        condition.contains("ice", ignoreCase = true) ||
        condition.contains("freezing", ignoreCase = true) -> Icons.Default.AcUnit

        condition.contains("mist", ignoreCase = true) ||
        condition.contains("fog", ignoreCase = true) -> Icons.Default.Cloud

        else -> Icons.Default.Cloud
    }
}

fun getMoonPhaseEmoji(phase: String): String {
    return when {
        phase.contains("New Moon", ignoreCase = true) -> "🌑"
        phase.contains("Waxing Crescent", ignoreCase = true) -> "🌒"
        phase.contains("First Quarter", ignoreCase = true) -> "🌓"
        phase.contains("Waxing Gibbous", ignoreCase = true) -> "🌔"
        phase.contains("Full Moon", ignoreCase = true) -> "🌕"
        phase.contains("Waning Gibbous", ignoreCase = true) -> "🌖"
        phase.contains("Last Quarter", ignoreCase = true) || phase.contains("Third Quarter", ignoreCase = true) -> "🌗"
        phase.contains("Waning Crescent", ignoreCase = true) -> "🌘"
        else -> "🌙"
    }
}

@Composable
fun SunMoonItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    backgroundColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF757575)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedLocationsScreen(
    onNavigateBack: () -> Unit,
    onLocationSelected: (SavedLocation) -> Unit,
    locationsRepository: LocationsRepository
) {
    val savedLocations by locationsRepository.savedLocations.collectAsState(emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Saved Locations",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1976D2),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1976D2),
                            Color(0xFF42A5F5),
                            Color(0xFF90CAF9)
                        )
                    )
                )
        ) {
            if (savedLocations.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = "No locations",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No saved locations yet",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Save locations from the weather screen to see them here",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(savedLocations) { location ->
                        SavedLocationItem(
                            location = location,
                            onLocationClick = { onLocationSelected(location) },
                            onDeleteClick = {
                                coroutineScope.launch {
                                    locationsRepository.removeLocation(location)
                                    snackbarHostState.showSnackbar(
                                        message = "🗑️ ${location.displayName} removed from favorites",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedLocationItem(
    location: SavedLocation,
    onLocationClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLocationClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (location.isCurrentLocation) Icons.Default.MyLocation else Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = location.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF424242)
                )
            }
            
            IconButton(
                onClick = onDeleteClick
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFE53E3E)
                )
            }
        }
    }
}

@Composable
fun LocationSuggestionItem(
    suggestion: LocationSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = "Location",
            tint = Color(0xFF1976D2),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = suggestion.display_name,
            fontSize = 15.sp,
            color = Color(0xFF212121),
            maxLines = 2
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WeatherScreenPreview() {
    FirstAdroidAppTheme {
        WeatherApp()
    }
}