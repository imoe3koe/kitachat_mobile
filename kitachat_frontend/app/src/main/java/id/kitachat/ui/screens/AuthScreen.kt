package id.kitachat.ui.screens

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kitachat.R
import id.kitachat.data.AuthRequest
import id.kitachat.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthenticated: (String, String) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var joinFamily by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().height(150.dp).align(Alignment.TopCenter).background(MaterialTheme.colorScheme.primary))
        Card(
            modifier = Modifier.align(Alignment.Center).padding(20.dp).widthIn(max = 420.dp).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(horizontal = 26.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
                Image(
                    painter = painterResource(id = R.drawable.logo_kitachat),
                    contentDescription = "Logo Kitachat asli",
                    modifier = Modifier
                        .size(72.dp)
                        .padding(bottom = 4.dp)
                )

                Text("Kitachat", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text("Hub Keluarga Tercinta (Railway)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthTabButton("Masuk", selected = !register, modifier = Modifier.weight(1f)) { register = false; message = "" }
                    AuthTabButton("Daftar", selected = register, modifier = Modifier.weight(1f)) { register = true; message = "" }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                if (register) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !joinFamily, onClick = { joinFamily = false }, label = { Text("Buat Keluarga") })
                        FilterChip(selected = joinFamily, onClick = { joinFamily = true }, label = { Text("Gabung Keluarga") })
                    }
                    if (joinFamily) OutlinedTextField(value = inviteCode, onValueChange = { inviteCode = it.uppercase() }, label = { Text("Kode undangan") }, placeholder = { Text("KITA-XXXX") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it.take(100) }, label = { Text("Nama Lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Nomor Telepon") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(if (register) "Password minimal 8 karakter" else "Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                
                Button(
                    onClick = {
                        if (phone.isBlank() || password.isBlank()) {
                            message = "Nomor telepon dan password wajib diisi."
                            return@Button
                        }
                        if (register && name.isBlank()) {
                            message = "Nama lengkap wajib diisi."
                            return@Button
                        }

                        isLoading = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val request = AuthRequest(
                                    phone = phone, 
                                    password = password, 
                                    name = if (register) name else null,
                                    register_type = if (register) (if (joinFamily) "join" else "create") else null,
                                    invite_code = if (register && joinFamily) inviteCode else null
                                )
                                val response = if (register) {
                                    RetrofitClient.apiService.register(request)
                                } else {
                                    RetrofitClient.apiService.login(request)
                                }

                                Handler(Looper.getMainLooper()).post {
                                    isLoading = false
                                    if (!response.session_token.isNullOrBlank()) {
                                        val resolvedName = response.user?.name ?: if (register) name else "Pengguna Kitachat"
                                        val userIdStr = response.user?.id?.toString() ?: ""

                                        // Simpan sesi lengkap ke SharedPreferences[cite: 7]
                                        val prefs = context.getSharedPreferences("kitachat_prefs", android.content.Context.MODE_PRIVATE)
                                        prefs.edit()
                                            .putString("user_id", userIdStr)
                                            .putString("session_token", response.session_token ?: "")
                                            .putString("user_name", resolvedName)
                                            .putString("phone", phone)
                                            .apply()

                                        // Verifikasi log sesi login untuk audit socket
                                        Log.d("KitachatLogin", "userId=$userIdStr token=${response.session_token}")

                                        onAuthenticated(resolvedName, phone)
                                    } else {
                                        message = response.message?.ifBlank { "Autentikasi gagal." } ?: response.error ?: "Autentikasi gagal."
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("AuthError", "Gagal autentikasi ke server Railway: ${e.message}", e)
                                Handler(Looper.getMainLooper()).post {
                                    isLoading = false
                                    message = "Koneksi gagal: ${e.localizedMessage ?: "Periksa BASE_URL dan koneksi internet Anda."}"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (register) "Daftar Sekarang" else "Masuk", modifier = Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                
                Text("developed by imo3koe", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AuthTabButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick, 
        modifier = modifier, 
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent))
        }
    }
}