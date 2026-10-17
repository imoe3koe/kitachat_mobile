package id.kitachat.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import id.kitachat.data.AlbumItem
import id.kitachat.data.BASE_URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyPhotoSliderScreen(
    photos: List<AlbumItem>,
    initialIndex: Int,
    onBackClick: () -> Unit,
    onShareToChatClick: (String) -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { photos.size }
    )

    // State untuk mendeteksi apakah halaman saat ini sedang di-zoom agar pager tidak bergeser saat panning
    var isZoomed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Pager untuk menggeser foto satu per satu (dinonaktifkan scroll-nya jika sedang di-zoom)
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = photos[page]
            val imageUrl = if (item.image_url.startsWith("http")) item.image_url else "${BASE_URL}${item.image_url.removePrefix("/")}"

            // State dengan remember(page) agar state zoom/offset terisolasi per halaman dan aman saat recycling
            var scale by remember(page) { mutableStateOf(1f) }
            var offset by remember(page) { mutableStateOf(Offset.Zero) }

            // Sinkronisasi status isZoomed dengan skala halaman aktif
            LaunchedEffect(scale) {
                if (pagerState.currentPage == page) {
                    isZoomed = scale > 1.05f
                }
            }

            // Animasi transisi skala saat double tap atau pinch
            val animatedScale by animateFloatAsState(targetValue = scale, label = "scaleAnimation")

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                val newX = offset.x + pan.x
                                val newY = offset.y + pan.y
                                // Pembatasan boundary agar gambar tidak keluar layar terlalu jauh
                                offset = Offset(
                                    newX.coerceIn(-500f * (scale - 1), 500f * (scale - 1)),
                                    newY.coerceIn(-500f * (scale - 1), 500f * (scale - 1))
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(page) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f // Zoom in 2.5x saat double tap
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = animatedScale,
                            scaleY = animatedScale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
        }

        // Top Bar (Tombol Kembali & Indikator Posisi Halaman)
        TopAppBar(
            title = {
                Text(
                    text = "${pagerState.currentPage + 1} dari ${photos.size}",
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            ),
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Bottom Bar (Keterangan Foto & Tombol Kirim ke Chat) - Hanya tampil saat tidak di-zoom (scale <= 1.05f)
        val currentItem = photos.getOrNull(pagerState.currentPage)
        if (currentItem != null && !isZoomed) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = currentItem.caption.orEmpty().ifBlank { "Momen Keluarga" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Diunggah oleh: ${currentItem.uploader_name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Button(
                        onClick = {
                            val activeUrl = if (currentItem.image_url.startsWith("http")) {
                                currentItem.image_url
                            } else {
                                "${BASE_URL}${currentItem.image_url.removePrefix("/")}"
                            }
                            onShareToChatClick(activeUrl)
                            Toast.makeText(context, "Foto berhasil dibagikan ke obrolan", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Kirim ke Chat")
                    }
                }
            }
        }
    }
}