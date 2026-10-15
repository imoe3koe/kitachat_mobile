package id.kitachat.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.kitachat.ui.theme.KitachatTheme
import id.kitachat.utils.SoundManager
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import id.kitachat.R

enum class KitachatTab(val title: String) {
    Chat("Obrolan"),
    Album("Album"),
    Agenda("Agenda"),
    Family("Keluarga"),
    Settings("Pengaturan")
}

@Composable
fun KitachatApp(windowWidthSizeClass: WindowWidthSizeClass) {
    val context = LocalContext.current
    
    val (savedUserId, savedName, savedPhone, savedDarkMode) = remember {
        try {
            val prefs = context.getSharedPreferences("kitachat_prefs", Context.MODE_PRIVATE)
            val id = prefs.getString("user_id", "") ?: ""
            val name = prefs.getString("user_name", "Nama Pengguna") ?: "Nama Pengguna"
            val phoneNum = prefs.getString("phone", "") ?: ""
            val darkModePref = prefs.getBoolean("dark_mode", false)
            Quadruple(id, name, phoneNum, darkModePref)
        } catch (e: Exception) {
            Log.e("KitachatApp", "Gagal membaca SharedPreferences: ${e.localizedMessage}", e)
            Quadruple("", "Nama Pengguna", "", false)
        }
    }

    var authenticated by remember { mutableStateOf(savedUserId.isNotBlank()) }
    var darkMode by remember { mutableStateOf(savedDarkMode) }
    var selectedTab by remember { mutableStateOf(KitachatTab.Chat) }
    var displayName by remember { mutableStateOf(savedName) }
    var phone by remember { mutableStateOf(savedPhone) }

    KitachatTheme(darkTheme = darkMode) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!authenticated) {
                AuthScreen(onAuthenticated = { name, userPhone ->
                    displayName = name.ifBlank { "Nama Pengguna" }
                    phone = userPhone
                    authenticated = true
                })
            } else {
                MainScreen(
                    windowWidthSizeClass = windowWidthSizeClass,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    displayName = displayName,
                    phone = phone,
                    darkMode = darkMode,
                    onToggleTheme = { 
                        darkMode = !darkMode
                        try {
                            val prefs = context.getSharedPreferences("kitachat_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("dark_mode", darkMode).apply()
                        } catch (e: Exception) {
                            Log.e("KitachatApp", "Gagal menyimpan preferensi tema: ${e.localizedMessage}")
                        }
                    },
                    onLogout = {
                        SoundManager.stopRingtone()
                        try {
                            val prefs = context.getSharedPreferences("kitachat_prefs", Context.MODE_PRIVATE)
                            val currentDarkMode = darkMode
                            prefs.edit().clear().putBoolean("dark_mode", currentDarkMode).apply()
                        } catch (e: Exception) {
                            Log.e("KitachatApp", "Gagal menghapus sesi saat logout: ${e.localizedMessage}")
                        }
                        authenticated = false
                    },
                    onProfileUpdated = { newName -> displayName = newName }
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    selectedTab: KitachatTab,
    onTabSelected: (KitachatTab) -> Unit,
    displayName: String,
    phone: String,
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onProfileUpdated: (String) -> Unit
) {
    val wide = windowWidthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            if (!wide) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(R.drawable.logo_kitachat),
                                contentDescription = "Logo KitaChat",
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedTab == KitachatTab.Chat) "Kitachat" else selectedTab.title,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(Icons.Default.Contrast, contentDescription = "Tema")
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Keluar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (!wide) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    KitachatTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { onTabSelected(tab) },
                            icon = { TabIcon(tab) },
                            label = { Text(tab.title, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            if (wide) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Icon(
                            Icons.Default.Forum,
                            contentDescription = "Kitachat",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 18.dp).size(30.dp)
                        )
                    }
                ) {
                    KitachatTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = selectedTab == tab,
                            onClick = { onTabSelected(tab) },
                            icon = { TabIcon(tab) },
                            label = { Text(tab.title) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onToggleTheme) {
                        Icon(Icons.Default.Contrast, contentDescription = "Ganti tema")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Keluar")
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                when (selectedTab) {
                    KitachatTab.Chat -> ChatTab(displayName = displayName, modifier = Modifier.fillMaxSize())
                    KitachatTab.Album -> AlbumTab(modifier = Modifier.fillMaxSize())
                    KitachatTab.Agenda -> AgendaTab(modifier = Modifier.fillMaxSize())
                    KitachatTab.Family -> FamilyTab(modifier = Modifier.fillMaxSize())
                    KitachatTab.Settings -> SettingsTab(
                        displayName = displayName,
                        phone = phone,
                        darkMode = darkMode,
                        onToggleTheme = onToggleTheme,
                        onLogout = onLogout,
                        modifier = Modifier.fillMaxSize(),
                        onProfileUpdated = { newName -> onProfileUpdated(newName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: KitachatTab) {
    when (tab) {
        KitachatTab.Chat -> Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
        KitachatTab.Album -> Icon(Icons.Default.PhotoLibrary, contentDescription = null)
        KitachatTab.Agenda -> Icon(Icons.Default.CalendarMonth, contentDescription = null)
        KitachatTab.Family -> Icon(Icons.Default.Groups, contentDescription = null)
        KitachatTab.Settings -> Icon(Icons.Default.Settings, contentDescription = null)
    }
}