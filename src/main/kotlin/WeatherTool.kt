package com.example

import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class WeatherTool(private val client: HttpClient) : ToolSet {

    @Tool
    @LLMDescription("Fetches the current weather for a given latitude and longitude using the Open-Meteo API.")
    suspend fun getWeather(
        @LLMDescription("The latitude of the location.")
        latitude: Double,
        @LLMDescription("The longitude of the location.")
        longitude: Double
    ): ToolResult = try {
        println("Fetching weather for Lat: $latitude, Lon: $longitude...")
        client.get("https://api.open-meteo.com/v1/forecastPOOP") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("current_weather", true)
            parameter("timezone", "auto")
        }.body<WeatherApiResponse>().currentWeather
    } catch (e: Exception) {
        println("An error occurred with the API request: ${e.message}")
        val message = "An error occurred with the API request: ${e.message}"
        ToolResult.Text(message)
    }

    @LLMDescription("The response from the Open-Meteo API.")
    @Serializable
    data class WeatherApiResponse(
        @LLMDescription("The latitude of the location.")
        @SerialName("latitude") val latitude: Double,
        @LLMDescription("The longitude of the location.")
        @SerialName("longitude") val longitude: Double,
        @LLMDescription("The current weather for the location.")
        @SerialName("current_weather") val currentWeather: CurrentWeather
    )

    @LLMDescription("The current weather for a given location.")
    @Serializable
    data class CurrentWeather(
        @LLMDescription("The temperature in degrees Celsius.")
        val temperature: Double,
        @LLMDescription("The wind speed in meters per second.")
        val windspeed: Double,
        @LLMDescription("The wind direction in degrees.")
        val winddirection: Double,
        @LLMDescription("The weather condition code.")
        val weathercode: Int,
        @LLMDescription("The time of the forecast in ISO 8601 format.")
        val time: String
    ) : ToolResult.JSONSerializable<CurrentWeather> {
        override fun getSerializer(): KSerializer<CurrentWeather> = serializer()
    }
}
