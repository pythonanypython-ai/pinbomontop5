package ir.pinbom.app.data.remote

import ir.pinbom.app.data.SessionManager
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import retrofit2.HttpException

@Singleton
class ApiClient @Inject constructor(private val session: SessionManager) {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; encodeDefaults = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    // Redirects are not followed: bearer tokens must never leak to a redirected host.
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).writeTimeout(90, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private val refreshLock = Mutex()
    // WebSockets are long-lived; the REST client's read/call timeouts must not
    // terminate an otherwise healthy upgraded connection.
    private val realtimeClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    suspend fun openChatRealtime(listener: WebSocketListener): WebSocket {
        // This authenticated request also runs the existing refresh-token path
        // before we copy the access token into the WebSocket handshake.
        request("GET", "chats/")
        session.load()
        val token = session.accessToken?.takeIf { it.isNotBlank() }
            ?: throw IOException("Sign in to connect to chat updates.")
        val base = session.baseUrl.trimEnd('/')
        val socketBase = when {
            base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substringAfter("://")
            base.startsWith("http://", ignoreCase = true) -> "ws://" + base.substringAfter("://")
            else -> throw IOException("The chat server URL must use HTTPS or HTTP.")
        }
        val request = Request.Builder()
            .url(socketBase + "/v1/chats/socket")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        return realtimeClient.newWebSocket(request, listener)
    }

    fun body(value: JsonElement): RequestBody = value.toString().toRequestBody(jsonType)
    suspend inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap()): T =
        decode(request("GET", path, query = query))
    inline fun <reified T> decode(text: String): T = json.decodeFromString<T>(text)

    suspend fun request(method: String, path: String, payload: RequestBody? = null,
        query: Map<String, String> = emptyMap(), authenticated: Boolean = true): String {
        session.load()
        val initialToken = if (authenticated) session.accessToken else null
        val response = execute(method, path, payload, query, initialToken)
        if (authenticated && initialToken != null && response.code == 401 && session.refreshToken != null) {
            response.close()
            val refreshed = refreshLock.withLock {
                if (session.accessToken != null && session.accessToken != initialToken) true
                else refresh()
            }
            if (refreshed) return consume(execute(method, path, payload, query, session.accessToken), authenticated)
            throw HttpException(retrofit2.Response.error<String>(401, "{\"error\":{\"code\":\"unauthorized\",\"message\":\"Session expired\"}}".toResponseBody(jsonType)))
        }
        return consume(response, authenticated)
    }
    private suspend fun refresh(): Boolean {
        val token = session.refreshToken ?: return false
        val response = execute("POST", "auth/refresh", body(buildJsonObject { put("refresh_token", token) }), emptyMap(), null)
        response.use {
            val text = it.body?.string().orEmpty()
            if (it.isSuccessful) {
                val tokens = json.decodeFromString<TokenResponse>(text)
                require(tokens.access_token.isNotBlank()) { "Missing refreshed access token" }
                session.setTokens(tokens.access_token, tokens.refresh_token.ifBlank { token }, tokens.user?.username.orEmpty())
                return true
            }
            if (it.code == 401 || it.code == 403) { session.clear(invalidated = true); return false }
            // A server outage or unsupported route is not a logout.
            throw HttpException(retrofit2.Response.error<String>(it.code, text.toResponseBody(jsonType)))
        }
    }
    private suspend fun consume(response: Response, authenticated: Boolean): String = response.use {
        val text = it.body?.string().orEmpty()
        if (!it.isSuccessful) {
            val code = runCatching { json.decodeFromString<ApiErrorEnvelope>(text).error.code }.getOrDefault("")
            if (authenticated && session.accessToken != null && (it.code == 401 ||
                (it.code == 403 && code in setOf("account_disabled", "user_disabled", "account_banned", "user_banned", "session_revoked")))) {
                session.clear(invalidated = true)
            }
            throw HttpException(retrofit2.Response.error<String>(it.code, text.toResponseBody(jsonType)))
        }
        text
    }
    private suspend fun execute(method: String, path: String, payload: RequestBody?, query: Map<String, String>, token: String?): Response {
        val url = (session.baseUrl.trimEnd('/') + "/v1/" + path.trimStart('/')).toHttpUrl().newBuilder()
            .apply { query.filterValues { it.isNotBlank() }.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .method(method, if (method == "GET" || method == "HEAD") null else payload ?: ByteArray(0).toRequestBody(null)).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, resource, _ -> resource.close() }
                }
            })
        }
    }
}
