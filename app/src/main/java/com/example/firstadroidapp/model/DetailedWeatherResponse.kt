package com.example.firstadroidapp.model

data class DetailedWeatherResponse(
    val current_condition: List<CurrentCondition>,
    val weather: List<WeatherDay>,
    val nearest_area: List<NearestArea>? = null,
    val astronomy: List<Astronomy>? = null
)

data class NearestArea(
    val areaName: List<AreaValue>,
    val country: List<AreaValue>,
    val region: List<AreaValue>
)

data class AreaValue(
    val value: String
)

data class Astronomy(
    val astronomy: List<AstroData>
)

data class AstroData(
    val sunrise: String,
    val sunset: String,
    val moonrise: String,
    val moonset: String,
    val moon_phase: String,
    val moon_illumination: String
)

data class CurrentCondition(
    val temp_C: String,
    val temp_F: String,
    val FeelsLikeC: String,
    val weatherDesc: List<WeatherDescription>,
    val humidity: String,
    val windspeedKmph: String,
    val winddirDegree: String,
    val winddir16Point: String,
    val pressure: String,
    val visibility: String,
    val uvIndex: String,
    val cloudcover: String,
    val precipMM: String,
    val weatherCode: String
)

data class WeatherDescription(
    val value: String
)

data class WeatherDay(
    val date: String,
    val maxtempC: String,
    val mintempC: String,
    val hourly: List<HourlyWeather>,
    val astronomy: List<AstroData>? = null
)

data class HourlyWeather(
    val time: String,
    val tempC: String,
    val weatherDesc: List<WeatherDescription>,
    val humidity: String,
    val windspeedKmph: String,
    val weatherCode: String = ""
)