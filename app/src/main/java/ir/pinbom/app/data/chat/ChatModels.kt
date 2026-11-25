package ir.pinbom.app.data.chat

import kotlinx.serialization.Serializable

@Serializable
data class ChatPeer(
    val id: Long = 0,
    val username: String = "",
    val display_name: String = "",
    val avatar_url: String = "",
)

@Serializable
data class ChatConversation(
    val id: String = "",
    val kind: String = "direct",
    val title: String = "",
    val peer: ChatPeer = ChatPeer(),
    val last_message: String = "",
    val last_message_at: String = "",
    val updated_at: String = "",
    val unread_count: Int = 0,
)

@Serializable
data class ChatConversationPage(val items: List<ChatConversation> = emptyList(), val next_cursor: String = "")

@Serializable
data class ChatMedia(
    val type: String = "",
    val url: String = "",
    val mime_type: String = "",
    val file_name: String = "",
    val duration_ms: Int = 0,
    val bytes: Long = 0,
)

@Serializable
data class ChatReplyPreview(
    val id: String = "",
    val sender_name: String = "",
    val body: String = "",
    val deleted: Boolean = false,
)

@Serializable
data class ChatForwardedPin(
    val id: Long = 0,
    val title: String = "",
    val image_url: String = "",
    val author_username: String = "",
)

@Serializable
data class ChatReaction(
    val emoji: String = "",
    val count: Int = 0,
    val mine: Boolean = false,
)

@Serializable
data class ChatMessage(
    val id: String = "",
    val conversation_id: String = "",
    val sender_id: Long = 0,
    val sender: ChatPeer = ChatPeer(),
    val client_message_id: String = "",
    val body: String = "",
    val media: List<ChatMedia> = emptyList(),
    val reply_to: String = "",
    val reply_preview: ChatReplyPreview? = null,
    val forwarded_pin: ChatForwardedPin? = null,
    val forwarded_pin_removed: Boolean = false,
    val edited_at: String = "",
    val deleted_at: String = "",
    val created_at: String = "",
    val read_by_peer: Boolean = false,
    val reactions: List<ChatReaction> = emptyList(),
)

@Serializable
data class ChatMessagePage(
    val peer: ChatPeer = ChatPeer(),
    val items: List<ChatMessage> = emptyList(),
    val next_cursor: String = "",
    val typing_users: List<ChatPeer> = emptyList(),
)

@Serializable
data class ChatConversationCreated(val id: String = "", val kind: String = "direct")

@Serializable
data class ChatRealtimeEvent(
    val type: String = "",
    val conversation_id: String = "",
    val message_id: String = "",
    val sender_id: Long = 0,
    val user_id: Long = 0,
    val at: String = "",
)
