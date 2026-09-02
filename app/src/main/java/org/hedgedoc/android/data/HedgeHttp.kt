package org.hedgedoc.android.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class HedgeHttp(val client: OkHttpClient) {
    data class RawResponse(
        val code: Int,
        val body: String,
        val headers: Map<String, String>,
        val finalUrl: String,
    )

    fun execute(request: Request, followBody: Boolean = true): RawResponse {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw HedgeException("Couldn't reach ${request.url.host}. ${e.message}", e)
        }
        response.use { res ->
            val body = if (followBody) res.body?.string().orEmpty() else ""
            val headers = buildMap {
                res.headers.names().forEach { name ->
                    val value = res.header(name)
                    if (value != null) put(name, value)
                }
            }
            if (res.code >= 500) {
                throw HedgeException("Server error ${res.code} from ${request.url.encodedPath}.")
            }
            return RawResponse(res.code, body, headers, res.request.url.toString())
        }
    }

    fun bytes(request: Request): ByteArray {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw HedgeException("Couldn't download. ${e.message}", e)
        }
        response.use { res ->
            if (res.code !in 200..299) {
                throw HedgeException("Download failed (${res.code}).")
            }
            return res.body?.bytes() ?: throw HedgeException("Empty download.")
        }
    }

    companion object {
        fun newClient(cookieJar: MemoryCookieJar): OkHttpClient {
            return OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
