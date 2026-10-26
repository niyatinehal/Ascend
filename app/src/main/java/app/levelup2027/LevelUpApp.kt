package app.levelup2027

import android.app.Application
import android.content.Context
import androidx.room.withTransaction
import app.levelup2027.ai.AiService
import app.levelup2027.ai.KeystoreSecretStore
import app.levelup2027.data.AppDatabase
import app.levelup2027.data.BackupManager
import app.levelup2027.data.DataStoreSettings
import app.levelup2027.data.Repository
import app.levelup2027.notify.Notifications
import app.levelup2027.notify.ReminderScheduler
import app.levelup2027.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LevelUpApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
        container.appScope.launch {
            container.repository.ensureDefaults()
            ReminderScheduler.scheduleAll(this@LevelUpApp, container.settings.current())
        }
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.build(appContext)
    val settings = DataStoreSettings(appContext)
    val repository = Repository(
        taskDao = db.tasks(),
        completionDao = db.completions(),
        goalDao = db.goals(),
        reviewDao = db.reviews(),
        categoryDao = db.categories(),
        planDao = db.plan(),
        settings = settings,
        runInTransaction = { block -> db.withTransaction { block() } },
        onChanged = { kind ->
            WidgetUpdater.refresh(appContext)
            if (kind == Repository.ChangeKind.REMINDERS || kind == Repository.ChangeKind.SETTINGS) {
                ReminderScheduler.scheduleAll(appContext, settings.current())
            }
        },
    )
    val backup = BackupManager(db, settings)
    val secrets = KeystoreSecretStore(appContext)
    val ai = AiService(
        settings, secrets, db.aiUsage(), BuildConfig.AI_SERVER_URL,
        builtInGeminiKeys = BuildConfig.GEMINI_KEYS.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    )
}

val Context.container: AppContainer get() = (applicationContext as LevelUpApp).container
