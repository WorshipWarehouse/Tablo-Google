package com.example.data.remote

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Tablo 4th Gen (LighthouseTV) authentication and HMAC-MD5 signing utilities.
 * Follows the reverse-engineered specification from trevor-viljoen/tablo-api.
 */
object TabloGen4Auth {
    const val CLOUD_HOST = "https://lighthousetv.ewscloud.com"
    const val USER_AGENT_CLOUD = "Tablo-FAST/2.0.0 (Mobile; iPhone; iOS 16.6)"
    const val USER_AGENT_WATCH = "Tablo-FAST/1.7.0 (Mobile; iPhone; iOS 18.4)"

    private const val HASH_KEY = "6l8jU5N43cEilqItmT3U2M2PFM3qPziilXqau9ys"
    private const val DEVICE_KEY = "ljpg6ZkwShVv8aI12E2LP55Ep8vq1uYDPvX0DdTB"

    fun deviceDate(): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("GMT")
        return sdf.format(Date())
    }

    fun md5Hex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hmacMd5Hex(key: String, payload: String): String {
        val mac = Mac.getInstance("HmacMD5")
        val keySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacMD5")
        mac.init(keySpec)
        val signed = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return signed.joinToString("") { "%02x".format(it) }
    }

    /**
     * Constructs the (Authorization, Date) headers required for Tablo Gen 4 local device calls.
     * Format: tablo:<DEVICE_KEY>:<hmac_md5_signature>
     */
    fun makeDeviceAuth(method: String, path: String, body: String = ""): Pair<String, String> {
        val date = deviceDate()
        val bodyHash = if (body.isNotEmpty()) md5Hex(body) else ""
        val payload = "$method\n$path\n$bodyHash\n$date"
        val sig = hmacMd5Hex(HASH_KEY, payload)
        val authHeader = "tablo:$DEVICE_KEY:$sig"
        return Pair(authHeader, date)
    }

    /**
     * Builds the standard watch POST body for Tablo Gen 4.
     */
    fun makeWatchBody(clientId: String): String {
        val safeClientId = clientId.ifBlank { "00000000-0000-0000-0000-000000000000" }
        return """{"bandwidth": null, "extra": {"limitedAdTracking": 1, "deviceOSVersion": "16.6", "lang": "en_US", "height": 1080, "deviceId": "00000000-0000-0000-0000-000000000000", "width": 1920, "deviceModel": "iPhone10,1", "deviceMake": "Apple", "deviceOS": "iOS"}, "device_id": "$safeClientId", "platform": "ios"}"""
    }
}
