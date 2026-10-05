package com.example.geosamplemanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.geosamplemanager.data.logs.Log
import com.example.geosamplemanager.data.settings.AppTheme
import com.example.geosamplemanager.data.voice.VoiceModelPreparer
import com.example.geosamplemanager.ui.navigation.AppScaffold
import com.example.geosamplemanager.ui.navigation.Screen
import com.example.geosamplemanager.ui.navigation.SimpleViewModelStoreOwner
import com.example.geosamplemanager.ui.theme.GeoSampleManagerTheme

/**
 * FIX 5.9-logs-3: логируем onResume / onPause.
 * FIX 5.9-settings-bt: запрашиваем BLUETOOTH_CONNECT (API 31+).
 * FIX 5.9-settings-scale: масштаб интерфейса через LocalDensity.
 * FIX 5.9-settings-scale-2: textFactor/densityFactor разделены.
 *
 * FIX 5.9-settings-theme:
 *  - тема (светлая / тёмная / системная) читается из
 *    GeoSampleApp.appearance и передаётся в GeoSampleManagerTheme.
 */
class MainActivity : ComponentActivity() {

    private val requestBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат не нужен — раздел Bluetooth сам покажет статус */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestBluetoothIfNeeded()

        val app = application as GeoSampleApp

        setContent {
            // FIX 5.9-settings-theme: подписка на appearance,
            // чтобы при смене темы весь UI пересобрался.
            val appearance by app.appearance.collectAsState()
            val useDark = when (appearance.theme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            GeoSampleManagerTheme(darkTheme = useDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.app("Приложение развёрнуто").write()
    }

    override fun onPause() {
        Log.app("Приложение свёрнуто").write()
        super.onPause()
    }

    private fun requestBluetoothIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as GeoSampleApp

    var ready by remember { mutableStateOf(app.voiceModel != null) }
    var status by remember { mutableStateOf("Инициализация…") }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (ready) return@LaunchedEffect
        val result = VoiceModelPreparer.prepare(context) { msg -> status = msg }
        result.getOrNull()?.let {
            app.voiceModel = it
            ready = true
        }
        result.exceptionOrNull()?.let {
            errorText = it.message ?: "Не удалось загрузить модель Vosk"
        }
    }

    when {
        errorText != null -> LoadingScreen(
            title = "Ошибка голосового помощника",
            message = errorText!!,
            showSpinner = false
        )
        !ready -> LoadingScreen(
            title = "Голосовой помощник",
            message = status,
            showSpinner = true
        )
        else -> ReadyContent(app)
    }
}

@Composable
private fun ReadyContent(app: GeoSampleApp) {
    val restartRequest by app.restartRequest.collectAsState()
    val tick = restartRequest?.tick ?: 0
    val initialRoute = restartRequest?.route ?: Screen.MAIN.route

    val appearance by app.appearance.collectAsState()

    key(tick) {
        val owner = remember { SimpleViewModelStoreOwner() }
        DisposableEffect(owner) {
            onDispose { owner.viewModelStore.clear() }
        }

        val baseDensity = LocalDensity.current
        val scaledDensity = remember(baseDensity, appearance.scale) {
            Density(
                density = baseDensity.density * appearance.scale.densityFactor,
                fontScale = baseDensity.fontScale * appearance.scale.textFactor
            )
        }

        CompositionLocalProvider(
            LocalViewModelStoreOwner provides owner,
            LocalDensity provides scaledDensity
        ) {
            AppScaffold(initialRoute = initialRoute)
        }
    }
}

@Composable
private fun LoadingScreen(
    title: String,
    message: String,
    showSpinner: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        if (showSpinner) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(24.dp))
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}