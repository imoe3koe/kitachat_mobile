package id.kitachat.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsTab(
    displayName: String,
    phone: String,
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    onProfileUpdated: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kitachat_prefs", Context.MODE_PRIVATE) }

    var editProfile by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(displayName) }
    var birthday by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    // PERBAIKAN: Sinkronisasi otomatis nilai editedName jika displayName dari luar berubah
    LaunchedEffect(displayName) {
        editedName = displayName
    }

    LazyColumn(
        modifier = modifier
            .padding(16.dp)
            .widthIn(max = 650.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(15.dp))
                    Column {
                        Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(phone.ifBlank { "Nomor Telepon" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Text(
                "Pengaturan Akun & Aplikasi",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SettingsItem(
                Icons.Default.PersonPin,
                "Ubah Informasi Profil",
                "Perbarui nama dan tanggal lahir"
            ) {
                editProfile = !editProfile
            }
        }

        if (editProfile) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Perbarui Data Profil", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it.take(100) },
                            label = { Text("Nama Lengkap") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = birthday,
                            onValueChange = { birthday = it },
                            label = { Text("Tanggal Lahir") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val trimmedName = editedName.ifBlank { "Nama Pengguna" }
                                prefs.edit().putString("user_name", trimmedName).apply()
                                onProfileUpdated(trimmedName)
                                notice = "Informasi profil berhasil diperbarui."
                                editProfile = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Simpan Perubahan")
                        }
                    }
                }
            }
        }

        item {
            SettingsItem(
                Icons.Default.Image,
                "Ganti Foto Profil",
                "Perbarui foto akun keluarga"
            ) {
                notice = "Fitur unggah foto profil segera hadir."
            }
        }

        item {
            SettingsItem(
                Icons.Default.Contrast,
                "Tema Tampilan",
                if (darkMode) "Mode Gelap Aktif" else "Mode Terang Aktif"
            ) {
                onToggleTheme()
            }
        }

        item {
            SettingsItem(
                Icons.Default.SystemUpdate,
                "Cek Pembaruan",
                "Periksa versi aplikasi terbaru"
            ) {
                notice = "Aplikasi Anda sudah menggunakan versi terbaru."
            }
        }

        item {
            SettingsItem(
                Icons.AutoMirrored.Filled.ExitToApp,
                "Keluar Aplikasi",
                "Akhiri sesi akun saat ini",
                danger = true,
                onClick = onLogout
            )
        }

        if (notice.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notice,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Medium,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}