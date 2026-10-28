package io.github.shamlawy.playnext.net

import io.github.shamlawy.playnext.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A call the practice copy refused before it left the phone. */
class WriteBlocked(why: String) : IOException(why)

/** The app's one network client: every call passes the write switch, helper calls carry the web app's Origin. */
object Net {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(Guard)
            // and again for every request that really goes out: a 307/308 keeps the method and the body, so without
            // this a report sent to /bug and redirected would be POSTed to wherever the redirect points (/vault/put)
            .addNetworkInterceptor(Guard)
            .build()
    }

    object Guard : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val url = req.url
            val body = if (url.host == Routes.HELPER_HOST && url.encodedPath == "/prices") {
                req.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }
            } else null
            when (val v = Routes.check(req.method, url.host, url.encodedPath, body)) {
                is Routes.Block -> throw WriteBlocked(v.why)
                Routes.Allow -> Unit
            }
            val out = if (Routes.wantsOrigin(url.host)) req.newBuilder().header("Origin", BuildConfig.WEB_ORIGIN).build() else req
            return chain.proceed(out)
        }
    }

    fun helper(path: String) = BuildConfig.HELPER + path

    fun postJson(url: String, json: String): Request = Request.Builder().url(url).post(json.toRequestBody(JSON)).build()

    /**
     * Runs a call off the main thread. Reads (GET/HEAD) retry twice on a network error with a short pause;
     * a refusal by the write switch never retries.
     */
    suspend fun <T> call(req: Request, read: (Response) -> T): T = call({ req }, read)

    /** The same, with the request made again for each try: a resumed download asks from where the last try stopped. */
    suspend fun <T> call(make: () -> Request, read: (Response) -> T): T = withContext(Dispatchers.IO) {
        var req = make()
        val tries = if (req.method == "GET" || req.method == "HEAD") 3 else 1
        var last: IOException? = null
        repeat(tries) { i ->
            if (i > 0) req = make()
            try {
                return@withContext client.newCall(req).execute().use(read)
            } catch (e: WriteBlocked) {
                throw e
            } catch (e: IOException) {
                last = e
                if (i < tries - 1) delay(600L * (i + 1))
            }
        }
        throw last ?: IOException("network")
    }
}
