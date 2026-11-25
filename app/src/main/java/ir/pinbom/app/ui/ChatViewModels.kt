package ir.pinbom.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.pinbom.app.data.chat.ChatConversation
import ir.pinbom.app.data.chat.ChatMessage
import ir.pinbom.app.data.chat.ChatMedia
import ir.pinbom.app.data.chat.ChatPeer
import ir.pinbom.app.data.chat.ChatRepository
import ir.pinbom.app.data.remote.SearchUser
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@HiltViewModel
class ChatInboxViewModel @Inject constructor(private val repository: ChatRepository) : ViewModel() {
    private val _items = MutableStateFlow<List<ChatConversation>>(emptyList())
    val items = _items.asStateFlow()
    private val _people = MutableStateFlow<List<SearchUser>>(emptyList())
    val people = _people.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow("")
    val error = _error.asStateFlow()
    private var realtimeJob: Job? = null
    private var realtimeSocket: WebSocket? = null
    private var reloadJob: Job? = null

    fun load() = viewModelScope.launch { loadNow() }

    private suspend fun loadNow() {
        _loading.value = true
        runCatching { repository.conversations() }
            .onSuccess { _items.value = it.items; _error.value = "" }
            .onFailure { _error.value = it.message.orEmpty() }
        _loading.value = false
    }

    private fun scheduleLoad() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(280)
            loadNow()
        }
    }

    fun startRealtime() {
        if (realtimeJob?.isActive == true) return
        realtimeJob = viewModelScope.launch {
            var retryMs = 1_000L
            while (isActive) {
                val disconnected = CompletableDeferred<Unit>()
                try {
                    realtimeSocket = repository.openRealtime(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            retryMs = 1_000L
                            scheduleLoad()
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val event = runCatching { repository.decodeRealtimeEvent(text) }.getOrNull() ?: return
                            if (event.type in setOf(
                                    "conversation.created", "message.created", "message.updated",
                                    "message.deleted", "reaction.updated", "receipt.read",
                                )) {
                                scheduleLoad()
                            }
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            disconnected.complete(Unit)
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            disconnected.complete(Unit)
                        }
                    })
                    disconnected.await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // Reconnect below. The next successful connection reloads the inbox.
                } finally {
                    realtimeSocket?.cancel()
                    realtimeSocket = null
                }
                if (isActive) {
                    delay(retryMs)
                    retryMs = (retryMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    fun stopRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
        reloadJob?.cancel()
        reloadJob = null
        realtimeSocket?.close(1000, "inbox closed")
        realtimeSocket = null
    }

    fun search(query: String) = viewModelScope.launch {
        if (query.isBlank()) { _people.value = emptyList(); return@launch }
        runCatching { repository.searchPeople(query) }
            .onSuccess { _people.value = it.items }
            .onFailure { _error.value = it.message.orEmpty() }
    }

    suspend fun openDirect(userId: Long): String = repository.createDirect(userId)
    suspend fun forward(conversationId: String, pinId: Long) { repository.sendForwardedPin(conversationId, pinId) }
}

@HiltViewModel
class ChatConversationViewModel @Inject constructor(private val repository: ChatRepository) : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _typing = MutableStateFlow(false)
    val typing = _typing.asStateFlow()
    private val _peer = MutableStateFlow<ChatPeer?>(null)
    val peer = _peer.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder = _loadingOlder.asStateFlow()
    private val _error = MutableStateFlow("")
    val error = _error.asStateFlow()

    private var nextCursor = ""
    private var realtimeJob: Job? = null
    private var realtimeSocket: WebSocket? = null
    private var typingResetJob: Job? = null
    private var lastTypingSentAt = 0L
    private var realtimeConversationId = ""

    suspend fun refresh(conversationId: String) {
        runCatching { repository.messages(conversationId) }
            .onSuccess { page ->
                _peer.value = page.peer.takeIf { it.id > 0L || it.username.isNotBlank() }
                val existing = _messages.value
                // A reconnect refreshes the latest page without discarding older
                // pages the user has already loaded or an optimistic pending send.
                _messages.value = (existing.filterNot { old -> page.items.any { it.id == old.id } } + page.items)
                    .distinctBy { it.id }
                    .sortedByMessageTime()
                // If we had only the newest page loaded, a full refresh may
                // now reveal that older history exists (for example, after a
                // busy conversation grows past the page size).
                if (nextCursor.isBlank() && existing.size <= page.items.size) nextCursor = page.next_cursor
                _typing.value = page.typing_users.isNotEmpty()
                page.items.lastOrNull()?.let { last ->
                    runCatching { repository.markRead(conversationId, last.id) }
                }
                _error.value = ""
            }
            .onFailure { _error.value = it.message.orEmpty() }
    }

    /** Load an older page when the reader reaches the top of the timeline. */
    suspend fun loadOlder(conversationId: String): Int {
        if (nextCursor.isBlank() || _loadingOlder.value) return 0
        _loadingOlder.value = true
        return try {
            val page = repository.messages(conversationId, nextCursor)
            nextCursor = page.next_cursor
            val existing = _messages.value.mapTo(HashSet()) { it.id }
            val older = page.items.filterNot { it.id in existing }
            if (older.isNotEmpty()) _messages.value = (older + _messages.value).sortedByMessageTime()
            older.size
        } catch (error: Throwable) {
            _error.value = error.message.orEmpty()
            0
        } finally {
            _loadingOlder.value = false
        }
    }

    fun startRealtime(conversationId: String) {
        if (realtimeJob?.isActive == true && realtimeConversationId == conversationId) return
        stopRealtime()
        realtimeConversationId = conversationId
        realtimeJob = viewModelScope.launch {
            var retryMs = 1_000L
            var connectedBefore = false
            while (isActive) {
                val disconnected = CompletableDeferred<Unit>()
                try {
                    realtimeSocket = repository.openRealtime(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            retryMs = 1_000L
                            if (connectedBefore) viewModelScope.launch { refresh(conversationId) }
                            connectedBefore = true
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val event = runCatching { repository.decodeRealtimeEvent(text) }.getOrNull() ?: return
                            if (event.conversation_id != conversationId) return
                            if (event.type == "typing") {
                                _typing.value = true
                                typingResetJob?.cancel()
                                typingResetJob = viewModelScope.launch {
                                    delay(3_600)
                                    _typing.value = false
                                }
                            } else if (event.type == "message.deleted") {
                                removeMessage(event.message_id)
                            } else if (event.type in setOf(
                                    "message.created", "message.updated", "reaction.updated", "receipt.read",
                                ) && event.message_id.isNotBlank()) {
                                viewModelScope.launch { refreshOne(conversationId, event.message_id, event.type) }
                            }
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            disconnected.complete(Unit)
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            disconnected.complete(Unit)
                        }
                    })
                    disconnected.await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    _error.value = error.message.orEmpty()
                } finally {
                    realtimeSocket?.cancel()
                    realtimeSocket = null
                }
                if (isActive) {
                    delay(retryMs)
                    retryMs = (retryMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    private suspend fun refreshOne(conversationId: String, messageId: String, eventType: String) {
        // Our own sent message is already in the list (optimistically or from
        // the POST response), so its WebSocket echo does not need another GET.
        val alreadyPresent = _messages.value.any { it.id == messageId }
        if (eventType == "message.created" && alreadyPresent) return
        runCatching { repository.message(conversationId, messageId) }
            .onSuccess { incoming ->
                upsertMessage(incoming)
                if (eventType == "message.created") {
                    runCatching { repository.markRead(conversationId, incoming.id) }
                } else if (eventType == "receipt.read" && incoming.read_by_peer) {
                    // A read receipt applies to every earlier message from the
                    // same sender, not only the final message in the read batch.
                    _messages.value = _messages.value.map { current ->
                        if (current.sender_id == incoming.sender_id && current.created_at <= incoming.created_at) {
                            current.copy(read_by_peer = true)
                        } else current
                    }
                }
                _error.value = ""
            }
            .onFailure {
                if (eventType == "message.created") _error.value = it.message.orEmpty()
            }
    }

    private fun upsertMessage(message: ChatMessage) {
        _messages.value = (_messages.value.filterNot {
            it.id == message.id || (message.client_message_id.isNotBlank() && it.client_message_id == message.client_message_id)
        } + message).sortedByMessageTime()
    }

    private fun removeMessage(messageId: String) {
        if (messageId.isBlank()) return
        _messages.value = _messages.value.filterNot { it.id == messageId }.map { message ->
            if (message.reply_to == messageId) {
                message.copy(reply_preview = message.reply_preview?.copy(body = "این پیام حذف شده است", deleted = true))
            } else message
        }
    }

    fun stopRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
        typingResetJob?.cancel()
        typingResetJob = null
        realtimeSocket?.close(1000, "conversation closed")
        realtimeSocket = null
        realtimeConversationId = ""
        _typing.value = false
    }

    suspend fun sendText(conversationId: String, text: String, replyTo: String = "", username: String): Boolean {
        val body = text.trim()
        if (body.isEmpty()) return false
        val clientId = java.util.UUID.randomUUID().toString()
        val optimistic = ChatMessage(
            id = "local:$clientId",
            conversation_id = conversationId,
            sender = ir.pinbom.app.data.chat.ChatPeer(username = username, display_name = username),
            client_message_id = clientId,
            body = body,
            reply_to = replyTo,
            created_at = java.time.Instant.now().toString(),
        )
        _messages.value = _messages.value + optimistic
        _busy.value = true
        return try {
            val sent = repository.sendText(conversationId, body, replyTo, clientId)
            upsertMessage(sent)
            _error.value = ""
            true
        } catch (error: Throwable) {
            _messages.value = _messages.value.filterNot { it.id == optimistic.id }
            _error.value = error.message.orEmpty()
            false
        } finally {
            _busy.value = false
        }
    }

    suspend fun sendVoice(conversationId: String, file: java.io.File, durationMs: Int): Boolean {
        _busy.value = true
        return try {
            val media: ChatMedia = repository.uploadVoice(file, durationMs)
            val sent = repository.sendVoice(conversationId, media)
            upsertMessage(sent)
            _error.value = ""
            true
        } catch (error: Throwable) {
            _error.value = error.message.orEmpty()
            false
        } finally {
            _busy.value = false
        }
    }

    suspend fun typing(conversationId: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTypingSentAt < 1_200L) return
        lastTypingSentAt = now
        if (!repository.sendTypingRealtime(realtimeSocket, conversationId)) {
            runCatching { repository.typing(conversationId) }
        }
    }

    suspend fun markConversationRead(conversationId: String) {
        val last = _messages.value.lastOrNull() ?: return
        runCatching { repository.markRead(conversationId, last.id) }
    }

    suspend fun react(conversationId: String, messageId: String, emoji: String) {
        runCatching { repository.react(conversationId, messageId, emoji) }
            .onSuccess { runCatching { repository.message(conversationId, messageId) }.onSuccess(::upsertMessage) }
            .onFailure { _error.value = it.message.orEmpty() }
    }

    suspend fun edit(conversationId: String, messageId: String, body: String) {
        runCatching { repository.edit(conversationId, messageId, body) }
            .onSuccess(::upsertMessage)
            .onFailure { _error.value = it.message.orEmpty() }
    }

    suspend fun delete(conversationId: String, messageId: String) {
        runCatching { repository.delete(conversationId, messageId) }
            .onSuccess { removeMessage(messageId) }
            .onFailure { _error.value = it.message.orEmpty() }
    }
}

private fun List<ChatMessage>.sortedByMessageTime(): List<ChatMessage> =
    sortedWith(compareBy<ChatMessage>({ it.created_at }, { it.id }))
