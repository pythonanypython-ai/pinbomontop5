package ir.pinbom.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.pinbom.app.data.chat.ChatConversation
import ir.pinbom.app.data.chat.ChatMessage
import ir.pinbom.app.data.chat.ChatMedia
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

    fun load() = viewModelScope.launch {
        _loading.value = true
        runCatching { repository.conversations() }
            .onSuccess { _items.value = it.items; _error.value = "" }
            .onFailure { _error.value = it.message.orEmpty() }
        _loading.value = false
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
                            viewModelScope.launch { load() }
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val event = runCatching { repository.decodeRealtimeEvent(text) }.getOrNull() ?: return
                            if (event.type in setOf(
                                    "conversation.created", "message.created", "message.updated",
                                    "message.deleted", "reaction.updated", "receipt.read",
                                )) {
                                viewModelScope.launch { load() }
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
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow("")
    val error = _error.asStateFlow()
    private var realtimeJob: Job? = null
    private var realtimeSocket: WebSocket? = null
    private var typingResetJob: Job? = null
    private var lastTypingSentAt = 0L
    private var realtimeConversationId = ""

    suspend fun refresh(conversationId: String) {
        runCatching { repository.messages(conversationId) }
            .onSuccess { page ->
                _messages.value = page.items
                _typing.value = page.typing_users.isNotEmpty()
                page.items.lastOrNull()?.let { repository.markRead(conversationId, it.id) }
                _error.value = ""
            }
            .onFailure { _error.value = it.message.orEmpty() }
    }

    fun startRealtime(conversationId: String) {
        if (realtimeJob?.isActive == true && realtimeConversationId == conversationId) return
        stopRealtime()
        realtimeConversationId = conversationId
        realtimeJob = viewModelScope.launch {
            var retryMs = 1_000L
            while (isActive) {
                val disconnected = CompletableDeferred<Unit>()
                try {
                    realtimeSocket = repository.openRealtime(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            retryMs = 1_000L
                            viewModelScope.launch { refresh(conversationId) }
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
                            } else if (event.type in setOf(
                                    "message.created", "message.updated", "message.deleted",
                                    "reaction.updated", "receipt.read",
                                )) {
                                viewModelScope.launch { refresh(conversationId) }
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

    suspend fun sendText(conversationId: String, text: String, replyTo: String = "") {
        _busy.value = true
        try {
            repository.sendText(conversationId, text, replyTo)
            refresh(conversationId)
        } catch (error: Throwable) { _error.value = error.message.orEmpty() }
        finally { _busy.value = false }
    }

    suspend fun sendVoice(conversationId: String, file: java.io.File, durationMs: Int) {
        _busy.value = true
        try {
            val media: ChatMedia = repository.uploadVoice(file, durationMs)
            repository.sendVoice(conversationId, media)
            refresh(conversationId)
        } catch (error: Throwable) { _error.value = error.message.orEmpty() }
        finally { _busy.value = false }
    }

    suspend fun typing(conversationId: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTypingSentAt < 1_200L) return
        lastTypingSentAt = now
        if (!repository.sendTypingRealtime(realtimeSocket, conversationId)) {
            runCatching { repository.typing(conversationId) }
        }
    }

    suspend fun react(conversationId: String, messageId: String, emoji: String) {
        runCatching { repository.react(conversationId, messageId, emoji) }.onSuccess { refresh(conversationId) }
    }
    suspend fun edit(conversationId: String, messageId: String, body: String) {
        runCatching { repository.edit(conversationId, messageId, body) }.onSuccess { refresh(conversationId) }
    }
    suspend fun delete(conversationId: String, messageId: String) {
        runCatching { repository.delete(conversationId, messageId) }.onSuccess { refresh(conversationId) }
    }
}
