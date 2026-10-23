package com.aditya.cabletv.saver

import com.aditya.cabletv.home.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Screensaver weather (R-19, D-024): Open-Meteo, free and keyless. Geocodes the city once per
 * fetch, then reads the current temperature and weather code. Cached 60 min; any failure keeps
 * the last line or hides it (the clock never waits on the network).
 */
class Weather(private val prefs: Prefs) {

    /** The cached line, e.g. "27°C CLOUDY", or null when there's no city or no data. */
    fun line(): String? = prefs.weatherLine.takeIf { it.isNotEmpty() && prefs.weatherCity.isNotEmpty() }

    /** Refreshes in the background if the cache is older than 60 minutes. */
    fun refreshIfStale() {
        val city = prefs.weatherCity
        if (city.isEmpty() || System.currentTimeMillis() - prefs.weatherAt < 60 * 60_000L) return
        Thread {
            runCatching {
                val geo = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8"))
                    .getJSONArray("results").getJSONObject(0)
                val now = get(
                    "https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code" +
                        "&latitude=${geo.getDouble("latitude")}&longitude=${geo.getDouble("longitude")}",
                ).getJSONObject("current")
                prefs.weatherLine = "${now.getDouble("temperature_2m").roundToInt()}°C ${word(now.getInt("weather_code"))}"
            }
        }.start()
    }

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000; c.readTimeout = 8_000
        return c.inputStream.use { JSONObject(it.bufferedReader().readText()) }.also { c.disconnect() }
    }

    /** WMO weather code → one uppercase word (design-spec S5 weather line). */
    private fun word(code: Int) = when (code) {
        0 -> "CLEAR"
        1, 2 -> "PARTLY CLOUDY"
        3 -> "CLOUDY"
        45, 48 -> "FOG"
        in 51..57 -> "DRIZZLE"
        in 61..67, in 80..82 -> "RAIN"
        in 71..77, 85, 86 -> "SNOW"
        in 95..99 -> "STORM"
        else -> ""
    }.trim()
}
