package ir.pinbom.app.data.chat

import ir.pinbom.app.data.remote.ApiClient
import ir.pinbom.app.data.remote.SearchUserPage
import java.io.File
import java.util.UUID
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody

@Singleton
class ChatRepository @Inject constructor(private val api: ApiClient) {
    suspend fun openRealtime(listener: WebSocketListener): WebSocket = api.openChatRealtime(listener)

    fun decodeRealtimeEvent(payload: String): ChatRealtimeEvent = api.decode(payload)

    fun sendTypingRealtime(socket: WebSocket?, conversationId: String): Boolean {
        if (socket == null) return false
        val event = buildJsonObject {
            put("type", "typing")
            put("conversation_id", conversationId)
        }
        return socket.send(event.toString())
    }

    suspend fun conversations(): ChatConversationPage = api.get("chats/")

    suspend fun searchPeople(query: String): SearchUserPage = api.get(
        "search/users",
        mapOf("q" to query.trim(), "limit" to "12"),
    )

    suspend fun createDirect(userId: Long): String {
        val body = buildJsonObject { put("user_id", userId) }
        return api.decode<ChatConversationCreated>(api.request("POST", "chats/direct", api.body(body))).id
    }

    suspend fun messages(conversationId: String, before: String = ""): ChatMessagePage = api.get(
        "chats/${segment(conversationId)}/messages",
        mapOf("limit" to "60", "before" to before),
    )

    suspend fun sendText(conversationId: String, text: String, replyTo: String = ""): ChatMessage {
        val body = buildJsonObject {
            put("client_message_id", UUID.randomUUID().toString())
            put("body", text.trim())
            if (replyTo.isNotBlank()) put("reply_to", replyTo)
        }
        return api.decode(api.request("POST", "chats/${segment(conversationId)}/messages", api.body(body)))
    }

    suspend fun sendForwardedPin(conversationId: String, pinId: Long, caption: String = ""): ChatMessage {
        val body = buildJsonObject {
            put("client_message_id", UUID.randomUUID().toString())
            put("body", caption.trim())
            put("forwarded_pin_id", pinId)
        }
        return api.decode(api.request("POST", "chats/${segment(conversationId)}/messages", api.body(body)))
    }

    suspend fun uploadVoice(file: File, durationMs: Int): ChatMedia {
        val part = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("duration_ms", durationMs.coerceIn(0, 600_000).toString())
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
            .build()
        return api.decode(api.request("POST", "chats/media", part))
    }

    suspend fun sendVoice(conversationId: String, media: ChatMedia): ChatMessage {
        val mediaJson = api.json.encodeToJsonElement(ChatMedia.serializer(), media)
        val body = buildJsonObject {
            put("client_message_id", UUID.randomUUID().toString())
            put("body", "")
            put("media", JsonArray(listOf(mediaJson)))
        }
        return api.decode(api.request("POST", "chats/${segment(conversationId)}/messages", api.body(body)))
    }

    suspend fun markRead(conversationId: String, messageId: String = "") {
        val body = buildJsonObject { if (messageId.isNotBlank()) put("message_id", messageId) }
        api.request("POST", "chats/${segment(conversationId)}/read", api.body(body))
    }

    suspend fun typing(conversationId: String) {
        api.request("POST", "chats/${segment(conversationId)}/typing", api.body(JsonObject(emptyMap())))
    }

    suspend fun react(conversationId: String, messageId: String, emoji: String) {
        api.request(
            "POST",
            "chats/${segment(conversationId)}/messages/${segment(messageId)}/reactions",
            api.body(buildJsonObject { put("emoji", emoji) }),
        )
    }

    suspend fun edit(conversationId: String, messageId: String, body: String): ChatMessage = api.decode(
        api.request(
            "PATCH",
            "chats/${segment(conversationId)}/messages/${segment(messageId)}",
            api.body(buildJsonObject { put("body", body) }),
        ),
    )

    suspend fun delete(conversationId: String, messageId: String) {
        api.request("DELETE", "chats/${segment(conversationId)}/messages/${segment(messageId)}")
    }

    private fun segment(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
