package org.jetbrains.demo.agent.tools

import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class WeatherTool(
    private val client: HttpClient,
    private val apiUrl: String
) : ToolSet {

    @Tool
    @LLMDescription("Fetches the current weather for a given latitude and longitude using the Open-Meteo API.")
    suspend fun weatherForLocation(
        @LLMDescription("The latitude of the location.")
        latitude: Double,
        @LLMDescription("The longitude of the location.")
        longitude: Double
    ): WeatherToolResult {
        val response = client.get(apiUrl) {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("current_weather", true)
            parameter("timezone", "auto")
        }
        return if (response.status.isSuccess()) {
            response.body<WeatherApiResponse>().currentWeather
        } else {
            val error = response.bodyAsText()
            Text("An error occurred with the API request. error=$error")
        }
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

    @Serializable
    sealed interface WeatherToolResult : ToolResult {
        override fun toStringDefault(): String = when (this) {
            is CurrentWeather -> Json.encodeToString(CurrentWeather.serializer(), this)
            is Text -> text
        }
    }

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
    ) : WeatherToolResult

    @Serializable
    data class Text(val text: String) : WeatherToolResult
}