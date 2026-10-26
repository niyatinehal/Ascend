package app.levelup2027.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.levelup2027.domain.WidgetConfig
import app.levelup2027.domain.WidgetMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
data class Reminder(val enabled: Boolean, val hour: Int, val minute: Int) {
    val label: String get() = "%d:%02d %s".format(if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "AM" else "PM")
}

@Serializable
data class WidgetSettings(
    val slot: TimeSlot? = null,
    val showCompleted: Boolean = false,
    val mode: WidgetMode = WidgetMode.IDEAL,
    val categories: Set<Category> = Category.entries.toSet(),
) {
    fun toConfig() = WidgetConfig(slot, showCompleted, mode, categories)
}

@Serializable
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.DARK,
    val defaultMode: DefaultMode = DefaultMode.DECIDE,
    val enabledCategories: Set<Category> = Category.entries.toSet(),
    val morningReminder: Reminder = Reminder(true, 7, 0),
    val workReminder: Reminder = Reminder(true, 12, 30),
    val eveningReminder: Reminder = Reminder(true, 20, 30),
    val weeklyReviewReminder: Reminder = Reminder(true, 18, 0),
    val widget: WidgetSettings = WidgetSettings(),
    /** Low-energy view is active only on this date; it never alters task definitions. */
    @Serializable(with = LocalDateSerializer::class) val lowEnergyDate: LocalDate? = null,
    val seedVersion: Int = 0,
    /** Notification permission is offered after the first completion, not on first launch. */
    val notificationPromptEligible: Boolean = false,
    val notificationPromptDismissed: Boolean = false,
) {
    fun isLowEnergy(today: LocalDate) = lowEnergyDate == today
}

interface SettingsSource {
    val flow: Flow<AppSettings>
    suspend fun current(): AppSettings = flow.first()
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

val SettingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private val Context.dataStore by preferencesDataStore("settings")

class DataStoreSettings(private val context: Context) : SettingsSource {
    private val key = stringPreferencesKey("app_settings_json")

    override val flow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { SettingsJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings()
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val cur = prefs[key]?.let { runCatching { SettingsJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
                ?: AppSettings()
            prefs[key] = SettingsJson.encodeToString(AppSettings.serializer(), transform(cur))
        }
    }
}

/** In-memory settings for tests. */
class MemorySettings(initial: AppSettings = AppSettings()) : SettingsSource {
    private val state = MutableStateFlow(initial)
    override val flow: Flow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value) }
}
