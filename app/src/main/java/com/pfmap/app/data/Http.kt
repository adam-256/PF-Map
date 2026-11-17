package com.pfmap.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class HttpException(val code: Int, val body: String) : IOException("HTTP $code")

internal object Http {
    private const val UA = "PF-Map-Android/1.0"

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20_000): String =
        request("GET", url, null, headers, timeoutMs)

    fun post(url: String, body: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20_000): String =
        request("POST", url, body, headers + ("Content-Type" to "application/json"), timeoutMs)

    private fun request(method: String, url: String, body: String?, headers: Map<String, String>, timeoutMs: Int): String {
        var target = url
        repeat(5) { // follow redirects manually so https->https cross-host hops (GitHub release assets) work
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs * 3
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json, */*")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: throw HttpException(code, "redirect without location")
                    target = URL(URL(target), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw HttpException(code, err)
                }
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects for $url")
    }
}
