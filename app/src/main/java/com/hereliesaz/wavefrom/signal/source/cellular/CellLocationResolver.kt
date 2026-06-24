package com.hereliesaz.wavefrom.signal.source.cellular

import com.hereliesaz.wavefrom.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** A WGS84 position. */
data class LatLon(val lat: Double, val lon: Double)

/** The identity OpenCellID needs to look up a tower. */
data class CellKey(val radio: String, val mcc: Int, val mnc: Int, val lac: Int, val cid: Long) {
    val cacheKey: String get() = "$radio:$mcc:$mnc:$lac:$cid"
}

/**
 * Resolves a cell tower's position from the OpenCellID database. The whole feature
 * is opt-in and degrades gracefully: with no API key
 * ([BuildConfig.OPENCELLID_API_KEY] blank), no network, or any error, [resolve]
 * returns null and the caller keeps the cell as `RssiOnly`. Hits *and* misses are
 * cached in memory so a rate-limited API is queried at most once per cell.
 */
class CellLocationResolver(
    private val apiKey: String = BuildConfig.OPENCELLID_API_KEY,
    private val endpoint: String = "https://opencellid.org/cell/get",
) : TowerResolver {
    private val cache = ConcurrentHashMap<String, LatLon>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    /** True when an API key is configured; gates the network path. */
    override val enabled: Boolean get() = apiKey.isNotBlank()

    override suspend fun resolve(key: CellKey): LatLon? {
        if (!enabled) return null
        cache[key.cacheKey]?.let { return it }
        if (key.cacheKey in misses) return null
        // Only cache a *definitive* miss (a real response that parsed to not-found).
        // A transient failure (timeout, 5xx, rate limit) returns null without caching
        // so the tower can resolve on a later poll once the network recovers.
        val body = fetch(key) ?: return null
        val loc = parse(body)
        if (loc != null) cache[key.cacheKey] = loc else misses.add(key.cacheKey)
        return loc
    }

    private suspend fun fetch(key: CellKey): String? = withContext(Dispatchers.IO) {
        val url = URL(
            "$endpoint?key=${enc(apiKey)}&mcc=${key.mcc}&mnc=${key.mnc}" +
                "&lac=${key.lac}&cellid=${key.cid}&radio=${enc(key.radio)}&format=json",
        )
        var conn: HttpURLConnection? = null
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 4000

        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

        /**
         * Parse an OpenCellID JSON body into a position, or null for an error /
         * not-found / (0,0) sentinel response. Pure — unit-tested without a network.
         */
        fun parse(body: String): LatLon? = try {
            val json = JSONObject(body)
            when {
                json.has("error") -> null
                !json.has("lat") || !json.has("lon") -> null
                else -> {
                    val lat = json.getDouble("lat")
                    val lon = json.getDouble("lon")
                    if (lat == 0.0 && lon == 0.0) null else LatLon(lat, lon)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
