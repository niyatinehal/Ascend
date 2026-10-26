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
import kotlinx.serialization.SerialName
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
    /** Category ids shown on the widget; empty means all enabled categories. */
    val categories: Set<String> = emptySet(),
) {
    fun toConfig() = WidgetConfig(slot, showCompleted, mode, categories)
}

/** Minutes the user can realistically spend on their plan per day. Null means "not set". */
@Serializable
data class Capacity(val weekdayMinutes: Int? = null, val weekendMinutes: Int? = null) {
    fun forDate(date: LocalDate): Int? =
        if (date.dayOfWeek.value >= 6) weekendMinutes ?: weekdayMinutes else weekdayMinutes ?: weekendMinutes
    val isSet: Boolean get() = weekdayMinutes != null || weekendMinutes != null
}

enum class AiMode(val label: String) {
    SERVER("Ascend AI server"),
    OWN_KEY("My own Anthropic API key"),
    OFF("Off"),
}

@Serializable
data class AiSettings(
    val mode: AiMode = AiMode.SERVER,
    /** Overrides the server URL built into the app (BuildConfig.AI_SERVER_URL). */
    val serverUrl: String = "",
    /** The user has read what is sent to the AI provider and agreed. Asked once, before the first AI call. */
    val consentGiven: Boolean = false,
    /** Apply small, explicitly requested changes (move one occurrence, lighter day) without a review step. */
    val autoApplySimple: Boolean = false,
    /** Local cap for own-key mode so a bug or loop can't run up the user's bill. */
    val monthlyRequestLimit: Int = 60,
)

@Serializable
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.DARK,
    val defaultMode: DefaultMode = DefaultMode.DECIDE,
    /** v1 kept enabled categories here (as enum names); now they live on the category rows. */
    @SerialName("enabledCategories") val legacyEnabledCategories: Set<String>? = null,
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
    /** 0 = written by v1 (or never written); see [migrated]. */
    val settingsVersion: Int = 0,
    val onboardingDone: Boolean = false,
    @Serializable(with = LocalDateSerializer::class) val planStartDate: LocalDate? = null,
    val capacity: Capacity = Capacity(),
    val ai: AiSettings = AiSettings(),
    /** Structured context from the last "Build my plan" conversation (work hours, preferences...). */
    val userContextJson: String = "",
    val widgetHintDismissed: Boolean = false,
) {
    fun isLowEnergy(today: LocalDate) = lowEnergyDate == today

    /**
     * Upgrades settings written by v1. Pure so it can be tested; disabling legacy categories on the
     * category rows is done by the repository from [legacyDisabledCategoryIds].
     */
    fun migrated(): AppSettings {
        if (settingsVersion >= CURRENT_VERSION) return this
        val widgetCats = widget.categories.map(Cat::fromLegacy).toSet()
        val allLegacy = Cat.LEGACY.values.toSet()
        return copy(
            settingsVersion = CURRENT_VERSION,
            widget = widget.copy(categories = if (widgetCats.containsAll(allLegacy)) emptySet() else widgetCats),
            onboardingDone = onboardingDone || seedVersion > 0,
            planStartDate = planStartDate ?: if (seedVersion > 0) Campaign.START else null,
        )
    }

    val legacyDisabledCategoryIds: Set<String>
        get() = legacyEnabledCategories?.let { enabled -> (Cat.LEGACY.keys - enabled).map(Cat::fromLegacy).toSet() } ?: emptySet()

    companion object { const val CURRENT_VERSION = 2 }
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
