package id.kitachat.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import id.kitachat.data.AlbumItem
import id.kitachat.data.BASE_URL
import id.kitachat.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

@Composable
fun AlbumTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("kitachat_prefs", Context.MODE_PRIVATE) }
    val userId = prefs.getString("user_id", "") ?: ""
    val sessionToken = prefs.getString("session_token", "") ?: ""

    var searchQuery by remember {
    mutableStateOf("")
    }

    var caption by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    
    // State index untuk slider viewer
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    
    val albumList = remember { mutableStateListOf<AlbumItem>() }

    val filteredAlbumList =
    remember(
        albumList,
        searchQuery
    ) {
        if (searchQuery.isBlank()) {

            albumList

        } else {

            albumList.filter {

                it.caption
                    ?.contains(
                        searchQuery,
                        ignoreCase = true
                    ) == true

            }
        }
    }


    // Fungsi untuk mengambil daftar foto album dari server Railway
    fun fetchAlbum() {
        if (userId.isBlank() || sessionToken.isBlank()) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getAlbum(userId, sessionToken)
                Handler(Looper.getMainLooper()).post {
                    albumList.clear()
                    albumList.addAll(response)
                    if (albumList.isEmpty()) {
                        selectedPhotoIndex = null
                    }
                }
            } catch (e: Exception) {
                Log.e("AlbumFetch", "Gagal memuat album: ${e.message}", e)
            }
        }
    }

    // Muat data album saat tab pertama kali dibuka
    LaunchedEffect(Unit) {
        fetchAlbum()
    }

    // Pemilih file universal untuk foto galeri/kamera
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            var name = "Foto_Keluarga.jpg"
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val retrieved = c.getString(nameIndex)
                        if (!retrieved.isNullOrBlank()) name = retrieved
                    }
                }
            }
            selectedFileName = name
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AddAPhoto, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Unggah Momen Keluarga", fontWeight = FontWeight.SemiBold)
                            Text(
                                selectedFileName ?: "Pilih foto dari galeri atau kamera",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Keterangan foto (opsional)") },
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Photo, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pilih Foto")
                        }

                        Button(
                            onClick = {
                                val uri = selectedFileUri
                                if (uri != null && userId.isNotBlank() && sessionToken.isNotBlank() && !isUploading) {
                                    isUploading = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val inputStream = context.contentResolver.openInputStream(uri)
                                            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                                            val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                                            inputStream?.use { input ->
                                                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                                            }

                                            val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                                            val multipartPart = MultipartBody.Part.createFormData("image", tempFile.name, requestBody)
                                            val captionBody = caption.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())

                                            val response = RetrofitClient.apiService.uploadAlbum(
                                                userId = userId,
                                                sessionToken = sessionToken,
                                                caption = captionBody,
                                                image = multipartPart
                                            )

                                            if (response.isSuccessful) {
                                                Handler(Looper.getMainLooper()).post {
                                                    caption = ""
                                                    selectedFileUri = null
                                                    selectedFileName = null
                                                    isUploading = false
                                                    fetchAlbum()
                                                }
                                            } else {
                                                isUploading = false
                                                Log.e("AlbumUpload", "Gagal unggah: ${response.errorBody()?.string()}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("AlbumUpload", "Error: ${e.message}", e)
                                            isUploading = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = selectedFileUri != null && !isUploading
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Unggah")
                            }
                        }
                    }
                }
            }

            Text("Album Keluarga", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            
            OutlinedTextField(
    value = searchQuery,
    onValueChange = {
        searchQuery = it
    },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    label = {
        Text("Cari judul foto")
    },
    leadingIcon = {
        Icon(
            Icons.Default.Search,
            contentDescription = null
        )
    }
)

            if (albumList.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada foto momen keluarga.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
    filteredAlbumList,
    key = { it.id }
) { item ->
                        Card(
                            modifier = Modifier.clickable {
                                selectedPhotoIndex = albumList.indexOf(item)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 2.dp
                            )
                        ) {
                            Column {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val imageUrl = if (item.image_url.startsWith("http")) item.image_url else "${BASE_URL}${item.image_url.removePrefix("/")}"
                                    
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(imageUrl)
                                            .crossfade(true)
                                            .size(400)
                                            .build(),
                                        contentDescription = item.caption,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Column(Modifier.padding(8.dp)) {
                                    Text(
                                        text = item.caption.orEmpty().ifBlank { "Momen Keluarga" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Oleh: ${item.uploader_name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Integrasi FamilyPhotoSliderScreen dengan pengiriman foto beserta caption album ke chat
        selectedPhotoIndex?.let { index ->
            if (index in albumList.indices) {
                val currentItem = albumList[index]
                FamilyPhotoSliderScreen(
                    photos = albumList,
                    initialIndex = index,
                    onBackClick = {
                        selectedPhotoIndex = null
                    },
                    onShareToChatClick = { imageUrl ->
                        if (userId.isNotBlank() && sessionToken.isNotBlank()) {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val imageBody = imageUrl.toRequestBody("text/plain".toMediaTypeOrNull())
                                    // Mengirimkan image_url beserta caption sebagai pesan teks pendamping
                                    RetrofitClient.apiService.shareAlbumToChat(
                                        userId = userId,
                                        sessionToken = sessionToken,
                                        imageUrl = imageBody
                                    )
                                } catch (e: Exception) {
                                    Log.e("AlbumShare", e.message ?: "Share gagal")
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}