package id.kitachat.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kitachat.data.BASE_URL
import id.kitachat.data.RetrofitClient
import id.kitachat.utils.SoundManager
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import androidx.compose.foundation.layout.imePadding
import androidx.core.content.FileProvider
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.DialogProperties

// --- MODEL DATA CHAT ---
data class ChatMessage(
    val id: String,
    val senderUserId: String,
    val sender: String,
    val text: String,

    val messageType: String = "text",

    val imageUrl: String? = null,
    val fileUrl: String? = null,
    val audioUrl: String? = null,

    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null,

    val isDeleted: Boolean = false,
    val isRead: Boolean = false,

    val time: String,

    val mine: Boolean
)

private fun buildMediaUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return if (url.startsWith("http")) url else BASE_URL.removeSuffix("/") + url
}

// SPRINT 1.8.2: Fungsi Preview Reply yang Mendukung Caption/Media
private fun getReplyPreview(
    message: ChatMessage
): String {
    return when {
        message.messageType == "audio" ->
            "🎤 Voice Note"

        message.messageType == "image" ->
            if (message.text.isNotBlank())
                "🖼 ${message.text}"
            else
                "🖼 Foto"

        message.messageType == "pdf" ->
            if (message.text.isNotBlank())
                "📄 ${message.text}"
            else
                "📄 Dokumen"

        message.text.isNotBlank() ->
            message.text

        else ->
            "Lampiran"
    }
}

// SPRINT 1.8.5: Helper untuk Mendeteksi Label & Ikon Tipe File Dokumen
private fun getFileLabel(
    url: String?
): Pair<String, String> {
    val value = url.orEmpty().lowercase()
    return when {
        value.endsWith(".pdf") ->
            "📄" to "PDF Document"

        value.endsWith(".doc") ||
        value.endsWith(".docx") ->
            "📝" to "Word Document"

        value.endsWith(".xls") ||
        value.endsWith(".xlsx") ->
            "📊" to "Excel Spreadsheet"

        value.endsWith(".ppt") ||
        value.endsWith(".pptx") ->
            "📈" to "PowerPoint"

        value.endsWith(".zip") ||
        value.endsWith(".rar") ->
            "📦" to "Archive"

        value.endsWith(".mp3") ||
        value.endsWith(".wav") ->
            "🎵" to "Audio File"

        else ->
            "📎" to "Dokumen"
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatTab(displayName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("kitachat_prefs", Context.MODE_PRIVATE) }
    
    val userId = prefs.getString("user_id", "") ?: ""
    val sessionToken = prefs.getString("session_token", "") ?: ""

    var draft by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    // State Bottom Sheet (Sprint 1.2)
    var showAttachmentMenu by remember { mutableStateOf(false) }

    // SPRINT 1.8.4: State Fullscreen Image Viewer
    var fullscreenImageUrl by remember {
        mutableStateOf<String?>(null)
    }

    // State URI Kamera (Sprint 1.3)
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // State Perekaman Voice Note (Sprint 1.4)
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordedAudioFile by remember { mutableStateOf<File?>(null) }
    var recordingDuration by remember { mutableStateOf(0) }
    var recordingHandler by remember { mutableStateOf<Handler?>(null) }

    val messages = remember { mutableStateListOf<ChatMessage>() }

    val onlineUsers = remember { mutableStateListOf<String>() }
    var typingIndicator by remember { mutableStateOf("") }
    var currentPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Player Lebih Stabil (Sprint 1.6)
    fun playAudio(url: String?) {
        if (url.isNullOrBlank()) {
            return
        }
        try {
            currentPlayer?.stop()
            currentPlayer?.release()
            currentPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener {
                    start()
                }
                prepareAsync()
                setOnCompletionListener {
                    release()
                    currentPlayer = null
                }
            }
        } catch (e: Exception) {
            Log.e("VoicePlayer", e.message ?: "")
        }
    }

    val listState = rememberLazyListState()

    fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                try {
                    listState.animateScrollToItem(messages.size - 1)
                } catch (_: Exception) {}
            }
        }
    }

    // Launcher Dokumen (Sprint 1.2)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedFileUri = it
            var name = "Dokumen"
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = c.getString(index)
                    }
                }
            }
            selectedFileName = name
        }
    }

    // Launcher Galeri Foto (Sprint 1.2)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedFileUri = it
            selectedFileName = "Foto_${System.currentTimeMillis()}"
        }
    }

    // Fungsi Membuat File Foto yang Dioptimalkan (Tanpa Duplikasi)
    fun createImageUri(): Uri {
        val imageFile = File(
            context.cacheDir,
            "IMG_${System.currentTimeMillis()}.jpg"
        )
        try {
            if (!imageFile.exists()) {
                imageFile.createNewFile()
            }
        } catch (e: Exception) {
            Log.e("Camera", "Gagal membuat file foto", e)
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
    }

    // Launcher Kamera (Sprint 1.3)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let {
                selectedFileUri = it
                selectedFileName = "Foto_${System.currentTimeMillis()}.jpg"
            }
        }
    }

    // Fungsi Start Recording (Sprint 1.4)
    fun startRecording() {
        try {
            val audioFile = File(
                context.cacheDir,
                "voice_${System.currentTimeMillis()}.m4a"
            )
            recordedAudioFile = audioFile
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
            recordingDuration = 0
            val handler = Handler(Looper.getMainLooper())
            recordingHandler = handler
            handler.post(
                object : Runnable {
                    override fun run() {
                        if (isRecording) {
                            recordingDuration++
                            handler.postDelayed(this, 1000)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("VoiceNote", "startRecording", e)
        }
    }

    // Fungsi Stop Recording (Sprint 1.4)
    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
        } catch (_: Exception) {
        }
        recordingHandler?.removeCallbacksAndMessages(null)
        isRecording = false
    }

    // Konversi Audio Menjadi Uri (Sprint 1.5)
    fun audioFileToUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }

    // Fungsi Upload Voice Note (Sprint 1.5)
    fun prepareVoiceNoteForSend() {
        recordedAudioFile?.let {
            selectedFileUri = audioFileToUri(it)
            selectedFileName = it.name
        }
    }

    // SPRINT 1.8.6.1: Audit & Perbaikan Stabilitas Socket.IO Realtime
    val socket = remember(userId, sessionToken) {
        try {
            val uri = URI.create(BASE_URL)
            val options = IO.Options().apply {
                transports = arrayOf("websocket", "polling")
                auth = mapOf("userId" to userId, "sessionToken" to sessionToken)
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                forceNew = false
            }
            IO.socket(uri, options).connect()
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(socket) {
        val connectListener = Emitter.Listener {
            Log.d("KitachatSocket", "CONNECTED")
        }

        val disconnectListener = Emitter.Listener {
            Log.d("KitachatSocket", "DISCONNECTED")
        }

        val chatListener = Emitter.Listener { args ->
            Log.d("KitachatRealtime", "Message received")
            if (args.isNotEmpty()) {
                val data = when (val arg = args[0]) {
                    is JSONObject -> arg
                    is JSONArray -> if (arg.length() > 0) arg.getJSONObject(0) else null
                    else -> null
                }
                data?.let {
                    val msgId = it.optString("id", System.currentTimeMillis().toString())
                    val senderUid = it.optString("user_id", "")
                    val senderName = it.optString("sender", it.optString("name", "Keluarga"))
                    val textContent = it.optString("text", it.optString("message", ""))
                    val imgUrl = it.optString("image_url", "")
                    val fUrl = it.optString("file_url", "")
                    val audUrl = it.optString("audio_url", "")
                    val mType = it.optString("message_type", if (imgUrl.isNotBlank()) "image" else if (audUrl.isNotBlank()) "audio" else if (fUrl.isNotBlank()) "pdf" else "text")
                    val repId = it.optString("reply_to_id", "")
                    val repSender = it.optString("reply_to_sender", "")
                    val repText = it.optString("reply_to_text", "")
                    val delStatus = it.optBoolean("is_deleted", false)
                    val readStatus = it.optBoolean("is_read", false)
                    val timeStr = it.optString("time", "Baru saja")

                    Handler(Looper.getMainLooper()).post {
                        val index = messages.indexOfFirst { m -> m.id == msgId }
                        if (index != -1) {
                            if (delStatus) {
                                messages[index] = messages[index].copy(isDeleted = true, text = "Pesan ini telah dihapus")
                            }
                        } else {
                            val isMine = senderUid == userId
                            messages.add(
                                ChatMessage(
                                    id = msgId,
                                    senderUserId = senderUid,
                                    sender = senderName,
                                    text = textContent,
                                    messageType = mType,
                                    imageUrl = imgUrl.ifBlank { null },
                                    fileUrl = fUrl.ifBlank { null },
                                    audioUrl = audUrl.ifBlank { null },
                                    replyToId = repId.ifBlank { null },
                                    replyToSender = repSender.ifBlank { null },
                                    replyToText = repText.ifBlank { null },
                                    isDeleted = delStatus,
                                    isRead = readStatus,
                                    time = timeStr,
                                    mine = isMine
                                )
                            )
                            scrollToBottom()

                            if (!isMine && !delStatus && !readStatus) {
                                SoundManager.playMessageSound(context)
                                socket?.emit(
                                    "message_read",
                                    JSONObject().apply {
                                        put("messageId", msgId)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        val historyListener = Emitter.Listener { args ->
            if (args.isNotEmpty() && args[0] is JSONArray) {
                val historyArray = args[0] as JSONArray
                Handler(Looper.getMainLooper()).post {
                    messages.clear()
                    for (i in 0 until historyArray.length()) {
                        val it = historyArray.getJSONObject(i)
                        val msgId = it.optString("id", System.currentTimeMillis().toString())
                        val senderUid = it.optString("user_id", "")
                        val senderName = it.optString("sender", it.optString("name", "Keluarga"))
                        val textContent = it.optString("text", it.optString("message", ""))
                        val imgUrl = it.optString("image_url", "")
                        val fUrl = it.optString("file_url", "")
                        val audUrl = it.optString("audio_url", "")
                        val mType = it.optString("message_type", if (imgUrl.isNotBlank()) "image" else if (audUrl.isNotBlank()) "audio" else if (fUrl.isNotBlank()) "pdf" else "text")
                        val readStatus = it.optBoolean("is_read", false)
                        val timeStr = it.optString("time", "Baru saja")
                        val isMine = senderUid == userId

                        messages.add(
                            ChatMessage(
                                id = msgId,
                                senderUserId = senderUid,
                                sender = senderName,
                                text = textContent,
                                messageType = mType,
                                imageUrl = imgUrl.ifBlank { null },
                                fileUrl = fUrl.ifBlank { null },
                                audioUrl = audUrl.ifBlank { null },
                                isRead = readStatus,
                                time = timeStr,
                                mine = isMine
                            )
                        )

                        if (!isMine && !readStatus) {
                            socket?.emit(
                                "message_read",
                                JSONObject().apply {
                                    put("messageId", msgId)
                                }
                            )
                        }
                    }
                    scrollToBottom()
                }
            }
        }

        val deleteListener = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val data = args[0] as JSONObject
                val targetId = data.optString("id")
                Handler(Looper.getMainLooper()).post {
                    val index = messages.indexOfFirst { it.id == targetId }
                    if (index != -1) {
                        messages[index] = messages[index].copy(isDeleted = true, text = "Pesan ini telah dihapus")
                    }
                }
            }
        }

        val readReceiptListener = Emitter.Listener { args ->
            if (args.isEmpty()) return@Listener
            val json = when (val arg = args[0]) {
                is JSONObject -> arg
                is JSONArray -> if (arg.length() > 0) arg.getJSONObject(0) else null
                else -> null
            }
            json?.let {
                val id = it.optString("id")
                Handler(Looper.getMainLooper()).post {
                    val index = messages.indexOfFirst { m -> m.id == id }
                    if (index != -1) {
                        messages[index] = messages[index].copy(isRead = true)
                    }
                }
            }
        }

        val onlineUsersListener = Emitter.Listener { args ->
            if (args.isEmpty()) return@Listener
            val arr = args[0] as JSONArray
            Handler(Looper.getMainLooper()).post {
                onlineUsers.clear()
                for (i in 0 until arr.length()) {
                    onlineUsers.add(arr.getString(i))
                }
            }
        }

        val typingListener = Emitter.Listener {
            Handler(Looper.getMainLooper()).post { typingIndicator = "Sedang mengetik..." }
        }

        val stopTypingListener = Emitter.Listener {
            Handler(Looper.getMainLooper()).post { typingIndicator = "" }
        }

        socket?.on(Socket.EVENT_CONNECT, connectListener)
        socket?.on(Socket.EVENT_DISCONNECT, disconnectListener)
        socket?.on("chat_history", historyListener)
        socket?.on("receive_message", chatListener)
        socket?.on("chatMessage", chatListener)
        socket?.on("message_deleted", deleteListener)
        socket?.on("messageDeleted", deleteListener)
        socket?.on("message_read_update", readReceiptListener)
        socket?.on("online_users_update", onlineUsersListener)
        socket?.on("user_typing", typingListener)
        socket?.on("user_stop_typing", stopTypingListener)

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val history = RetrofitClient.apiService.getMessages(userId = userId, sessionToken = sessionToken)
                Handler(Looper.getMainLooper()).post {
                    messages.clear()
                    history.forEach { netMsg ->
                        val senderUidStr = netMsg.user_id?.toString() ?: ""
                        val isMine = senderUidStr == userId
                        messages.add(
                            ChatMessage(
                                id = netMsg.id ?: System.currentTimeMillis().toString(),
                                senderUserId = senderUidStr,
                                sender = netMsg.sender,
                                text = netMsg.text ?: "",
                                messageType = netMsg.message_type,
                                imageUrl = netMsg.image_url,
                                fileUrl = netMsg.file_url,
                                audioUrl = netMsg.audio_url,
                                replyToId = netMsg.reply_to_id,
                                replyToSender = null,
                                replyToText = null,
                                isDeleted = netMsg.is_deleted,
                                isRead = netMsg.is_read,
                                time = netMsg.timestamp ?: "Baru saja",
                                mine = isMine
                            )
                        )

                        if (!isMine && !netMsg.is_read) {
                            socket?.emit(
                                "message_read",
                                JSONObject().apply {
                                    put("messageId", netMsg.id)
                                }
                            )
                        }
                    }
                    scrollToBottom()
                }
            } catch (e: Exception) {
                Log.e("ChatHistory", "Gagal memuat riwayat: ${e.message}")
            }
        }

        onDispose {
            currentPlayer?.stop()
            currentPlayer?.release()
            socket?.off(Socket.EVENT_CONNECT, connectListener)
            socket?.off(Socket.EVENT_DISCONNECT, disconnectListener)
            socket?.off("chat_history", historyListener)
            socket?.off("receive_message", chatListener)
            socket?.off("chatMessage", chatListener)
            socket?.off("message_deleted", deleteListener)
            socket?.off("messageDeleted", deleteListener)
            socket?.off("message_read_update", readReceiptListener)
            socket?.off("online_users_update", onlineUsersListener)
            socket?.off("user_typing", typingListener)
            socket?.off("user_stop_typing", stopTypingListener)
            socket?.disconnect()
        }
    }

    val onDeleteMessageRequest = { messageId: String ->
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.deleteMessage(
                    id = messageId,
                    userId = userId,
                    sessionToken = sessionToken
                )
                if (response.isSuccessful) {
                    Handler(Looper.getMainLooper()).post {
                        val index = messages.indexOfFirst { it.id == messageId }
                        if (index != -1) {
                            messages[index] = messages[index].copy(
                                isDeleted = true,
                                text = "Pesan telah dihapus"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatDelete", "Delete gagal", e)
            }
        }
    }

    fun uriToMultipartPart(cxt: Context, uri: Uri): MultipartBody.Part? {
        return try {
            val mimeType = cxt.contentResolver.getType(uri) ?: "application/octet-stream"
            val resolvedFileName = cxt.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }.orEmpty()

            val fileExtension = resolvedFileName.substringAfterLast('.', "").takeIf { it.length in 1..10 }
            val safeFileName = "kitachat_${System.currentTimeMillis()}" + 
                if (fileExtension != null) ".$fileExtension" else ""

            val tempFile = File(cxt.cacheDir, safeFileName)
            cxt.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return null

            val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("media", resolvedFileName.ifBlank { safeFileName }, requestBody)
        } catch (e: Exception) {
            Log.e("ChatUpload", "Gagal memproses lampiran: ${e.message}", e)
            null
        }
    }

    // Bottom Sheet (Sprint 1.2 & 1.3)
    if (showAttachmentMenu) {
        ModalBottomSheet(
            onDismissRequest = {
                showAttachmentMenu = false
            }
        ) {
            ListItem(
                headlineContent = {
                    Text("Kamera")
                },
                leadingContent = {
                    Icon(
                        Icons.Default.PhotoCamera,
                        null
                    )
                },
                modifier = Modifier.clickable {
                    showAttachmentMenu = false
                    val uri = createImageUri()
                    cameraImageUri = uri
                    cameraLauncher.launch(uri)
                }
            )

            ListItem(
                headlineContent = {
                    Text("Foto Galeri")
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Photo,
                        null
                    )
                },
                modifier = Modifier.clickable {
                    showAttachmentMenu = false
                    imagePickerLauncher.launch("image/*")
                }
            )

            ListItem(
                headlineContent = {
                    Text("Dokumen")
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Description,
                        null
                    )
                },
                modifier = Modifier.clickable {
                    showAttachmentMenu = false
                    documentPickerLauncher.launch(arrayOf("*/*"))
                }
            )

            Spacer(
                Modifier.height(24.dp)
            )
        }
    }

    // SPRINT 1.8.4.1: Fullscreen Image Dialog dengan Background Theme & Zoom/Drag
    fullscreenImageUrl?.let { url: String ->
        Dialog(
    onDismissRequest = {
        fullscreenImageUrl = null
    },
    properties = DialogProperties(
        usePlatformDefaultWidth = false
    )
)
 {
 Surface(
    color = Color.Black.copy(alpha = 0.15f),
    modifier = Modifier.fillMaxSize()
) {

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
        contentAlignment = Alignment.Center
    ) {
                    AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                )

                    // Tombol Tutup Fullscreen Viewer
                    IconButton(
                        onClick = { fullscreenImageUrl = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Avatar User",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )

                        Text(
                            text = if (typingIndicator.isNotBlank()) typingIndicator else "${onlineUsers.size} anggota online",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        
        // SPRINT 1.8.3 (Patch 3): Kurangi Jarak Antar Bubble
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), 
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message, 
                    onReply = { replyTo = message }, 
                    onDelete = { onDeleteMessageRequest(message.id) },
                    onPlayAudio = { url -> playAudio(url) },
                    onImageClick = { imageUrl ->
                        fullscreenImageUrl = imageUrl
                    }
                )
            }
        }
        
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                replyTo?.let { quoted ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp), 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(3.dp).height(32.dp).background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(8.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                text = "Membalas ${quoted.sender}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = getReplyPreview(quoted),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { replyTo = null }) { Icon(Icons.Default.Close, contentDescription = "Batalkan Balasan") }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                selectedFileName?.let { fileName ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                null
                            )
                            Spacer(
                                Modifier.width(8.dp)
                            )
                            Text(
                                fileName,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    selectedFileUri = null
                                    selectedFileName = null
                                }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    null
                                )
                            }
                        }
                    }
                    Spacer(
                        Modifier.height(6.dp)
                    )
                }

                // Tampilkan Status Rekam (Sprint 1.4)
                if (isRecording) {
                    Text(
                        text = "Merekam... ${recordingDuration}s",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(
                        Modifier.height(4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            showAttachmentMenu = true
                        }
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Lampiran",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    val typingHandler = remember { Handler(Looper.getMainLooper()) }

                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it.take(5000)
                            socket?.emit("typing")
                            typingHandler.removeCallbacksAndMessages(null)
                            typingHandler.postDelayed({ socket?.emit("stop_typing") }, 1200)
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ketik pesan...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        enabled = !isSending
                    )

                    // Tombol Mic / Stop (Sprint 1.4)
                    IconButton(
                        onClick = {
                            if (!isRecording) {
                                startRecording()
                            } else {
                                stopRecording()
                                prepareVoiceNoteForSend()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isRecording)
                                Icons.Default.Stop
                            else
                                Icons.Default.Mic,
                            contentDescription = "Voice Note",
                            tint = if (isRecording)
                                Color.Red
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = {
                            if ((draft.isNotBlank() || selectedFileUri != null) && userId.isNotBlank() && sessionToken.isNotBlank() && !isSending) {
                                val msgText = draft.trim()
                                val fileUri = selectedFileUri
                                val currentReplyId = replyTo?.id

                                socket?.emit("stop_typing")
                                typingHandler.removeCallbacksAndMessages(null)

                                isSending = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val messageBody = if (msgText.isNotBlank()) msgText.toRequestBody("text/plain".toMediaTypeOrNull()) else null
                                        val replyBody = currentReplyId?.toRequestBody("text/plain".toMediaTypeOrNull())
                                        val mediaPart = fileUri?.let { uriToMultipartPart(context, it) }

                                        val response = RetrofitClient.apiService.sendMessage(
                                            userId = userId,
                                            sessionToken = sessionToken,
                                            message = messageBody,
                                            replyToId = replyBody,
                                            media = mediaPart
                                        )

                                        if (response.isSuccessful) {
                                            Handler(Looper.getMainLooper()).post {
                                                draft = ""
                                                selectedFileUri = null
                                                selectedFileName = null
                                                replyTo = null
                                                isSending = false
                                            }
                                        } else {
                                            isSending = false
                                        }
                                    } catch (e: Exception) {
                                        isSending = false
                                    }
                                }
                            }
                        },
                        enabled = !isSending,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onPlayAudio: (String?) -> Unit,
    onImageClick: (String?) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // SPRINT 1.8.5.1: Buka Lampiran Berdasarkan MIME Type yang Sesuai (PDF, Docx, Excel, dll)
    fun openAttachment(url: String?) {
        if (url.isNullOrBlank()) return
        try {
            val parsedUrl = buildMediaUrl(url) ?: url
            val uri = Uri.parse(parsedUrl)
            val lowerUrl = parsedUrl.lowercase()
            val mimeType = when {
                lowerUrl.endsWith(".pdf") -> "application/pdf"
                lowerUrl.endsWith(".doc") || lowerUrl.endsWith(".docx") -> "application/msword"
                lowerUrl.endsWith(".xls") || lowerUrl.endsWith(".xlsx") -> "application/vnd.ms-excel"
                lowerUrl.endsWith(".ppt") || lowerUrl.endsWith(".pptx") -> "application/vnd.ms-powerpoint"
                lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".wav") -> "audio/*"
                lowerUrl.endsWith(".zip") || lowerUrl.endsWith(".rar") -> "application/zip"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("OpenAttachment", "Gagal membuka lampiran dengan viewer eksternal", e)
            try {
                // Fallback membuka via browser jika tidak ada aplikasi pembawa
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(buildMediaUrl(url) ?: url))
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }
    
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                // SPRINT 1.8.3 (Patch 1): Perkecil Bubble Width ke 260.dp
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (message.mine) 12.dp else 2.dp,
                    bottomEnd = if (message.mine) 2.dp else 12.dp
                ))
                .background(
                    if (message.isDeleted) MaterialTheme.colorScheme.surfaceVariant
                    else if (message.mine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.surface
                )
                .combinedClickable(
                    onClick = { },
                    onLongClick = {
                        if (!message.isDeleted) {
                            showMenu = true
                        }
                    }
                )
                // SPRINT 1.8.3.1 & 1.8.6: Kurangi Padding Bubble Lebih Ramping
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            if (!message.mine && !message.isDeleted) {
                Text(
                    text = message.sender,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(1.dp))
            }
            
            // Label Pesan Audio Saat History Dimuat
            if (message.messageType == "audio" && message.text.isBlank()) {
                Text(
                    text = "Voice Note",
                    fontWeight = FontWeight.Medium
                )
            }

            // SPRINT 1.8.3.1: Preview Foto Diperkecil Menjadi 130.dp
            if (message.messageType == "image" && !message.imageUrl.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                AsyncImage(
                    model = buildMediaUrl(message.imageUrl),
                    contentDescription = "Foto",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onImageClick(
                                buildMediaUrl(message.imageUrl)
                            )
                        }
                )
                Spacer(Modifier.height(3.dp))
            }

            // SPRINT 1.8.5: Smart Document Preview dengan Ikon & Keterangan Jenis File yang Dinamis
            if (
                message.messageType == "pdf" &&
                !message.fileUrl.isNullOrBlank()
            ) {
                val fileInfo = getFileLabel(message.fileUrl)
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            openAttachment(message.fileUrl)
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fileInfo.first,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = message.fileUrl.substringAfterLast('/'),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = fileInfo.second,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
            }

            if (message.text.isNotBlank() && message.messageType != "pdf") {
                Text(
                    text = message.text,
                    color = if (message.isDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = if (message.isDeleted) FontStyle.Italic else FontStyle.Normal
                )
            }

            // SPRINT 1.8.3.1: Voice Note Lebih Kompak & Ramping
            if (message.messageType == "audio" && !message.audioUrl.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            onPlayAudio(buildMediaUrl(message.audioUrl))
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Putar Audio",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Putar Voice Note",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
            
            Row(
                Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = message.time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                if (message.mine) {
                    Icon(
                        imageVector = if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                        contentDescription = if (message.isRead) "Dibaca" else "Terkirim",
                        tint = if (message.isRead) Color(0xFF2F80ED) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Balas Pesan") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        showMenu = false
                        onReply()
                    }
                )
                if (message.mine) {
                    DropdownMenuItem(
                        text = { Text("Hapus Pesan Untuk Semua") },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { onDelete(); showMenu = false }
                    )
                }
            }
        }
    }
}