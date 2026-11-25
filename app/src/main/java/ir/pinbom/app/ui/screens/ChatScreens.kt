package ir.pinbom.app.ui.screens

import android.Manifest
import android.net.Uri
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import ir.pinbom.app.R
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.data.chat.ChatConversation
import ir.pinbom.app.data.chat.ChatMessage
import ir.pinbom.app.data.remote.SearchUser
import ir.pinbom.app.ui.ChatConversationViewModel
import ir.pinbom.app.ui.ChatInboxViewModel
import ir.pinbom.app.ui.components.Avatar
import ir.pinbom.app.ui.components.imageUrl
import ir.pinbom.app.ui.nav.Routes
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.PinBomTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val chatEmojis = listOf("❤️", "😂", "🔥", "😍", "👏", "🥹", "👍", "🎉", "🤔", "😮", "🙏", "💯")

@Composable
fun ChatInboxScreen(
    nav: NavHostController,
    session: SessionManager,
    initialUserId: Long = 0L,
    forwardedPinId: Long = 0L,
    vm: ChatInboxViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.load()
        vm.startRealtime()
    }
    DisposableEffect(Unit) {
        onDispose { vm.stopRealtime() }
    }
    LaunchedEffect(search) {
        delay(350)
        if (search.isNotBlank()) vm.search(search) else vm.search("")
    }
    LaunchedEffect(initialUserId, forwardedPinId) {
        if (initialUserId > 0L) {
            runCatching { vm.openDirect(initialUserId) }
                .onSuccess { id ->
                    if (forwardedPinId > 0L) runCatching { vm.forward(id, forwardedPinId) }
                    nav.navigate(Routes.chat(id)) { launchSingleTop = true }
                }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        containerColor = PinBomTheme.colors.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dimens.screen, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.chat_messages), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.chat_subtitle), style = MaterialTheme.typography.labelMedium, color = PinBomTheme.colors.textSecondary)
                }
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.screen)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.chat_search)) },
                shape = RoundedCornerShape(18.dp),
            )
            Spacer(Modifier.height(12.dp))
            if (search.isNotBlank()) {
                Text(stringResource(R.string.chat_people), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (people.isEmpty()) {
                    Text(stringResource(R.string.chat_no_people), color = PinBomTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
                }
                people.forEach { person ->
                    PersonRow(person, session) {
                        scope.launch {
                            runCatching { vm.openDirect(person.id) }
                                .onSuccess { id ->
                                    if (forwardedPinId > 0L) runCatching { vm.forward(id, forwardedPinId) }
                                    nav.navigate(Routes.chat(id))
                                }
                                .onFailure { vm.load() }
                        }
                    }
                }
                Divider(Modifier.padding(vertical = 10.dp), color = PinBomTheme.colors.divider)
                Text(stringResource(R.string.chat_conversations), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (items.isEmpty() && !loading && search.isBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.chat_no_conversations), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.chat_start_hint), color = PinBomTheme.colors.textSecondary)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(items, key = { it.id }) { conversation ->
                        ConversationRow(conversation, session) {
                            scope.launch {
                                if (forwardedPinId > 0L) runCatching { vm.forward(conversation.id, forwardedPinId) }
                                nav.navigate(Routes.chat(conversation.id))
                            }
                        }
                    }
                }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun PersonRow(person: SearchUser, session: SessionManager, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(person.display_name.ifBlank { person.username }, url = person.avatar_url, baseUrl = session.baseUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(person.display_name.ifBlank { person.username }, fontWeight = FontWeight.SemiBold)
            Text("@${person.username}", color = PinBomTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.chat_send_message), color = PinBomTheme.colors.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ConversationRow(conversation: ChatConversation, session: SessionManager, onClick: () -> Unit) {
    val name = conversation.peer.display_name.ifBlank { conversation.peer.username.ifBlank { conversation.title } }
    val defaultName = stringResource(R.string.chat_default_name)
    val preview = stringResource(R.string.chat_start_conversation)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, url = conversation.peer.avatar_url, baseUrl = session.baseUrl, size = 54.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name.ifBlank { defaultName }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(conversation.last_message.ifBlank { preview }, style = MaterialTheme.typography.bodySmall, color = PinBomTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(chatClock(conversation.last_message_at), style = MaterialTheme.typography.labelSmall, color = PinBomTheme.colors.textSecondary)
            if (conversation.unread_count > 0) {
                Spacer(Modifier.height(5.dp))
                Box(Modifier.clip(CircleShape).background(PinBomTheme.colors.primary).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(conversation.unread_count.coerceAtMost(99).toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ChatConversationScreen(
    nav: NavHostController,
    conversationId: String,
    session: SessionManager,
    vm: ChatConversationViewModel = hiltViewModel(),
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val typing by vm.typing.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var actionTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editingTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editingText by remember { mutableStateOf("") }
    var emojiExpanded by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }

    suspend fun refreshNow() { vm.refresh(conversationId) }
    LaunchedEffect(conversationId) {
        refreshNow()
        vm.startRealtime(conversationId)
    }
    DisposableEffect(conversationId) {
        onDispose { vm.stopRealtime() }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.release() }
            recorder = null
        }
    }

    fun startRecording() {
        runCatching {
            val file = File(context.cacheDir, "bumo-voice-${System.currentTimeMillis()}.m4a")
            val activeRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setMaxDuration(10 * 60 * 1000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = activeRecorder
            recordingFile = file
            recordingStartedAt = SystemClock.elapsedRealtime()
            recording = true
        }.onFailure { recording = false }
    }
    fun stopRecordingAndSend() {
        val duration = (SystemClock.elapsedRealtime() - recordingStartedAt).toInt().coerceIn(0, 600_000)
        val file = recordingFile
        runCatching { recorder?.stop() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        recording = false
        recordingFile = null
        if (file != null && file.exists() && file.length() > 0) {
            scope.launch {
                vm.sendVoice(conversationId, file, duration)
                file.delete()
            }
        } else file?.delete()
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }

    Scaffold(
        containerColor = PinBomTheme.colors.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.chat_open_conversation), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(if (typing) stringResource(R.string.chat_typing) else stringResource(R.string.chat_private_message), color = PinBomTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { scope.launch { refreshNow() } }) { Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.chat_more)) }
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(PinBomTheme.colors.surface).imePadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
                replyTarget?.let { target ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Reply, contentDescription = null, tint = PinBomTheme.colors.primary)
                        Text(stringResource(R.string.chat_reply_to, target.body.take(60)), modifier = Modifier.weight(1f), color = PinBomTheme.colors.textSecondary, maxLines = 1)
                        TextButton(onClick = { replyTarget = null }) { Text(stringResource(R.string.chat_reply_cancel)) }
                    }
                }
                if (recording) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.chat_recording), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { stopRecordingAndSend() }) { Text(stringResource(R.string.chat_send_voice)) }
                    }
                }
                if (emojiExpanded) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        chatEmojis.forEach { emoji ->
                            Text(emoji, modifier = Modifier.clickable { draft = (draft + emoji).take(4000) }.padding(4.dp), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { value -> draft = value.take(4000); if (value.isNotBlank()) scope.launch { vm.typing(conversationId) } },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.chat_composer_hint)) },
                        maxLines = 5,
                        shape = RoundedCornerShape(22.dp),
                    )
                    IconButton(onClick = { emojiExpanded = !emojiExpanded }) { Text("😊", style = MaterialTheme.typography.titleLarge) }
                    if (draft.isBlank()) {
                        IconButton(
                            enabled = !busy,
                            onClick = {
                                if (recording) stopRecordingAndSend()
                                else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(if (recording) MaterialTheme.colorScheme.error else PinBomTheme.colors.primary),
                        ) {
                            Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = stringResource(R.string.chat_voice), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        IconButton(
                            enabled = !busy,
                            onClick = {
                                val text = draft.trim()
                                if (text.isNotBlank()) scope.launch {
                                    vm.sendText(conversationId, text, replyTarget?.id.orEmpty())
                                    draft = ""
                                    replyTarget = null
                                }
                            },
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(PinBomTheme.colors.primary),
                        ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send), tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        mine = message.sender.username.equals(session.username, ignoreCase = true),
                        baseUrl = session.baseUrl,
                        accessToken = session.accessToken,
                        onLongPress = { actionTarget = message },
                        onPinClick = { pinId -> nav.navigate(Routes.pin(pinId)) },
                    )
                }
            }
            if (messages.isEmpty() && !busy) {
                Text(stringResource(R.string.chat_first_message), modifier = Modifier.align(Alignment.CenterHorizontally).padding(18.dp), color = PinBomTheme.colors.textSecondary)
            }
        }
    }

    editingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { editingTarget = null },
            title = { Text(stringResource(R.string.chat_edit_message)) },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it.take(4000) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val edited = editingText.trim()
                    if (edited.isNotBlank()) scope.launch { vm.edit(conversationId, target.id, edited) }
                    editingTarget = null
                }) { Text(stringResource(R.string.chat_save)) }
            },
            dismissButton = { TextButton(onClick = { editingTarget = null }) { Text(stringResource(R.string.chat_close)) } },
        )
    }

    actionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(stringResource(R.string.chat_manage_message)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chatEmojis.take(8).forEach { emoji ->
                            Text(emoji, modifier = Modifier.clickable {
                                actionTarget = null
                                scope.launch { vm.react(conversationId, target.id, emoji) }
                            }.padding(4.dp), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    TextButton(onClick = { replyTarget = target; actionTarget = null }) { Text(stringResource(R.string.chat_reply)) }
                    if (target.sender.username.equals(session.username, true)) {
                        if (target.body.isNotBlank()) {
                            TextButton(onClick = {
                                editingText = target.body
                                editingTarget = target
                                actionTarget = null
                            }) { Text(stringResource(R.string.chat_edit)) }
                        }
                        TextButton(onClick = {
                            actionTarget = null
                            scope.launch { vm.delete(conversationId, target.id) }
                        }) { Text(stringResource(R.string.chat_delete), color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionTarget = null }) { Text(stringResource(R.string.chat_close)) } },
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    baseUrl: String,
    accessToken: String?,
    onLongPress: () -> Unit,
    onPinClick: (Long) -> Unit,
) {
    val colors = PinBomTheme.colors
    val nowLabel = stringResource(R.string.chat_now)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 5.dp, bottomEnd = if (mine) 5.dp else 20.dp))
                .background(if (mine) colors.primary.copy(alpha = if (colors.isDark) 0.28f else 0.12f) else colors.surfaceGrey)
                .pointerInput(message.id) { detectTapGestures(onLongPress = { onLongPress() }) }
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            message.forwarded_pin?.let { pin ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).clickable { onPinClick(pin.id) }.padding(8.dp)) {
                    Text("📌 ${stringResource(R.string.chat_pin_forwarded)}", color = colors.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    if (pin.image_url.isNotBlank()) {
                        AsyncImage(model = imageUrl(baseUrl, pin.image_url), contentDescription = pin.title, modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp)))
                    }
                    Text(pin.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("@${pin.author_username}", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                if (message.body.isNotBlank()) Spacer(Modifier.height(7.dp))
            }
            message.media.forEach { media ->
                if (media.type == "voice") {
                    VoiceMessage(media.url, media.duration_ms, baseUrl, accessToken)
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (message.reply_to.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(Icons.Default.Reply, contentDescription = null, tint = colors.primary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.chat_reply_preview), color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (message.body.isNotBlank()) Text(message.body, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(message.created_at.substringAfter('T').take(5).ifBlank { "اکنون" }, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                if (mine) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (message.read_by_peer) Icons.Default.DoneAll else Icons.Default.Check,
                        contentDescription = if (message.read_by_peer) stringResource(R.string.chat_read) else stringResource(R.string.chat_sent),
                        tint = if (message.read_by_peer) colors.primary else colors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (message.reactions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    message.reactions.forEach { reaction ->
                        Text("${reaction.emoji} ${reaction.count}", style = MaterialTheme.typography.labelSmall, color = if (reaction.mine) colors.primary else colors.textSecondary)
                    }
                }
            }
        }
    }
}


@Composable
private fun VoiceMessage(url: String, durationMs: Int, baseUrl: String, accessToken: String?) {
    val context = LocalContext.current
    var playing by remember(url) { mutableStateOf(false) }
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(url) {
        onDispose { runCatching { player?.release() }; player = null }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(220.dp)) {
        IconButton(onClick = {
            if (playing) {
                runCatching { player?.pause() }
                playing = false
            } else {
                runCatching {
                    val resolved = imageUrl(baseUrl, url)
                    val next = MediaPlayer()
                    val headers = accessToken?.takeIf { it.isNotBlank() }?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
                    next.setDataSource(context, Uri.parse(resolved), headers)
                    next.setOnPreparedListener { it.start(); playing = true }
                    next.setOnCompletionListener { playing = false }
                    next.prepareAsync()
                    player = next
                }
            }
        }) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = stringResource(R.string.chat_voice_playback), tint = PinBomTheme.colors.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(if (playing) stringResource(R.string.chat_playing) else stringResource(R.string.chat_voice), fontWeight = FontWeight.SemiBold)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall, color = PinBomTheme.colors.textSecondary)
        }
        Text("〰〰〰", color = PinBomTheme.colors.primary)
    }
}

private fun chatClock(value: String): String = value.substringAfterLast('T').substringAfterLast(' ').take(5).takeIf { it.length == 5 } ?: ""

private fun formatDuration(durationMs: Int): String {
    val seconds = (durationMs / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
