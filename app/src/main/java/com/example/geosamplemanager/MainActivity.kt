package com.example.geosamplemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.voice.VoiceModelPreparer
import com.example.geosamplemanager.ui.navigation.AppScaffold
import com.example.geosamplemanager.ui.theme.GeoSampleManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeoSampleManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

/**
 * Корень приложения.
 *
 * Пока модель Vosk не готова — показываем экран загрузки.
 * Как только готова — обычный AppScaffold.
 */
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
        else -> AppScaffold()
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