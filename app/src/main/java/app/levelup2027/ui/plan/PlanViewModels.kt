package app.levelup2027.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.levelup2027.AppContainer
import app.levelup2027.ai.AiAvailability
import app.levelup2027.ai.AiError
import app.levelup2027.ai.AiJson
import app.levelup2027.ai.ChangeValidator
import app.levelup2027.ai.ChatTurn
import app.levelup2027.ai.ContextBuilder
import app.levelup2027.ai.DraftGoal
import app.levelup2027.ai.DraftTask
import app.levelup2027.ai.IntakeResult
import app.levelup2027.ai.PlanDraft
import app.levelup2027.ai.PlanIssue
import app.levelup2027.ai.PlanMaterializer
import app.levelup2027.ai.PlanState
import app.levelup2027.ai.PlanValidator
import app.levelup2027.ai.QuestionAnswer
import app.levelup2027.ai.ValidatedChanges
import app.levelup2027.ai.ValidatedPlan
import app.levelup2027.ai.WeekPlan
import app.levelup2027.data.DraftKind
import app.levelup2027.data.DraftStatus
import app.levelup2027.data.PlanDraftRecord
import app.levelup2027.data.Repository
import app.levelup2027.domain.Dates
import app.levelup2027.domain.ScheduleEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** What an AI screen should show when a call can't go ahead or fails. */
sealed class AiBlock {
    data object NeedsConsent : AiBlock()
    data object NeedsSetup : AiBlock()
    data class Failed(val message: String, val retryable: Boolean) : AiBlock()
}

private fun Throwable.toBlock(): AiBlock = when (this) {
    is AiError.NoConsent -> AiBlock.NeedsConsent
    is AiError.NotConfigured -> AiBlock.NeedsSetup
    is AiError.QuotaExceeded, is AiError.Auth -> AiBlock.Failed(message ?: "AI request failed.", retryable = false)
    is AiError -> AiBlock.Failed(message ?: "AI request failed.", retryable = true)
    else -> AiBlock.Failed("Something went wrong: ${message?.take(120) ?: javaClass.simpleName}", retryable = true)
}

private suspend fun checkReady(c: AppContainer): AiBlock? = when (c.ai.availability()) {
    AiAvailability.READY -> null
    AiAvailability.NEEDS_CONSENT -> AiBlock.NeedsConsent
    AiAvailability.NOT_CONFIGURED, AiAvailability.OFF -> AiBlock.NeedsSetup
}

// ---------------------------------------------------------------- Build my plan

enum class BuilderStep { DESCRIBE, QUESTIONS, WORKING, PREVIEW, DONE }

data class BuilderState(
    val step: BuilderStep = BuilderStep.DESCRIBE,
    val text: String = "",
    val intake: IntakeResult? = null,
    val answers: Map<String, String> = emptyMap(),
    val plan: ValidatedPlan? = null,
    val working: String = "",
    val block: AiBlock? = null,
    val acceptedSummary: String = "",
)

class PlanBuilderViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val _state = MutableStateFlow(BuilderState())
    val state: StateFlow<BuilderState> = _state
    private var draftId = UUID.randomUUID().toString()
    private var job: Job? = null
    private val materializer = PlanMaterializer()

    init {
        // Resume an unfinished conversation so nothing the user typed is lost.
        viewModelScope.launch {
            val d = repo.latestOpenDraft(DraftKind.ONBOARDING) ?: return@launch
            draftId = d.id
            val intake = d.intakeJson.takeIf { it.isNotBlank() }?.let { runCatching { AiJson.decodeFromString(IntakeResult.serializer(), it) }.getOrNull() }
            val answers = d.answersJson.takeIf { it.isNotBlank() }?.let {
                runCatching { AiJson.decodeFromString(ListSerializer(QuestionAnswer.serializer()), it) }.getOrNull()
            }.orEmpty()
            val draft = d.draftJson.takeIf { it.isNotBlank() }?.let { runCatching { AiJson.decodeFromString(PlanDraft.serializer(), it) }.getOrNull() }
            val answerMap = intake?.questions.orEmpty().associate { q -> q.id to (answers.firstOrNull { it.question == q.question }?.answer ?: "") }
            _state.value = BuilderState(
                step = when { draft != null -> BuilderStep.PREVIEW; intake != null && intake.questions.isNotEmpty() -> BuilderStep.QUESTIONS; else -> BuilderStep.DESCRIBE },
                text = d.inputText, intake = intake, answers = answerMap,
                plan = draft?.let { validate(it) },
            )
        }
    }

    private fun startDate(): LocalDate = Dates.today().let { if (LocalTime.now().hour >= 15) it.plusDays(1) else it }

    private suspend fun validate(d: PlanDraft): ValidatedPlan {
        val s = repo.settings.current()
        return PlanValidator.validate(d, startDate(), repo.getCategories(), s.capacity.weekdayMinutes, s.capacity.weekendMinutes)
    }

    private suspend fun persist(transform: (PlanDraftRecord) -> PlanDraftRecord) {
        val now = System.currentTimeMillis()
        val base = repo.draft(draftId) ?: PlanDraftRecord(draftId, DraftKind.ONBOARDING, DraftStatus.OPEN, now, now)
        repo.saveDraft(transform(base))
    }

    fun setText(t: String) { _state.value = _state.value.copy(text = t) }
    fun setAnswer(id: String, a: String) { _state.value = _state.value.copy(answers = _state.value.answers + (id to a)) }
    fun dismissBlock() { _state.value = _state.value.copy(block = null) }

    private fun answersList(s: BuilderState) = s.intake?.questions.orEmpty().map { q -> QuestionAnswer(q.question, s.answers[q.id].orEmpty().trim()) }

    /** Step 1: understand the message and decide whether to ask questions. */
    fun submitDescription() {
        val text = _state.value.text.trim()
        if (text.length < 3) return
        job?.cancel()
        job = viewModelScope.launch {
            persist { it.copy(inputText = text, status = DraftStatus.OPEN) }
            checkReady(c)?.let { _state.value = _state.value.copy(block = it); return@launch }
            _state.value = _state.value.copy(step = BuilderStep.WORKING, working = "Understanding your goals…", block = null)
            runCatching { c.ai.intake(Dates.today(), text) }
                .onSuccess { intake ->
                    persist { it.copy(intakeJson = AiJson.encodeToString(IntakeResult.serializer(), intake)) }
                    _state.value = _state.value.copy(intake = intake, answers = intake.questions.associate { it.id to "" })
                    if (intake.questions.isEmpty()) generate() else _state.value = _state.value.copy(step = BuilderStep.QUESTIONS)
                }
                .onFailure { e -> _state.value = _state.value.copy(step = BuilderStep.DESCRIBE, block = e.toBlock()) }
        }
    }

    /** Step 2 (and Regenerate): ask for a full draft. Nothing is saved to the schedule yet. */
    fun generate(feedback: String = "") {
        val s = _state.value
        job?.cancel()
        job = viewModelScope.launch {
            checkReady(c)?.let { _state.value = _state.value.copy(block = it); return@launch }
            val answers = answersList(s)
            persist { it.copy(answersJson = AiJson.encodeToString(ListSerializer(QuestionAnswer.serializer()), answers)) }
            _state.value = s.copy(step = BuilderStep.WORKING, working = if (feedback.isBlank() && s.plan == null) "Building your plan… this usually takes under a minute." else "Revising your plan…", block = null)
            val payload = ContextBuilder.plan(
                Dates.today(), startDate(), s.text, s.intake, answers, repo.getCategories(), repo.getGoals(),
                repo.settings.current().capacity, previousSummary = s.plan?.draft?.summary.orEmpty(), feedback = feedback,
            )
            runCatching { c.ai.generatePlan(payload) }
                .onSuccess { draft ->
                    val v = validate(draft)
                    if (v.hasErrors) {
                        _state.value = _state.value.copy(step = if (s.plan != null) BuilderStep.PREVIEW else BuilderStep.QUESTIONS.takeIf { s.intake?.questions?.isNotEmpty() == true } ?: BuilderStep.DESCRIBE,
                            block = AiBlock.Failed("The plan couldn't be used: " + v.issues.filter { it.level == app.levelup2027.ai.IssueLevel.ERROR }.joinToString(" ") { it.message }, true))
                    } else {
                        persist { it.copy(draftJson = AiJson.encodeToString(PlanDraft.serializer(), v.draft), lastError = "") }
                        _state.value = _state.value.copy(step = BuilderStep.PREVIEW, plan = v)
                    }
                }
                .onFailure { e ->
                    persist { it.copy(lastError = e.message.orEmpty()) }
                    _state.value = _state.value.copy(
                        step = when { s.plan != null -> BuilderStep.PREVIEW; s.intake?.questions?.isNotEmpty() == true -> BuilderStep.QUESTIONS; else -> BuilderStep.DESCRIBE },
                        block = e.toBlock(),
                    )
                }
        }
    }

    fun cancelWork() {
        job?.cancel()
        val s = _state.value
        _state.value = s.copy(step = when { s.plan != null -> BuilderStep.PREVIEW; s.intake?.questions?.isNotEmpty() == true -> BuilderStep.QUESTIONS; else -> BuilderStep.DESCRIBE })
    }

    fun backToDescribe() { _state.value = _state.value.copy(step = BuilderStep.DESCRIBE) }

    // ---- Local edits in the preview (no AI call) ----

    private fun editDraft(f: (PlanDraft) -> PlanDraft) = viewModelScope.launch {
        val p = _state.value.plan ?: return@launch
        val v = validate(f(p.draft))
        persist { it.copy(draftJson = AiJson.encodeToString(PlanDraft.serializer(), v.draft)) }
        _state.value = _state.value.copy(plan = v)
    }

    fun removeGoal(key: String) = editDraft { d ->
        d.copy(goals = d.goals.filter { it.key != key }, habits = d.habits.filter { it.goalKey != key }, firstWeek = d.firstWeek.filter { it.goalKey != key },
            weeklyOutcomes = d.weeklyOutcomes.filter { it.goalKey != key })
    }
    fun updateGoal(g: DraftGoal) = editDraft { d -> d.copy(goals = d.goals.map { if (it.key == g.key) g else it }) }
    fun removeTask(key: String) = editDraft { d -> d.copy(habits = d.habits.filter { it.key != key }, firstWeek = d.firstWeek.filter { it.key != key }) }
    fun updateTask(t: DraftTask) = editDraft { d ->
        d.copy(habits = d.habits.map { if (it.key == t.key) t else it }, firstWeek = d.firstWeek.map { if (it.key == t.key) t else it })
    }
    fun setCapacity(weekday: Int?, weekend: Int?) = editDraft { it.copy(weekdayMinutes = weekday, weekendMinutes = weekend) }

    /** Accept: the only step that writes to the schedule. */
    fun accept() = viewModelScope.launch {
        val p = _state.value.plan ?: return@launch
        val m = materializer.materialize(p, draftId, repo.getCategories(), repo.getAllTasks(), repo.getGoals())
        val contextJson = _state.value.intake?.let { AiJson.encodeToString(app.levelup2027.ai.UserContext.serializer(), it.context) }.orEmpty()
        repo.acceptPlan(m, draftId, p.startDate, p.draft.summary.ifBlank { "Plan accepted" }, contextJson)
        _state.value = _state.value.copy(step = BuilderStep.DONE, acceptedSummary = "${m.goals.size} goals and ${m.tasks.size} tasks added." +
            if (m.skippedDuplicates.isNotEmpty()) " ${m.skippedDuplicates.size} duplicates were skipped." else "")
    }

    /** Throws the current draft away (the user chose "Start over"). */
    fun discard() = viewModelScope.launch {
        if (_state.value.step != BuilderStep.DONE) persist { it.copy(status = DraftStatus.DISCARDED) }
        finish()
    }

    /** Clears the screen state after a plan was accepted; the accepted draft record is left as is. */
    fun finish() {
        job?.cancel()
        draftId = UUID.randomUUID().toString()
        _state.value = BuilderState()
    }
}

// ---------------------------------------------------------------- Adjust my plan

data class AdjustState(
    val turns: List<ChatTurn> = emptyList(),
    val input: String = "",
    val working: Boolean = false,
    val reply: String = "",
    val question: String = "",
    val proposal: ValidatedChanges? = null,
    val selected: Set<Int> = emptySet(),
    val block: AiBlock? = null,
    val applied: String = "",
)

class AdjustViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val _state = MutableStateFlow(AdjustState())
    val state: StateFlow<AdjustState> = _state
    private var job: Job? = null

    fun setInput(t: String) { _state.value = _state.value.copy(input = t) }
    fun toggle(i: Int) { val s = _state.value; _state.value = s.copy(selected = if (i in s.selected) s.selected - i else s.selected + i) }
    fun dismissBlock() { _state.value = _state.value.copy(block = null) }
    fun reset() { job?.cancel(); _state.value = AdjustState() }

    fun send(textOverride: String? = null) {
        val text = (textOverride ?: _state.value.input).trim()
        if (text.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch {
            checkReady(c)?.let { _state.value = _state.value.copy(block = it); return@launch }
            val turns = _state.value.turns + ChatTurn("user", text)
            _state.value = _state.value.copy(turns = turns, input = "", working = true, block = null, proposal = null, reply = "", question = "", applied = "")
            val today = Dates.today()
            val s = repo.settings.current()
            val tasks = repo.getAllTasks()
            val completions = repo.getAllCompletions()
            val goals = repo.getGoals()
            val payload = ContextBuilder.adjust(
                today, LocalTime.now(), tasks, completions, goals, repo.getMilestones(), s.capacity, s.isLowEnergy(today),
                Repository.hiddenCategories(repo.getCategories()), turns,
            )
            runCatching { c.ai.adjust(payload) }
                .onSuccess { r ->
                    val validated = ChangeValidator.validate(r.changes, PlanState(today, tasks, completions, goals, s.capacity)) { title ->
                        "t-" + PlanValidator.slug(title).take(20) + "-" + UUID.randomUUID().toString().take(6)
                    }
                    val replyTurn = ChatTurn("assistant", if (r.needsClarification) r.question else r.reply)
                    if (s.ai.autoApplySimple && validated.allSimple && !r.needsClarification) {
                        repo.applyChanges(validated.changes, "AI_ADJUST_AUTO", text)
                        _state.value = _state.value.copy(turns = turns + replyTurn, working = false, reply = r.reply,
                            applied = "Applied: " + validated.changes.joinToString("; ") { it.summary })
                    } else {
                        _state.value = _state.value.copy(
                            turns = turns + replyTurn, working = false, reply = r.reply,
                            question = if (r.needsClarification) r.question else "",
                            proposal = validated,
                            selected = validated.changes.indices.toSet(),
                        )
                    }
                }
                .onFailure { e -> _state.value = _state.value.copy(working = false, block = e.toBlock(), input = text, turns = turns.dropLast(1)) }
        }
    }

    fun applySelected() = viewModelScope.launch {
        val s = _state.value
        val p = s.proposal ?: return@launch
        val chosen = p.changes.filterIndexed { i, _ -> i in s.selected }
        if (chosen.isEmpty()) return@launch
        runCatching { repo.applyChanges(chosen, "AI_ADJUST", s.turns.lastOrNull { it.role == "user" }?.text.orEmpty()) }
            .onSuccess { _state.value = s.copy(proposal = null, applied = "Done: " + chosen.joinToString("; ") { it.summary }) }
            .onFailure { e -> _state.value = s.copy(block = AiBlock.Failed("Couldn't apply: ${e.message?.take(120)}", false)) }
    }

    fun rejectProposal() { _state.value = _state.value.copy(proposal = null, applied = "No changes made.") }
}

// ---------------------------------------------------------------- Plan next week

data class WeekState(
    val weekStart: LocalDate? = null,
    val working: Boolean = false,
    val plan: WeekPlan? = null,
    val issues: List<PlanIssue> = emptyList(),
    val block: AiBlock? = null,
    val done: String = "",
)

class WeekPlanViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val _state = MutableStateFlow(WeekState())
    val state: StateFlow<WeekState> = _state
    private val materializer = PlanMaterializer()

    fun dismissBlock() { _state.value = _state.value.copy(block = null) }

    fun generate(weekStart: LocalDate) = viewModelScope.launch {
        checkReady(c)?.let { _state.value = _state.value.copy(block = it, weekStart = weekStart); return@launch }
        _state.value = WeekState(weekStart = weekStart, working = true)
        val s = repo.settings.current()
        val tasks = repo.getAllTasks()
        val goals = repo.getGoals().filter { it.isActive }
        val categories = repo.getCategories()
        val payload = ContextBuilder.week(Dates.today(), weekStart, tasks, repo.getAllCompletions(), goals, repo.getMilestones(), categories, s.capacity, s.userContextJson)
        runCatching { c.ai.planWeek(payload) }
            .onSuccess { w ->
                val habits = tasks.filter { it.enabled && it.recurrenceRule != null }
                val existing = tasks.filter { it.enabled && it.scheduledDate != null }.mapTo(HashSet()) { it.title to it.scheduledDate!! }
                val (clean, issues) = PlanValidator.validateWeek(
                    w, weekStart, goals.associate { it.id to it.title }, categories, existing, s.capacity.weekdayMinutes, s.capacity.weekendMinutes,
                ) { d -> ScheduleEngine.tasksFor(d, habits).sumOf { it.estimatedIdealMinutes } }
                _state.value = WeekState(weekStart = weekStart, plan = clean, issues = issues)
            }
            .onFailure { e -> _state.value = WeekState(weekStart = weekStart, block = e.toBlock()) }
    }

    fun removeTask(key: String) {
        val p = _state.value.plan ?: return
        _state.value = _state.value.copy(plan = p.copy(tasks = p.tasks.filter { it.key != key }))
    }

    fun accept() = viewModelScope.launch {
        val st = _state.value
        val p = st.plan ?: return@launch
        val start = st.weekStart ?: return@launch
        val (tasks, outcomes) = materializer.materializeWeek(p, start, "week-$start", repo.getCategories(), repo.getGoals())
        repo.acceptWeek(tasks, outcomes, null, p.summary.ifBlank { "Planned week of $start" })
        _state.value = WeekState(done = "${tasks.size} tasks added for the week of ${app.levelup2027.domain.DateFmt.short(start)}.")
    }

    fun discard() { _state.value = WeekState() }
}
