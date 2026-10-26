package app.levelup2027.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.levelup2027.container
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import app.levelup2027.ui.goals.GoalsScreen
import app.levelup2027.ui.plan.AdjustSheet
import app.levelup2027.ui.plan.AdjustViewModel
import app.levelup2027.ui.plan.PlanBuilderScreen
import app.levelup2027.ui.plan.PlanBuilderViewModel
import app.levelup2027.ui.plan.WeekPlanSheet
import app.levelup2027.ui.plan.WeekPlanViewModel
import app.levelup2027.ui.review.ReviewScreen
import app.levelup2027.ui.settings.SettingsScreen
import app.levelup2027.ui.tasks.AllTasksScreen
import app.levelup2027.ui.tasks.TaskDetailSheet
import app.levelup2027.ui.tasks.TaskEditorDialog
import app.levelup2027.ui.today.TodayScreen
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Outlined.CheckCircle),
    TASKS("Tasks", Icons.Outlined.ListAlt),
    GOALS("Goals", Icons.Outlined.Flag),
    REVIEW("Review", Icons.Outlined.Insights),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

data class OpenRequest(val taskId: String?, val date: LocalDate?, val tab: Tab, val slot: TimeSlot?)

/** Navigation actions shared by all screens. */
class AppNav(
    val openTask: (taskId: String, date: LocalDate?) -> Unit,
    val editTask: (Task?) -> Unit,
    val goTo: (Tab) -> Unit,
    val showMessage: (String) -> Unit,
    /** The weekly schedule view in the Tasks tab. */
    val openWeekPlan: () -> Unit,
    val openPlanBuilder: () -> Unit = {},
    val openAdjust: () -> Unit = {},
    val planNextWeek: (LocalDate) -> Unit = {},
    val openAiSettings: () -> Unit = {},
)

val LocalNav = compositionLocalOf<AppNav> { error("no nav") }

@Composable
fun AppRoot(vm: AppViewModel, openRequests: MutableStateFlow<OpenRequest?>) {
    val container = LocalContext.current.container
    val builderVm: PlanBuilderViewModel = viewModel(factory = viewModelFactory { initializer { PlanBuilderViewModel(container) } })
    val adjustVm: AdjustViewModel = viewModel(factory = viewModelFactory { initializer { AdjustViewModel(container) } })
    val weekVm: WeekPlanViewModel = viewModel(factory = viewModelFactory { initializer { WeekPlanViewModel(container) } })
    val settings by vm.settings.collectAsState()
    val catalog by vm.catalog.collectAsState()
    var builderOpen by rememberSaveable { mutableStateOf(false) }
    var adjustOpen by rememberSaveable { mutableStateOf(false) }
    var weekToPlan by remember { mutableStateOf<LocalDate?>(null) }
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var detail by remember { mutableStateOf<Pair<String, LocalDate?>?>(null) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }
    var weekPlanRequested by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }

    val open by openRequests.collectAsState()
    LaunchedEffect(open) {
        val r = open ?: return@LaunchedEffect
        tab = r.tab
        if (r.slot != null) vm.todayFilter.value = TodayFilter.forSlot(r.slot)
        if (r.taskId != null) detail = r.taskId to r.date
        openRequests.value = null
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }

    val nav = remember {
        AppNav(
            openTask = { id, date -> detail = id to date },
            editTask = { t -> if (t == null) creating = true else editing = t },
            goTo = { tab = it },
            showMessage = { message = it },
            openWeekPlan = { weekPlanRequested = true; tab = Tab.TASKS },
            openPlanBuilder = { builderOpen = true },
            openAdjust = { adjustOpen = true },
            planNextWeek = { weekToPlan = it },
            openAiSettings = { builderOpen = false; tab = Tab.SETTINGS },
        )
    }

    val s = settings
    if (s == null) return
    val onboarding = !s.onboardingDone

    BackHandler(enabled = tab != Tab.TODAY) { tab = Tab.TODAY }

    CompositionLocalProvider(LocalNav provides nav, LocalCatalog provides catalog) {
        if (onboarding || builderOpen) {
            PlanBuilderScreen(
                vm, builderVm, isOnboarding = onboarding,
                // Closing keeps an unfinished draft so it can be resumed; only a finished one is cleared.
                onClose = {
                    builderOpen = false
                    if (builderVm.state.value.step == app.levelup2027.ui.plan.BuilderStep.DONE) builderVm.finish()
                    tab = Tab.TODAY
                },
                onOpenAiSettings = { builderOpen = false; if (onboarding) vm.updateSettings { it.copy(onboardingDone = true) }; tab = Tab.SETTINGS },
            )
            return@CompositionLocalProvider
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when (tab) {
                    Tab.TODAY -> TodayScreen(vm)
                    Tab.TASKS -> AllTasksScreen(vm, weekPlanRequested = weekPlanRequested, onWeekPlanShown = { weekPlanRequested = false })
                    Tab.GOALS -> GoalsScreen(vm)
                    Tab.REVIEW -> ReviewScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }

        detail?.let { (id, date) ->
            TaskDetailSheet(vm, taskId = id, requestedDate = date, onDismiss = { detail = null })
        }
        if (adjustOpen) AdjustSheet(vm, adjustVm, onDismiss = { adjustOpen = false }, onOpenAiSettings = { tab = Tab.SETTINGS })
        weekToPlan?.let { w -> WeekPlanSheet(vm, weekVm, w, onDismiss = { weekToPlan = null }, onOpenAiSettings = { tab = Tab.SETTINGS }) }
        if (creating || editing != null) {
            TaskEditorDialog(
                vm = vm,
                original = editing,
                onDismiss = { creating = false; editing = null },
            )
        }
    }
}
