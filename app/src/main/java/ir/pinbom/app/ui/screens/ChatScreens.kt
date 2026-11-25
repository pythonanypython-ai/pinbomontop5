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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    nav: NavHostController,
    conversationId: String,
    session: SessionManager,
    vm: ChatConversationViewModel = hiltViewModel(),
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val typing by vm.typing.collectAsStateWithLifecycle()
    val peerFromServer by vm.peer.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loadingOlder by vm.loadingOlder.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var actionTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editingTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editingText by remember { mutableStateOf("") }
    var emojiExpanded by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var initialTimelineReady by remember(conversationId) { mutableStateOf(false) }
    var initialScrollDone by remember(conversationId) { mutableStateOf(false) }

    val peer = peerFromServer ?: remember(messages, session.username) {
        messages.firstOrNull { !it.sender.username.equals(session.username, ignoreCase = true) }?.sender
    }
    val peerTitle = peer?.let { it.display_name.ifBlank { it.username } }
        .orEmpty().ifBlank { stringResource(R.string.chat_open_conversation) }
    val visibleMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.body.contains(searchQuery, ignoreCase = true) }
    }
    val isAtBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.totalItemsCount == 0 || layout.visibleItemsInfo.lastOrNull()?.index?.let { it >= layout.totalItemsCount - 2 } == true
        }
    }

    LaunchedEffect(conversationId) {
        vm.startRealtime(conversationId)
        vm.refresh(conversationId)
    }
    DisposableEffect(conversationId) {
        onDispose { vm.stopRealtime() }
    }
    // Revalidate the open conversation after the app returns to foreground.
    // This also updates forwarded-pin placeholders after admin moderation.
    DisposableEffect(lifecycleOwner, conversationId) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (firstResume) firstResume = false else scope.launch { vm.refresh(conversationId) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(messages.size, searchQuery) {
        if (searchQuery.isBlank() && visibleMessages.isNotEmpty()) {
            if (!initialScrollDone) {
                listState.scrollToItem(visibleMessages.lastIndex)
                initialScrollDone = true
            } else if (isAtBottom) {
                listState.animateScrollToItem(visibleMessages.lastIndex)
            }
            initialTimelineReady = true
        }
    }
    // Keep older history available without blocking the current timeline. The
    // snapshot observes the current oldest message as well as scroll position,
    // so it never captures the initial empty list in a stale coroutine closure.
    LaunchedEffect(conversationId, listState) {
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex, messages.firstOrNull()?.id.orEmpty(), searchQuery.isBlank() && initialTimelineReady)
        }
            .distinctUntilChanged()
            .collect { (firstIndex, anchorId, pagingEnabled) ->
                if (pagingEnabled && firstIndex == 0 && anchorId.isNotBlank()) {
                    val anchorOffset = listState.firstVisibleItemScrollOffset
                    val added = vm.loadOlder(conversationId)
                    if (added > 0) {
                        val newIndex = vm.messages.value.indexOfFirst { it.id == anchorId }
                        if (newIndex >= 0) listState.scrollToItem(newIndex, anchorOffset)
                    }
                }
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.release() }
            recorder = null
        }
    }

    fun startRecording() {
        runCatching {
            val file = File(context.cacheDir, "pinbom-voice-${System.currentTimeMillis()}.m4a")
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
            Box(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                if (searchOpen) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(start = 54.dp, end = 58.dp),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.chat_search_messages)) },
                        shape = RoundedCornerShape(18.dp),
                    )
                } else {
                    Row(
                        Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (peer != null) {
                            Avatar(peerTitle, url = peer.avatar_url, baseUrl = session.baseUrl, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(peerTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (typing) stringResource(R.string.chat_typing) else stringResource(R.string.chat_private_message),
                                color = if (typing) PinBomTheme.colors.primary else PinBomTheme.colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Box(Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { if (searchOpen) { searchOpen = false; searchQuery = "" } else moreMenuExpanded = true }) {
                        Icon(if (searchOpen) Icons.Default.Close else Icons.Default.MoreVert, contentDescription = stringResource(R.string.chat_more))
                    }
                    DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_search_messages)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = { moreMenuExpanded = false; searchOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_mark_read)) },
                            leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                            onClick = { moreMenuExpanded = false; scope.launch { vm.markConversationRead(conversationId) } },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_refresh)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = { moreMenuExpanded = false; scope.launch { vm.refresh(conversationId) } },
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = PinBomTheme.colors.surface, shadowElevation = 8.dp) {
                Column(
                    Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    androidx.compose.animation.AnimatedVisibility(visible = replyTarget != null) {
                        val target = replyTarget
                        if (target != null) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(PinBomTheme.colors.surfaceGrey).padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.width(3.dp).height(34.dp).clip(CircleShape).background(PinBomTheme.colors.primary))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(target.sender.display_name.ifBlank { target.sender.username }, color = PinBomTheme.colors.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(target.body.ifBlank { stringResource(R.string.chat_reply_preview) }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = PinBomTheme.colors.textSecondary)
                                }
                                IconButton(onClick = { replyTarget = null }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_reply_cancel)) }
                            }
                        }
                    }
                    if (recording) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.chat_recording), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { stopRecordingAndSend() }) { Text(stringResource(R.string.chat_send_voice)) }
                        }
                    }
                    if (emojiExpanded) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            chatEmojis.forEach { emoji ->
                                Text(emoji, modifier = Modifier.clickable { draft = (draft + emoji).take(4000) }.padding(4.dp), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { value -> draft = value.take(4000); if (value.isNotBlank()) scope.launch { vm.typing(conversationId) } },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.chat_composer_hint), textAlign = TextAlign.Start) },
                            maxLines = 5,
                            shape = RoundedCornerShape(24.dp),
                        )
                        IconButton(onClick = { emojiExpanded = !emojiExpanded }, modifier = Modifier.size(46.dp)) {
                            Text("😊", style = MaterialTheme.typography.titleLarge)
                        }
                        if (draft.isBlank()) {
                            IconButton(
                                enabled = !busy,
                                onClick = {
                                    if (recording) stopRecordingAndSend()
                                    else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier.size(46.dp).clip(CircleShape).background(if (recording) MaterialTheme.colorScheme.error else PinBomTheme.colors.primary),
                            ) {
                                Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = stringResource(R.string.chat_voice), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        } else {
                            IconButton(
                                enabled = !busy,
                                onClick = {
                                    val text = draft.trim()
                                    if (text.isNotBlank()) scope.launch {
                                        val sent = vm.sendText(conversationId, text, replyTarget?.id.orEmpty(), session.username)
                                        if (sent) { draft = ""; replyTarget = null; emojiExpanded = false }
                                    }
                                },
                                modifier = Modifier.size(46.dp).clip(CircleShape).background(PinBomTheme.colors.primary),
                            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send), tint = MaterialTheme.colorScheme.onPrimary) }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), textAlign = TextAlign.Center)
            }
            if (loadingOlder) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(5.dp).size(18.dp), strokeWidth = 2.dp)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleMessages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        mine = message.sender.username.equals(session.username, ignoreCase = true),
                        baseUrl = session.baseUrl,
                        accessToken = session.accessToken,
                        onLongPress = { actionTarget = message },
                        onSwipeReply = { replyTarget = message },
                        onReplyPreviewClick = { replyId ->
                            val targetIndex = messages.indexOfFirst { it.id == replyId }
                            if (targetIndex >= 0) scope.launch { listState.animateScrollToItem(targetIndex) }
                        },
                        onPinClick = { pinId -> nav.navigate(Routes.pin(pinId)) },
                    )
                }
                if (visibleMessages.isEmpty() && searchQuery.isNotBlank()) {
                    item {
                        Text(stringResource(R.string.chat_no_search_matches), modifier = Modifier.fillMaxWidth().padding(24.dp), color = PinBomTheme.colors.textSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
            if (messages.isEmpty() && !busy && searchQuery.isBlank()) {
                Text(stringResource(R.string.chat_first_message), modifier = Modifier.fillMaxWidth().padding(18.dp), color = PinBomTheme.colors.textSecondary, textAlign = TextAlign.Center)
            }
        }
    }

    editingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { editingTarget = null },
            title = { Text(stringResource(R.string.chat_edit_message)) },
            text = {
                OutlinedTextField(value = editingText, onValueChange = { editingText = it.take(4000) }, modifier = Modifier.fillMaxWidth(), maxLines = 5)
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

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.chat_delete)) },
            text = { Text(stringResource(R.string.chat_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; scope.launch { vm.delete(conversationId, target.id) } }) {
                    Text(stringResource(R.string.chat_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.chat_close)) } },
        )
    }

    actionTarget?.let { target ->
        ModalBottomSheet(onDismissRequest = { actionTarget = null }, containerColor = PinBomTheme.colors.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.chat_manage_message), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    chatEmojis.forEach { emoji ->
                        Surface(
                            modifier = Modifier.size(44.dp).clickable {
                                actionTarget = null
                                scope.launch { vm.react(conversationId, target.id, emoji) }
                            },
                            shape = CircleShape,
                            color = PinBomTheme.colors.surfaceGrey,
                        ) { Box(contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.titleLarge) } }
                    }
                }
                HorizontalDivider(color = PinBomTheme.colors.divider)
                TextButton(onClick = { replyTarget = target; actionTarget = null }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Reply, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_reply))
                    }
                }
                if (target.body.isNotBlank()) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(target.body)); actionTarget = null }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.chat_copy))
                        }
                    }
                }
                if (target.sender.username.equals(session.username, true)) {
                    if (target.body.isNotBlank()) {
                        TextButton(onClick = { editingText = target.body; editingTarget = target; actionTarget = null }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.chat_edit))
                            }
                        }
                    }
                    TextButton(onClick = { deleteTarget = target; actionTarget = null }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.chat_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    baseUrl: String,
    accessToken: String?,
    onLongPress: () -> Unit,
    onSwipeReply: () -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onPinClick: (Long) -> Unit,
) {
    val colors = PinBomTheme.colors
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 68.dp.toPx() }
    val maxSwipeOffset = with(density) { 110.dp.toPx() }
    var swipeOffset by remember(message.id) { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(
        topStart = 19.dp,
        topEnd = 19.dp,
        bottomStart = if (mine) 19.dp else 5.dp,
        bottomEnd = if (mine) 5.dp else 19.dp,
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        if (kotlin.math.abs(swipeOffset) > 12f) {
            Icon(
                Icons.Default.Reply,
                contentDescription = null,
                tint = colors.primary.copy(alpha = (kotlin.math.abs(swipeOffset) / 90f).coerceIn(0.25f, 1f)),
                modifier = Modifier.align(if (swipeOffset > 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 12.dp),
            )
        }
        Column(
            Modifier
                .widthIn(min = 56.dp, max = 360.dp)
                .graphicsLayer { translationX = swipeOffset }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            swipeOffset = (swipeOffset + dragAmount).coerceIn(-maxSwipeOffset, maxSwipeOffset)
                            change.consume()
                        },
                        onDragEnd = {
                            if (kotlin.math.abs(swipeOffset) >= swipeThreshold) onSwipeReply()
                            swipeOffset = 0f
                        },
                        onDragCancel = { swipeOffset = 0f },
                    )
                }
                .pointerInput(message.id) { detectTapGestures(onLongPress = { onLongPress() }) }
                .clip(shape)
                .background(if (mine) colors.primary.copy(alpha = if (colors.isDark) 0.26f else 0.105f) else colors.surfaceGrey)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            message.forwarded_pin?.let { pin ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
                        .clickable { onPinClick(pin.id) }.padding(8.dp),
                ) {
                    Text("📌 ${stringResource(R.string.chat_pin_forwarded)}", color = colors.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    if (pin.image_url.isNotBlank()) {
                        AsyncImage(model = imageUrl(baseUrl, pin.image_url), contentDescription = pin.title, modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp)))
                    }
                    Text(pin.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("@${pin.author_username}", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                if (message.body.isNotBlank()) Spacer(Modifier.height(7.dp))
            }
            if (message.forwarded_pin_removed) {
                Text(stringResource(R.string.chat_deleted_pin), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            message.reply_preview?.let { preview ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface.copy(alpha = 0.72f))
                        .clickable { onReplyPreviewClick(preview.id) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(3.dp).height(34.dp).clip(CircleShape).background(colors.primary))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(preview.sender_name, color = colors.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(preview.body.ifBlank { stringResource(R.string.chat_reply_preview) }, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
            message.media.forEach { media ->
                if (media.type == "voice") {
                    VoiceMessage(media.url, media.duration_ms, baseUrl, accessToken)
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (message.body.isNotBlank()) {
                Text(message.body, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
            }
            if (message.reactions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState())) {
                    message.reactions.forEach { reaction ->
                        Surface(shape = CircleShape, color = colors.surface.copy(alpha = 0.8f)) {
                            Text(
                                "${reaction.emoji} ${reaction.count}",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (reaction.mine) colors.primary else colors.textSecondary,
                            )
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (message.edited_at.isNotBlank()) {
                    Text(stringResource(R.string.chat_edited), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(chatClock(message.created_at).ifBlank { stringResource(R.string.chat_now) }, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
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
