package id.kitachat.ui.screens

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.kitachat.data.AgendaItem
import id.kitachat.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AgendaTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("kitachat_prefs", Context.MODE_PRIVATE) }
    val userId = prefs.getString("user_id", "") ?: ""
    val sessionToken = prefs.getString("session_token", "") ?: ""

    var showForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val agendaList = remember { mutableStateListOf<AgendaItem>() }
    val birthdays = listOf("Ibu • 12 Jan", "Ayah • 4 Mar", "Kak Rina • 22 Jul", "Dina • 9 Nov")

    // Fungsi untuk mengambil data agenda dari server Railway[cite: 8]
    fun fetchAgenda() {
        if (userId.isBlank() || sessionToken.isBlank()) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getAgenda(userId, sessionToken)
                Handler(Looper.getMainLooper()).post {
                    agendaList.clear()
                    agendaList.addAll(response)
                }
            } catch (e: Exception) {
                Log.e("AgendaFetch", "Gagal memuat agenda: ${e.message}", e)
            }
        }
    }

    // Muat data saat tab pertama kali dibuka
    LaunchedEffect(Unit) {
        fetchAgenda()
    }

    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("🎂  Ulang Tahun Anggota", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 135.dp), modifier = Modifier.heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(birthdays) { birthday ->
                            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cake, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(7.dp)); Text(birthday, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tambah Agenda Keluarga", fontWeight = FontWeight.SemiBold)
                            Text("Buat jadwal atau kegiatan baru", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { showForm = !showForm }) { Icon(if (showForm) Icons.Default.ExpandLess else Icons.Default.Add, "Formulir") }
                    }
                    if (showForm) {
                        OutlinedTextField(title, { title = it.take(150) }, modifier = Modifier.fillMaxWidth(), label = { Text("Nama acara atau kegiatan") }, singleLine = true)
                        OutlinedTextField(date, { date = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tanggal (Format: YYYY-MM-DD atau Teks)") }, singleLine = true)
                        OutlinedTextField(description, { description = it.take(1000) }, modifier = Modifier.fillMaxWidth(), label = { Text("Keterangan atau lokasi") }, singleLine = true)
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (title.isNotBlank() && date.isNotBlank() && userId.isNotBlank() && sessionToken.isNotBlank() && !isSaving) {
                                        isSaving = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val bodyMap = mapOf(
                                                    "title" to title.trim(),
                                                    "date" to date.trim(),
                                                    "description" to description.trim()
                                                )
                                                val response = RetrofitClient.apiService.createAgenda(userId, sessionToken, bodyMap)
                                                if (response.isSuccessful) {
                                                    Handler(Looper.getMainLooper()).post {
                                                        title = ""
                                                        date = ""
                                                        description = ""
                                                        showForm = false
                                                        isSaving = false
                                                        fetchAgenda() // Muat ulang daftar agenda
                                                    }
                                                } else {
                                                    isSaving = false
                                                    Log.e("AgendaCreate", "Gagal menyimpan agenda")
                                                }
                                            } catch (e: Exception) {
                                                Log.e("AgendaCreate", "Error: ${e.message}", e)
                                                isSaving = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSaving
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text("Simpan")
                                }
                            }
                            OutlinedButton(onClick = { showForm = false }, modifier = Modifier.weight(1f)) { Text("Batal") }
                        }
                    }
                }
            }
        }
        item { Text("Daftar Kegiatan Mendatang", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        
        if (agendaList.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada agenda kegiatan tercatat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(agendaList, key = { it.id }) { event ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.Event, null, tint = MaterialTheme.colorScheme.primary) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, fontWeight = FontWeight.SemiBold)
                            // Diperbaiki dari item.event_date menjadi event.event_date
                            Text(event.event_date, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            if (!event.description.isNullOrBlank()) Text(event.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}