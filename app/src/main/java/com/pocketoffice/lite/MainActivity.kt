package com.pocketoffice.lite

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pocketoffice.lite.data.AppViewModel
import com.pocketoffice.lite.file.CreateDocContract
import com.pocketoffice.lite.file.OpenDocContract
import com.pocketoffice.lite.navigation.AppRoot
import com.pocketoffice.lite.ui.theme.PocketOfficeTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    // Jika pengguna membatalkan, hasilnya null dan tidak terjadi apa-apa.
    private val openLauncher = registerForActivityResult(OpenDocContract()) { uri ->
        if (uri != null) vm.openUri(uri)
    }
    private val createLauncher = registerForActivityResult(CreateDocContract()) { uri ->
        if (uri != null) vm.saveAsTo(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = when (vm.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            PocketOfficeTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        vm = vm,
                        onPickDocument = { launchSafely { openLauncher.launch(
                            arrayOf(
                                "application/pdf", "text/*",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint"
                            )
                        ) } },
                        onPickSaveAs = { name -> launchSafely { createLauncher.launch(name) } }
                    )
                }
            }
        }
    }

    private fun launchSafely(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            vm.message = "Tidak ada aplikasi pemilih file di HP ini."
        }
    }

    override fun onStop() {
        super.onStop()
        vm.flush() // pastikan teks tidak hilang saat pindah aplikasi
    }
}
