package com.example.geosamplemanager.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.data.bluetooth.BtDevice
import com.example.geosamplemanager.data.bluetooth.BtProfile
import com.example.geosamplemanager.data.settings.AppTheme
import com.example.geosamplemanager.data.settings.OrderNumberRule
import com.example.geosamplemanager.data.settings.OrderSource
import com.example.geosamplemanager.data.settings.UiScale
import com.example.geosamplemanager.data.voice.SoundLevel
import com.example.geosamplemanager.data.voice.SoundTestUtil
import com.example.geosamplemanager.data.voice.TtsVolume
import com.example.geosamplemanager.data.voice.VoiceMode
import com.example.geosamplemanager.data.voice.VoiceSettings
import kotlin.math.roundToInt

/**
 * FIX 5.9-settings-sound-3-fix-2: скорость TTS — ползунок.
 * FIX 5.9-settings-scale-2: чипы и пресеты в горизонтальном скролле.
 * FIX 5.9-settings-theme: в разделе «Внешний вид» — выбор темы.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val message by viewModel.message.collectAsState()
    val selected by viewModel.selectedCategory.collectAsState()

    var openedLogs by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.reload() }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    if (openedLogs) {
        LogsScreen(onClose = { openedLogs = false })
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val isWide = maxW >= 600.dp

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                CategoryTree(
                    selected = selected,
                    onSelect = { viewModel.selectCategory(it) },
                    modifier = Modifier.fillMaxHeight().width(maxW * 0.4f)
                )
                VerticalDivider()
                CategoryContent(
                    category = selected,
                    viewModel = viewModel,
                    onOpenLogs = { openedLogs = true },
                    modifier = Modifier.fillMaxHeight().weight(1f)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                CategoryChipsRow(selected = selected, onSelect = { viewModel.selectCategory(it) })
                HorizontalDivider()
                CategoryContent(
                    category = selected,
                    viewModel = viewModel,
                    onOpenLogs = { openedLogs = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun CategoryTree(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )
        Spacer(Modifier.height(8.dp))
        SettingsCategory.values().forEach { cat ->
            val isSelected = cat == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    cat.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun CategoryChipsRow(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsCategory.values().forEach { cat ->
            FilterChip(
                selected = cat == selected,
                onClick = { onSelect(cat) },
                label = { Text(cat.title, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun CategoryContent(
    category: SettingsCategory,
    viewModel: SettingsViewModel,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (category) {
        SettingsCategory.IMPORT -> ImportSettingsContent(viewModel, modifier)
        SettingsCategory.VOICE -> VoiceSettingsContent(viewModel, modifier)
        SettingsCategory.SOUND -> SoundSettingsContent(viewModel, modifier)
        SettingsCategory.BLUETOOTH -> BluetoothSettingsContent(viewModel, modifier)
        SettingsCategory.APPEARANCE -> AppearanceSettingsContent(viewModel, modifier)
        SettingsCategory.SYSTEM -> SystemSettingsContent(modifier, onOpenLogs)
        SettingsCategory.ABOUT -> AboutContent(modifier)
    }
}

// ============================================================
// РАЗДЕЛ «ВНЕШНИЙ ВИД»
// ============================================================

@Composable
private fun AppearanceSettingsContent(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val ap by viewModel.appearanceSettings.collectAsState()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Внешний вид",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // FIX 5.9-settings-theme:
        // выбор темы — светлая / тёмная / системная.
        InfoCard(
            title = "Тема",
            help = "«Системная» — как в настройках Android " +
                    "(тёмная ночью, светлая днём). «Светлая» и «Тёмная» — " +
                    "фиксированные, независимо от системы."
        ) {
            Text(
                "Оформление приложения",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            ChoiceRow(
                options = AppTheme.values().map { it to it.title },
                selected = ap.theme,
                onSelect = { viewModel.setTheme(it) }
            )
        }

        InfoCard(
            title = "Размер интерфейса",
            help = "Масштаб применяется ко всему приложению сразу. " +
                    "Крупнее — больше текст; отступы и кнопки растут " +
                    "умеренно, чтобы вёрстка оставалась аккуратной."
        ) {
            Text(
                "Выберите удобный размер",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            ChoiceRow(
                options = UiScale.values().map { it to it.title },
                selected = ap.scale,
                onSelect = { viewModel.setUiScale(it) }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (ap.scale) {
                    UiScale.NORMAL -> "Текущий: обычный."
                    UiScale.LARGE -> "Текущий: крупнее. Текст 115%, элементы 105%."
                    UiScale.HUGE -> "Текущий: крупный. Текст 130%, элементы 110%."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================
// РАЗДЕЛ «ЗВУК»
// ============================================================

@Composable
private fun SoundSettingsContent(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vs by viewModel.voiceSettings.collectAsState()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Звук",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        InfoCard(
            title = "Озвучка ответов",
            help = "Голосовой помощник произносит ответы через синтезатор речи. " +
                    "«Выкл» — полное молчание. Скорость 1.0 — обычная."
        ) {
            Text("Громкость", style = MaterialTheme.typography.bodyMedium)
            ChoiceRow(
                options = listOf(
                    TtsVolume.OFF to "Выкл",
                    TtsVolume.QUIET to "Тихая",
                    TtsVolume.NORMAL to "Обычная",
                    TtsVolume.LOUD to "Громкая"
                ),
                selected = vs.ttsVolume,
                onSelect = { viewModel.setTtsVolume(it) }
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Скорость",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "%.2f×".format(vs.ttsSpeedValue),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = vs.ttsSpeedValue,
                onValueChange = { viewModel.setTtsSpeed(it) },
                valueRange = VoiceSettings.MIN_TTS_SPEED..VoiceSettings.MAX_TTS_SPEED,
                steps = 14
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.5×", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("1.0×", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("2.0×", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        SoundTestUtil.speakTest(
                            context,
                            vs.ttsVolume,
                            VoiceSettings.DEFAULT_TTS_SPEED
                        )
                    },
                    enabled = vs.ttsVolume != TtsVolume.OFF,
                    modifier = Modifier.weight(1f)
                ) { Text("Обычно") }
                OutlinedButton(
                    onClick = {
                        SoundTestUtil.speakTest(
                            context,
                            vs.ttsVolume,
                            vs.ttsSpeedValue
                        )
                    },
                    enabled = vs.ttsVolume != TtsVolume.OFF,
                    modifier = Modifier.weight(1f)
                ) { Text("Проверить") }
            }
        }

        InfoCard(
            title = "Звуковые сигналы",
            help = "Короткие бипы-подтверждения: успех, внимание, ошибка. " +
                    "Не зависят от озвучки ответов."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = vs.feedbackEnabled,
                    onCheckedChange = { viewModel.setFeedbackEnabled(it) }
                )
                Text(
                    "Включить звуковые сигналы",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (vs.feedbackEnabled) {
                Spacer(Modifier.height(8.dp))
                Text("Громкость", style = MaterialTheme.typography.bodyMedium)
                ChoiceRow(
                    options = listOf(
                        SoundLevel.QUIET to "Тихая",
                        SoundLevel.NORMAL to "Обычная",
                        SoundLevel.LOUD to "Громкая"
                    ),
                    selected = vs.feedbackVolume,
                    onSelect = { viewModel.setFeedbackVolume(it) }
                )

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        SoundTestUtil.playAllSignals(context, vs)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Проверить сигналы")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Прозвучит три сигнала: успех, внимание, ошибка.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Сброс",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.resetSoundToDefaults() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Сбросить настройки звука") }
            }
        }
    }
}

// ============================================================
// РАЗДЕЛ «ГОЛОС»
// ============================================================

@Composable
private fun VoiceSettingsContent(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val vs by viewModel.voiceSettings.collectAsState()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Голосовой помощник",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        InfoCard(
            title = "Отображение",
            help = "Настройки вида списка проб при работе со сверкой."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = vs.showCharacteristic,
                    onCheckedChange = { viewModel.setShowCharacteristic(it) }
                )
                Text(
                    "Показывать колонку «Характеристика» в списке проб",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        InfoCard(
            title = "Распознавание",
            help = "Грамматика Vosk ограничивает словарь только нужными словами — " +
                    "это резко повышает точность распознавания чисел и команд."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = vs.useGrammar,
                    onCheckedChange = { viewModel.setUseGrammar(it) }
                )
                Text(
                    "Использовать грамматику распознавания",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Выключение снижает точность, но добавляет устойчивость " +
                        "к нестандартным словам.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        InfoCard(
            title = "Поведение",
            help = "Авто-стоп выключает микрофон после указанного времени молчания. " +
                    "Обучение — короткие экраны при первом запуске помощника."
        ) {
            Text("Авто-стоп", style = MaterialTheme.typography.bodyMedium)
            ChoiceRow(
                options = listOf(
                    0 to "Выкл",
                    5 to "5 мин",
                    10 to "10 мин",
                    20 to "20 мин"
                ),
                selected = vs.autoStopMinutes,
                onSelect = { viewModel.setAutoStopMinutes(it) }
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = vs.showOnboarding,
                    onCheckedChange = { viewModel.setShowOnboarding(it) }
                )
                Text(
                    "Показывать обучение при следующем запуске",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Режим работы", style = MaterialTheme.typography.bodyMedium)
            ChoiceRow(
                options = listOf(
                    VoiceMode.NOVICE to "Начинающий",
                    VoiceMode.EXPERIENCED to "Опытный"
                ),
                selected = vs.mode,
                onSelect = { viewModel.setVoiceMode(it) }
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Сброс",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.resetVoiceToDefaults() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Сбросить настройки голоса") }
            }
        }
    }
}

// ============================================================
// РАЗДЕЛ «BLUETOOTH»
// ============================================================

@Composable
private fun BluetoothSettingsContent(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val bs by viewModel.btSettings.collectAsState()
    val devices by viewModel.pairedDevices.collectAsState()
    val enabled by viewModel.btEnabled.collectAsState()
    val hasPermission by viewModel.btHasPermission.collectAsState()
    val activeDevice by viewModel.btActiveDevice.collectAsState()

    var showMicTest by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Bluetooth",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        InfoCard(
            title = "Состояние",
            help = "Голосовой помощник может использовать микрофон " +
                    "Bluetooth-гарнитуры вместо встроенного."
        ) {
            Text(
                if (enabled) "Bluetooth включён" else "Bluetooth выключен",
                style = MaterialTheme.typography.bodyMedium
            )
            val active = activeDevice
            if (active != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Подключено: ${active.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!hasPermission) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Нет разрешения на доступ к Bluetooth. Разрешите " +
                            "в системных настройках.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.openBluetoothSystemSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть настройки Android")
            }
        }

        if (!enabled || !hasPermission) {
            return@Column
        }

        InfoCard(
            title = "Использовать гарнитуру",
            help = "Если выключено — голосовой помощник работает через " +
                    "встроенный микрофон."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = bs.enabled,
                    onCheckedChange = { viewModel.setBluetoothEnabled(it) }
                )
                Text(
                    "Работать через Bluetooth-гарнитуру",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        InfoCard(
            title = "Устройство",
            help = "Выберите гарнитуру для голосового помощника. " +
                    "«Встроенный микрофон» — микрофон планшета."
        ) {
            val allDevices = devices + BtDevice.BUILT_IN
            allDevices.forEach { dev ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setBluetoothDevice(dev) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = if (dev.isBuiltIn) {
                            !bs.enabled || bs.deviceAddress == null
                        } else {
                            bs.deviceAddress == dev.address
                        },
                        onClick = { viewModel.setBluetoothDevice(dev) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        dev.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        InfoCard(
            title = "Профиль",
            help = "SCO — для разговора, ниже задержка. A2DP — для музыки, " +
                    "выше качество. Для голосового помощника лучше SCO."
        ) {
            ChoiceRow(
                options = listOf(
                    BtProfile.SCO to "SCO (разговор)",
                    BtProfile.A2DP to "A2DP (медиа)"
                ),
                selected = bs.profile,
                onSelect = { viewModel.setBtProfile(it) }
            )
        }

        InfoCard(
            title = "Если устройство отключилось",
            help = "Что делать, если гарнитура вне зоны. По умолчанию — " +
                    "переходим на встроенный микрофон, чтобы работа не " +
                    "прерывалась."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = bs.fallbackToBuiltIn,
                    onCheckedChange = { viewModel.setFallbackToBuiltIn(it) }
                )
                Text(
                    "Переключаться на встроенный микрофон",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Проверка",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Скажите что-нибудь и проверьте, что микрофон " +
                            "распознаёт текст.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showMicTest = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Проверить микрофон")
                }
            }
        }
    }

    if (showMicTest) {
        MicrophoneTestDialog(
            btSettings = bs,
            onDismiss = { showMicTest = false }
        )
    }
}

// ============================================================
// РАЗДЕЛ «О ПРИЛОЖЕНИИ»
// ============================================================

@Composable
private fun AboutContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "GeoSample Manager",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Версия $versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Приложение для управления геохимическими пробами " +
                    "в горнодобывающей промышленности. Учёт нарядов, " +
                    "импорт описей проб из Excel, сверка фактического " +
                    "наличия, весовой контроль, заметки и фото.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================
// РАЗДЕЛ «СИСТЕМА»
// ============================================================

@Composable
private fun SystemSettingsContent(
    modifier: Modifier = Modifier,
    onOpenLogs: () -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Система",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Журнал событий",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "История действий в приложении: запуск, навигация, поиск, " +
                            "отметки, операции с базой данных. Помогает понять, " +
                            "что происходило в сессии, и разобрать ошибки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenLogs,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.List, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Открыть журнал событий")
                }
            }
        }
    }
}

@Composable
private fun StubContent(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================
// КОНТЕНТ «ИМПОРТ EXCEL»
// ============================================================

@Composable
private fun ImportSettingsContent(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsState()

    var showAddAreaDialog by remember { mutableStateOf(false) }
    var areaToRename by remember { mutableStateOf<String?>(null) }
    var areaToDelete by remember { mutableStateOf<String?>(null) }
    var showResetUserDialog by remember { mutableStateOf(false) }
    var showHeadersDialog by remember { mutableStateOf(false) }
    var showTypesDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportTo(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFrom(it) } }

    val totalHeaderUserWords = settings.userHeaderKeywords.values.sumOf { it.size }
    val totalTypeUserWords = settings.userTypeValueKeywords.values.sumOf { it.size }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Настройки импорта", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        InfoCard(
            title = "Участки и префиксы",
            help = "Префиксы нужны, чтобы при импорте автоматически определить участок " +
                    "по номерам скважин и проб. У одного участка может быть несколько префиксов."
        ) {
            settings.areaPrefixes.forEach { (area, prefixes) ->
                AreaRow(
                    areaName = area,
                    prefixesCsv = prefixes.joinToString(", "),
                    onRename = { areaToRename = area },
                    onPrefixesChange = { viewModel.setAreaPrefixes(area, it) },
                    onDelete = { areaToDelete = area }
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showAddAreaDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Добавить участок")
            }
        }

        InfoCard(
            title = "Определение наряда",
            help = "Программа берёт имя файла (если один лист) или имя листа " +
                    "(если листов несколько) и извлекает номер наряда. " +
                    "Например, из «02-КОПТ00027» получится наряд «27»."
        ) {
            Text("Источник имени", style = MaterialTheme.typography.bodyMedium)
            ChoiceRow(
                options = listOf(
                    OrderSource.AUTO to "Авто",
                    OrderSource.FILENAME to "Имя файла",
                    OrderSource.SHEET_NAME to "Имя листа"
                ),
                selected = settings.orderSource,
                onSelect = { viewModel.setOrderSource(it) }
            )
            Spacer(Modifier.height(8.dp))
            Text("Как извлекать номер", style = MaterialTheme.typography.bodyMedium)
            ChoiceRow(
                options = listOf(
                    OrderNumberRule.LAST_INTEGER to "Последнее число",
                    OrderNumberRule.FULL_NAME to "Всё имя целиком"
                ),
                selected = settings.orderNumberRule,
                onSelect = { viewModel.setOrderNumberRule(it) }
            )
        }

        InfoCard(
            title = "Слова для заголовков",
            help = "Когда вы вручную назначите роль колонке при импорте, система " +
                    "запомнит это слово. Здесь можно посмотреть и отредактировать. " +
                    "Также показаны стандартные слова."
        ) {
            KeywordButton(
                label = "Слова для заголовков ($totalHeaderUserWords)",
                onClick = { showHeadersDialog = true }
            )
            Spacer(Modifier.height(6.dp))
            KeywordButton(
                label = "Слова для типов проб ($totalTypeUserWords)",
                onClick = { showTypesDialog = true }
            )
        }

        InfoCard(
            title = "Бланки и стандартные образцы",
            help = "Бланк — зарезервированное место под контрольный образец. У него нет " +
                    "интервала, веса и характеристики. Такие строки пропускаются при " +
                    "импорте. Холостые пробы сюда не относятся."
        ) {
            KeywordField(
                label = "Слова-маркеры (через запятую)",
                csv = settings.blankKeywords.joinToString(", "),
                onChange = { viewModel.setBlankKeywords(it) }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = settings.skipBlanksWithoutData,
                    onCheckedChange = { viewModel.setSkipBlanks(it) }
                )
                Text("Пропускать бланки без интервала и веса", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Управление настройками",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("geosample_import_settings.json") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Экспорт") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Импорт") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showResetUserDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сбросить пользовательские слова") }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { viewModel.resetToDefaults() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Сбросить всё к стандартным") }
            }
        }
    }

    // ============ ДИАЛОГИ ============

    if (showAddAreaDialog) {
        TextInputDialog(
            title = "Новый участок",
            label = "Название",
            initial = "",
            onConfirm = { viewModel.addArea(it); showAddAreaDialog = false },
            onDismiss = { showAddAreaDialog = false }
        )
    }

    areaToRename?.let { old ->
        TextInputDialog(
            title = "Переименовать участок",
            label = "Новое имя",
            initial = old,
            onConfirm = { viewModel.renameArea(old, it); areaToRename = null },
            onDismiss = { areaToRename = null }
        )
    }

    areaToDelete?.let { area ->
        AlertDialog(
            onDismissRequest = { areaToDelete = null },
            title = { Text("Удалить участок?") },
            text = { Text("Участок «$area» и все его префиксы будут удалены из настроек.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeArea(area); areaToDelete = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { areaToDelete = null }) { Text("Отмена") } }
        )
    }

    if (showResetUserDialog) {
        AlertDialog(
            onDismissRequest = { showResetUserDialog = false },
            title = { Text("Сбросить пользовательские слова?") },
            text = { Text("Будут удалены все слова, добавленные через ручной маппинг. " +
                    "Участки, наряды и стандартные словари не тронуты.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetUserKeywords(); showResetUserDialog = false }) {
                    Text("Сбросить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showResetUserDialog = false }) { Text("Отмена") } }
        )
    }

    if (showHeadersDialog) {
        val sections = listOf(
            "serial" to "Серийный №",
            "well" to "Скважина / Выработка",
            "sample" to "Проба",
            "int_from" to "Интервал от",
            "int_to" to "Интервал до",
            "weight" to "Вес",
            "material" to "Характеристика",
            "type" to "Тип пробы"
        ).map { (role, title) ->
            KeywordSectionUi(
                key = role,
                title = title,
                userWords = settings.userKeywordsForHeader(role),
                defaultWords = settings.defaultHeaderKeywords[role].orEmpty()
            )
        }
        HeaderKeywordsDialog(
            title = "Слова для заголовков",
            help = "Ваши слова используются при автоматическом определении колонок. " +
                    "Стандартные слова уже работают автоматически — их трогать не нужно.",
            sections = sections,
            onAdd = { role, word -> viewModel.addUserHeaderKeyword(role, word) },
            onRemove = { role, word -> viewModel.removeUserHeaderKeyword(role, word) },
            onDismiss = { showHeadersDialog = false }
        )
    }

    if (showTypesDialog) {
        val sections = listOf(
            "hollow" to "Холостая",
            "auger" to "Шнековая",
            "channel" to "Бороздовая / канава",
            "cobra" to "Кобра",
            "duplicate" to "Дубликат"
        ).map { (code, title) ->
            KeywordSectionUi(
                key = code,
                title = title,
                userWords = settings.userKeywordsForType(code),
                defaultWords = settings.defaultTypeValueKeywords[code].orEmpty()
            )
        }
        HeaderKeywordsDialog(
            title = "Слова для типов проб",
            help = "Значения из колонки «Тип пробы», которые надо отнести к определённому типу. " +
                    "Например, если в файле пишут «керновая» — добавьте это в шнековую.",
            sections = sections,
            onAdd = { code, word -> viewModel.addUserTypeKeyword(code, word) },
            onRemove = { code, word -> viewModel.removeUserTypeKeyword(code, word) },
            onDismiss = { showTypesDialog = false }
        )
    }
}

// ============================================================
// ВСПОМОГАТЕЛЬНЫЕ КОМПОНЕНТЫ
// ============================================================

@Composable
private fun KeywordButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun InfoCard(
    title: String,
    help: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    var helpVisible by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (help != null) {
                    IconButton(onClick = { helpVisible = !helpVisible }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Пояснение",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (help != null && helpVisible) {
                Text(
                    help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AreaRow(
    areaName: String,
    prefixesCsv: String,
    onRename: () -> Unit,
    onPrefixesChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    var localCsv by remember(prefixesCsv) { mutableStateOf(prefixesCsv) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                areaName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRename) { Text("Переименовать") }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        OutlinedTextField(
            value = localCsv,
            onValueChange = { localCsv = it; onPrefixesChange(it) },
            singleLine = true,
            label = { Text("Префиксы через запятую") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun KeywordField(label: String, csv: String, onChange: (String) -> Unit) {
    var local by remember(csv) { mutableStateOf(csv) }
    OutlinedTextField(
        value = local,
        onValueChange = { local = it; onChange(it) },
        singleLine = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("ОК") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}