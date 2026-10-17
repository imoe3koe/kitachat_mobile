package id.kitachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlin.math.abs

@Composable
fun PhotoViewer(
    imageUrl: String,
    caption: String? = null,
    uploader: String? = null,
    onDismiss: () -> Unit
) {

    // State reset otomatis saat imageUrl berubah
    var scale by remember(imageUrl) {
        mutableStateOf(1f)
    }

    var offsetX by remember(imageUrl) {
        mutableStateOf(0f)
    }

    var offsetY by remember(imageUrl) {
        mutableStateOf(0f)
    }

    Dialog(
        onDismissRequest = {
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(imageUrl) {

                        detectTransformGestures { _, pan, zoom, _ ->

                            val newScale =
                                (scale * zoom).coerceIn(
                                    1f,
                                    4f
                                )

                            scale = newScale

                            if (scale > 1f) {
                                // Perbaikan: maxOffset dinamis berdasarkan scale agar lebih adaptif di berbagai ukuran layar
                                val maxOffset = 500f * scale

                                offsetX =
                                    (offsetX + pan.x)
                                        .coerceIn(
                                            -maxOffset,
                                            maxOffset
                                        )

                                offsetY =
                                    (offsetY + pan.y)
                                        .coerceIn(
                                            -maxOffset,
                                            maxOffset
                                        )

                            } else {

                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .pointerInput(imageUrl) {

                        detectTapGestures(

                            onDoubleTap = {

                                if (abs(scale - 1f) < 0.1f) {

                                    scale = 2f

                                } else {

                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        )
                    },

                contentAlignment = Alignment.Center
            ) {

                AsyncImage(
                    model = imageUrl,
                    contentDescription = caption,
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

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {

                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup"
                    )
                }

                // Perbaikan UX: Caption hanya tampil saat gambar tidak di-zoom (skala mendekati 1f)
                if (
                    scale <= 1.05f &&
                    (!caption.isNullOrBlank() || !uploader.isNullOrBlank())
                ) {

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.surface
                            .copy(alpha = 0.90f)
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            if (!caption.isNullOrBlank()) {

                                Text(
                                    text = caption,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            if (!uploader.isNullOrBlank()) {

                                Text(
                                    text = "Oleh: $uploader",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}