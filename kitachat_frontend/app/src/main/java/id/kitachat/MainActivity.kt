package id.kitachat

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import id.kitachat.ui.screens.KitachatApp
import id.kitachat.utils.SoundManager

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

private val permissionLauncher =
    registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        permissions.forEach { (permission, granted) ->

            Log.d(
                TAG,
                "$permission = $granted"
            )
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Meminta izin runtime untuk Kamera dan Audio sebelum konten dimuat
        permissionLauncher.launch(
    arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )
)


        setContent {

            val windowSizeClass = calculateWindowSizeClass(this)
            val windowWidthSizeClass = windowSizeClass.widthSizeClass

            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(), // <-- REVISI: Melindungi area gambar agar tidak tertutup sistem UI / cutout[cite: 7]
                    color = MaterialTheme.colorScheme.background
                ) {
                    KitachatApp(
                        windowWidthSizeClass = windowWidthSizeClass
                    )
                }
            }
        }
    }

    override fun onStop() {
        try {
            SoundManager.stopRingtone()
            Log.d(TAG, "Ringtone dihentikan")
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Gagal menghentikan ringtone",
                exception
            )
        }

        super.onStop()
    }
}