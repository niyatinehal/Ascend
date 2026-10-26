package app.levelup2027.data

import app.levelup2027.domain.Recurrence
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The initial plan. Task ids are stable: re-seeding only inserts ids that don't exist yet,
 * so the user's edits are never overwritten. Bump [VERSION] when adding new seed rows.
 */
object Seed {
    const val VERSION = 1

    private fun bullets(vararg lines: String) = lines.joinToString("\n") { "• $it" }

    private fun recurring(
        id: String, title: String, description: String, category: String, slot: TimeSlot,
        ideal: String, minimum: String, idealMin: Int, minMin: Int, rule: String, goalId: String?,
        energy: EnergyLevel, sort: Int, workout: Boolean = false, resources: String = "", notes: String = "",
        activeFrom: LocalDate? = null,
    ) = Task(
        id = id, title = title, description = description, category = category, timeSlot = slot,
        idealDescription = ideal, minimumDescription = minimum, estimatedIdealMinutes = idealMin,
        estimatedMinimumMinutes = minMin, recurrenceRule = rule, goalId = goalId, energyLevel = energy,
        sortOrder = sort, countsAsWorkout = workout, resources = resources, notes = notes,
        activeFrom = activeFrom ?: Campaign.START, activeUntil = Campaign.END, isSeed = true, source = Source.SEED,
    )

    private fun weekly(vararg d: DayOfWeek) = Recurrence.weekly(*d)

    private const val JS_TOPICS = "Topic bank: scope & closures, `this`, prototypes, the event loop, promises, async/await, error handling, modules, TypeScript types, generics, narrowing, utility types."
    private const val BACKEND_TOPICS = "Topic bank: HTTP & status codes, REST design, SQL & joins, indexes, transactions, authentication, authorization, caching, queues, rate limiting, logging & monitoring, testing."
    private const val SYSDESIGN_TOPICS = "Topic bank: load balancing, caching layers, replication, sharding, queues, idempotency, rate limiting, consistency trade-offs, failure handling, observability."
    private const val DSA_TOPICS = "Suggested order: arrays & strings → hash maps → two pointers → sliding window → stacks & queues → linked lists → binary search → trees → graphs (BFS/DFS) → heaps → DP basics."

    val recurringTasks: List<Task> = listOf(
        // Daily
        recurring(
            "daily-morning", "Morning routine & today's priorities",
            "Your normal morning, plus a quick look at today's plan.",
            Cat.PLANNING, TimeSlot.MORNING,
            bullets("Follow your normal morning routine: breakfast, get ready", "Review today's tasks and pick the top priority"),
            bullets("Review today's top priority", "Begin getting ready"),
            15, 3, "DAILY", null, EnergyLevel.LOW, 0,
        ),
        // During work: short observations only
        recurring(
            "mon-work-question", "Capture one technical question",
            "A short note from your real work. Skip it if nothing meaningful came up.",
            Cat.LEARNING, TimeSlot.WORK,
            bullets("Record the problem, its context, and what you want to understand"),
            bullets("Write one question in a note"),
            10, 2, weekly(MONDAY), GoalIds.TECH, EnergyLevel.LOW, 10,
        ),
        recurring(
            "tue-work-backend", "Spot one backend concept at work",
            "Notice a backend idea in your job: an API, a query, a cache, a queue.",
            Cat.LEARNING, TimeSlot.WORK,
            bullets("Note one backend concept you met at work", "Write where you saw it and what you want to learn about it"),
            bullets("Write the concept's name in a note"),
            10, 2, weekly(TUESDAY), GoalIds.TECH, EnergyLevel.LOW, 10,
        ),
        recurring(
            "wed-work-bizproblem", "Record one operational or business problem",
            "Raw material for business discovery: slow, manual, error-prone work you see.",
            Cat.BUSINESS, TimeSlot.WORK,
            bullets("Note a repetitive, slow or error-prone process", "Who suffers from it, and how often?"),
            bullets("Write the problem in one line"),
            10, 2, weekly(WEDNESDAY), GoalIds.BUSINESS, EnergyLevel.LOW, 10,
        ),
        recurring(
            "thu-work-failure", "Pick a system and one failure scenario",
            "Practise thinking like a designer about systems you already touch.",
            Cat.LEARNING, TimeSlot.WORK,
            bullets("Pick a system you work with", "Write one way it could fail and what would happen"),
            bullets("Name the system and one failure in a note"),
            10, 2, weekly(THURSDAY), GoalIds.TECH, EnergyLevel.LOW, 10,
        ),
        recurring(
            "fri-work-solved", "Record a problem you solved independently",
            "Evidence of independent engineering, useful later in interviews.",
            Cat.LEARNING, TimeSlot.WORK,
            bullets("Write the problem, the options you considered, and why you chose your solution"),
            bullets("Write one line about what you solved"),
            10, 2, weekly(FRIDAY), GoalIds.TECH, EnergyLevel.LOW, 10,
        ),
        // Weekday evenings
        recurring(
            "weekday-evening-review", "End-of-day review",
            "Dinner, prepare for tomorrow, and close the day.",
            Cat.PLANNING, TimeSlot.EVENING,
            bullets("Eat dinner and prepare for tomorrow", "Write a short reflection: what got done, what got in the way"),
            bullets("Record one thing you completed", "Name tomorrow's first task"),
            10, 3, weekly(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), null, EnergyLevel.LOW, 90,
        ),
        // Monday
        recurring(
            "mon-tech-js", "JavaScript / TypeScript study",
            "Programming foundations.",
            Cat.LEARNING, TimeSlot.EVENING,
            bullets("Study JavaScript or TypeScript for 30 minutes", "Practise one concept in code", "Explain it in your own words"),
            bullets("Study for 10 minutes", "Write one sentence explaining the concept"),
            30, 10, weekly(MONDAY), GoalIds.TECH, EnergyLevel.MEDIUM, 40, resources = JS_TOPICS,
        ),
        recurring(
            "mon-gym", "Gym: quads, glutes & core",
            "Training session 1 of 4 this week.",
            Cat.FITNESS, TimeSlot.EVENING,
            bullets("Complete the planned quad, glute and core workout", "Record sets, reps and weights"),
            bullets("Do a shortened, safe version (main lift + one accessory) if appropriate"),
            60, 25, weekly(MONDAY), GoalIds.FITNESS, EnergyLevel.HIGH, 30, workout = true,
            notes = "Progress gradually. Stop any exercise that causes sharp pain.",
        ),
        // Tuesday
        recurring(
            "tue-tech-backend", "Backend engineering study",
            "One backend topic, understood properly.",
            Cat.LEARNING, TimeSlot.EVENING,
            bullets(
                "Study one backend topic for 35 minutes",
                "Answer: what is it, what problem does it solve, how does it work, when to use it, what can go wrong",
            ),
            bullets("Study for 10 minutes", "Write one sentence explaining the topic"),
            35, 10, weekly(TUESDAY), GoalIds.TECH, EnergyLevel.MEDIUM, 40, resources = BACKEND_TOPICS,
        ),
        recurring(
            "tue-gym", "Gym: back & arms + pull-up progression",
            "Training session 2 of 4 this week.",
            Cat.FITNESS, TimeSlot.EVENING,
            bullets(
                "Complete the planned back and arm workout",
                "Include a pull-up progression: dead hangs, negatives, band-assisted or scapular pulls",
                "Record the results",
            ),
            bullets("Do a shortened workout or a 15-minute technique session if appropriate"),
            60, 20, weekly(TUESDAY), GoalIds.FITNESS, EnergyLevel.HIGH, 30, workout = true,
        ),
        // Wednesday: recovery
        recurring(
            "wed-biz-discovery", "Business discovery",
            "Find real problems before building anything.",
            Cat.BUSINESS, TimeSlot.EVENING,
            bullets(
                "Spend 30 minutes investigating a customer segment",
                "Record one problem, one potential customer, and evidence to investigate",
            ),
            bullets("Spend 10 minutes researching one potential customer problem"),
            30, 10, weekly(WEDNESDAY), GoalIds.BUSINESS, EnergyLevel.MEDIUM, 40,
        ),
        recurring(
            "wed-recovery", "Recovery: easy walk or mobility",
            "No gym today. Recovery is part of training.",
            Cat.HEALTH, TimeSlot.EVENING,
            bullets("Take an easy walk or do gentle mobility", "Prioritise recovery and sleep"),
            bullets("Take a short, comfortable walk, or simply rest"),
            25, 5, weekly(WEDNESDAY), GoalIds.FITNESS, EnergyLevel.LOW, 30,
        ),
        // Thursday
        recurring(
            "thu-tech-sysdesign", "System design study",
            "Design decisions, failure modes and trade-offs.",
            Cat.LEARNING, TimeSlot.EVENING,
            bullets(
                "Study system design for 30 minutes",
                "Draw a small diagram or explain one design decision",
                "Include one failure scenario and one trade-off",
            ),
            bullets("Study for 10 minutes", "Write one design question or trade-off"),
            30, 10, weekly(THURSDAY), GoalIds.TECH, EnergyLevel.MEDIUM, 40, resources = SYSDESIGN_TOPICS,
        ),
        recurring(
            "thu-gym", "Gym: glutes & hamstrings",
            "Training session 3 of 4 this week.",
            Cat.FITNESS, TimeSlot.EVENING,
            bullets("Complete the planned glute and hamstring workout", "Record sets, reps and weights"),
            bullets("Do a shortened, safe version if appropriate"),
            60, 25, weekly(THURSDAY), GoalIds.FITNESS, EnergyLevel.HIGH, 30, workout = true,
        ),
        // Friday
        recurring(
            "fri-tech-dsa", "DSA practice",
            "Interview problem-solving, one problem at a time.",
            Cat.CAREER, TimeSlot.EVENING,
            bullets(
                "Attempt one problem independently first",
                "Explain the approach and its time/space complexity",
                "Re-solve it without looking at the answer",
            ),
            bullets("Spend 10 minutes attempting the problem or understanding one relevant concept"),
            40, 10, weekly(FRIDAY), GoalIds.CAREER, EnergyLevel.MEDIUM, 40, resources = DSA_TOPICS,
        ),
        recurring(
            "fri-gym", "Gym: chest, shoulders & triceps + push-ups",
            "Training session 4 of 4 this week.",
            Cat.FITNESS, TimeSlot.EVENING,
            bullets(
                "Complete the planned chest, shoulder and triceps workout",
                "Practise a push-up progression with good form (incline → knee → full)",
            ),
            bullets("Do a shortened workout or brief technique practice if appropriate"),
            60, 20, weekly(FRIDAY), GoalIds.FITNESS, EnergyLevel.HIGH, 30, workout = true,
        ),
        // Saturday: deep work
        recurring(
            "sat-tech-project", "Project: build one small feature",
            "Saturday morning deep work on your backend project.",
            Cat.LEARNING, TimeSlot.MORNING,
            bullets(
                "Spend 60–90 minutes implementing one small feature",
                "Test it",
                "Commit the changes",
                "Record what you learned",
            ),
            bullets("Spend 30 minutes implementing or testing one small part of the project"),
            75, 30, weekly(SATURDAY), GoalIds.CAREER, EnergyLevel.HIGH, 20,
        ),
        recurring(
            "sat-biz-build", "Business: research, validate or build",
            "Saturday afternoon.",
            Cat.BUSINESS, TimeSlot.FLEXIBLE,
            bullets(
                "Spend 60–90 minutes on customer research, validation, or building a validated solution",
                "Record the evidence and your next step",
            ),
            bullets("Spend 15 minutes on one concrete business action"),
            75, 15, weekly(SATURDAY), GoalIds.BUSINESS, EnergyLevel.MEDIUM, 20,
        ),
        recurring(
            "sat-easy-cardio", "Optional: easy walk or mobility",
            "Only if recovered. The evening is for rest.",
            Cat.FITNESS, TimeSlot.FLEXIBLE,
            bullets("Take an easy walk or do mobility, if you feel recovered"),
            bullets("Rest. Recovery counts."),
            30, 0, weekly(SATURDAY), GoalIds.FITNESS, EnergyLevel.LOW, 30,
        ),
        // Sunday: light
        recurring(
            "sun-fitness-checkin", "Weekly fitness check-in & recovery",
            "Track strength and habits, not only weight. Log numbers in Goals → Fitness.",
            Cat.FITNESS, TimeSlot.MORNING,
            bullets(
                "Record body weight (same conditions each week)",
                "Note push-up and pull-up progress",
                "Note energy, sleep and soreness; light mobility",
            ),
            bullets("Record body weight or one strength number"),
            20, 5, weekly(SUNDAY), GoalIds.FITNESS, EnergyLevel.LOW, 20,
        ),
        recurring(
            "sun-tech-review", "Technical review",
            "Sunday afternoon: consolidate what you learned this week.",
            Cat.LEARNING, TimeSlot.FLEXIBLE,
            bullets("Review one concept learned this week", "Explain it without reading your notes"),
            bullets("Spend 10 minutes reviewing one concept"),
            25, 10, weekly(SUNDAY), GoalIds.TECH, EnergyLevel.LOW, 20,
        ),
        recurring(
            "sun-finance", "Finances & parents' phone fund",
            "Emergency savings stay untouched. Log the balance in Goals → Parents' phones.",
            Cat.FINANCE, TimeSlot.EVENING,
            bullets(
                "Review this week's spending and savings",
                "Update the parents' phone fund balance",
                "Check progress toward the savings target",
            ),
            bullets("Record current balances and spending"),
            20, 5, weekly(SUNDAY), GoalIds.FAMILY, EnergyLevel.LOW, 20,
        ),
        recurring(
            "sun-weekly-review", "Weekly review & plan next week",
            "Use the Review tab to save your answers.",
            Cat.PLANNING, TimeSlot.EVENING,
            bullets(
                "Review the week's completed tasks",
                "Record technical, fitness, business and financial progress",
                "Decide next week's priorities",
            ),
            bullets("Spend 15 minutes reviewing the week"),
            40, 15, weekly(SUNDAY), null, EnergyLevel.MEDIUM, 30,
        ),
        // Roadmap phase: job search begins March 2027, ahead of the June interviewing checkpoint.
        recurring(
            "sun-career-search", "Job search & interview practice",
            "From March 2027, aiming for ₹10–15 LPA remote/hybrid roles by June.",
            Cat.CAREER, TimeSlot.FLEXIBLE,
            bullets(
                "Apply to 2–3 well-matched roles, or do one 20-minute mock interview",
                "Log applications and interviews in Goals → Career",
            ),
            bullets("Shortlist one role, or answer one interview question out loud"),
            30, 10, weekly(SUNDAY), GoalIds.CAREER, EnergyLevel.MEDIUM, 25,
            activeFrom = LocalDate.of(2027, 3, 1),
        ),
    )

    private val byId = recurringTasks.associateBy { it.id }

    /** A dated curriculum task that replaces the recurring task [base] on [date]. */
    private fun cur(id: String, date: LocalDate, base: String, title: String, ideal: String, minimum: String, idealMin: Int? = null, minMin: Int? = null): Task {
        val b = byId.getValue(base)
        require(Recurrence.parse(b.recurrenceRule)!!.matches(date)) { "$id: $base is not scheduled on $date" }
        return b.copy(
            id = id, title = title, description = "Curriculum week ${id.substringAfter("cur-w").first()} · replaces \"${b.title}\" today.",
            idealDescription = ideal, minimumDescription = minimum,
            estimatedIdealMinutes = idealMin ?: b.estimatedIdealMinutes,
            estimatedMinimumMinutes = minMin ?: b.estimatedMinimumMinutes,
            scheduledDate = date, recurrenceRule = null, supersedesTaskId = base,
            resources = "", notes = b.notes,
        )
    }

    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    val curriculumTasks: List<Task> = listOf(
        // Week 1: 26 Oct – 1 Nov 2026 — baselines
        cur("cur-w1-mon-gym", d(10, 26), "mon-gym", "Fitness baseline + first session",
            bullets("Do the planned quad/glute/core workout at a comfortable load", "Record baseline: body weight, max good-form push-ups, pull-ups (dead-hang time if 0)", "Save the numbers in Goals → Fitness"),
            bullets("Record body weight and one strength baseline")),
        cur("cur-w1-mon-js", d(10, 26), "mon-tech-js", "List your JS/TS strengths & weaknesses",
            bullets("Spend 30 minutes listing JS/TS topics you're confident in vs. shaky on", "Rate each 1–3 and pick the first three to study"),
            bullets("Write three strengths and three weaknesses")),
        cur("cur-w1-tue-backend", d(10, 27), "tue-tech-backend", "Technical baseline: backend",
            bullets("Write what you already know about HTTP, APIs, databases and auth", "Mark the gaps: this becomes your study list"),
            bullets("List five backend topics you can't yet explain")),
        cur("cur-w1-wed-biz", d(10, 28), "wed-biz-discovery", "Identify three customer segments",
            bullets("List three customer segments you can actually reach", "For each: a likely painful problem and where to find these people"),
            bullets("Write down three segments, one line each")),
        cur("cur-w1-thu-sys", d(10, 29), "thu-tech-sysdesign", "Draw one existing system workflow",
            bullets("Draw a system from work as boxes and arrows", "Mark one failure point and one trade-off"),
            bullets("Sketch three boxes and the arrows between them")),
        cur("cur-w1-fri-dsa", d(10, 30), "fri-tech-dsa", "Attempt one easy DSA problem",
            bullets("Pick an easy array or string problem", "Attempt it alone for 20 minutes", "Explain your approach and its complexity"),
            bullets("Read one easy problem and write your first idea")),
        cur("cur-w1-sat-project", d(10, 31), "sat-tech-project", "Define your backend project's scope",
            bullets("Choose a small backend project you can finish (e.g. a REST API with auth + database)", "Write the scope: 3–5 features, the stack, and a 'not doing' list"),
            bullets("Write the project idea and three features"), 60, 20),
        cur("cur-w1-sun-review", d(11, 1), "sun-weekly-review", "Week 1 review",
            bullets("Review your baselines and the week", "Note what went well and what blocked you", "Set week 2's main priority"),
            bullets("15 minutes: one win, one blocker")),

        // Week 2: 2–8 Nov 2026
        cur("cur-w2-mon-js", d(11, 2), "mon-tech-js", "Async JavaScript: promises & async/await",
            bullets("Study promises and async/await for 30 minutes", "Write a small example that chains two async calls with error handling", "Explain the event loop's role in your own words"),
            bullets("Study for 10 minutes", "Write one sentence on what a promise is")),
        cur("cur-w2-tue-backend", d(11, 3), "tue-tech-backend", "HTTP: requests, responses & status codes",
            bullets("Study request/response structure, methods, headers and status codes", "Inspect real requests in your browser's network tab", "Explain when to use 400 vs 401 vs 403 vs 404 vs 500"),
            bullets("Study for 10 minutes", "Write what a status code tells the client")),
        cur("cur-w2-wed-biz", d(11, 4), "wed-biz-discovery", "Pick one segment & find five contacts",
            bullets("Choose one segment from week 1", "Find five real people or businesses you could talk to", "Note how you'll reach each one"),
            bullets("Pick the segment and find one contact")),
        cur("cur-w2-thu-sys", d(11, 5), "thu-tech-sysdesign", "Basic API design: define a few endpoints",
            bullets("Design 4–6 REST endpoints for your project", "For each: method, path, request, response, error cases", "Note one trade-off you made"),
            bullets("Write two endpoints with method and path")),
        cur("cur-w2-fri-dsa", d(11, 6), "fri-tech-dsa", "DSA: arrays & strings",
            bullets("Solve one array/string problem independently", "Explain the approach and complexity", "Re-solve it without looking"),
            bullets("Spend 10 minutes on one array or string problem")),
        cur("cur-w2-sat-project", d(11, 7), "sat-tech-project", "Project setup + one small feature",
            bullets("Set up the repo, runtime and a basic server", "Implement one endpoint end-to-end", "Commit and note what you learned"),
            bullets("Spend 30 minutes on the project setup")),
        cur("cur-w2-sun-review", d(11, 8), "sun-weekly-review", "Week 2 review",
            bullets("Review the week's tasks and evidence", "Record progress across goals", "Set week 3's main priority"),
            bullets("15 minutes: one win, one blocker")),

        // Week 3: 9–15 Nov 2026
        cur("cur-w3-mon-js", d(11, 9), "mon-tech-js", "JavaScript error handling",
            bullets("Study try/catch, error types, async error handling and custom errors", "Add proper error handling to one piece of code", "Explain what should happen when things fail"),
            bullets("Study for 10 minutes", "Write one sentence on handling async errors")),
        cur("cur-w3-tue-backend", d(11, 10), "tue-tech-backend", "SQL & database fundamentals",
            bullets("Study tables, keys, joins and indexes", "Write three queries against a sample schema", "Explain when an index helps and what it costs"),
            bullets("Study for 10 minutes", "Write one query with a JOIN")),
        cur("cur-w3-wed-biz", d(11, 11), "wed-biz-discovery", "Customer conversation (if possible)",
            bullets("Talk to one potential customer about their problem, not your idea", "Record what they do today, what it costs them, and quotes", "Log the conversation in Goals → Business"),
            bullets("Send one message asking for a short conversation")),
        cur("cur-w3-thu-sys", d(11, 12), "thu-tech-sysdesign", "Database transactions",
            bullets("Study ACID and isolation levels", "Describe one failure a transaction prevents in your project", "Note one trade-off (consistency vs. throughput)"),
            bullets("Write what 'atomic' means with one example")),
        cur("cur-w3-fri-dsa", d(11, 13), "fri-tech-dsa", "DSA: attempt & explain one problem",
            bullets("Attempt one problem independently", "Explain the solution and complexity out loud as if in an interview"),
            bullets("Spend 10 minutes attempting one problem")),
        cur("cur-w3-sat-project", d(11, 14), "sat-tech-project", "Project feature + a useful test",
            bullets("Add one feature (e.g. persistence to a database)", "Write at least one useful automated test", "Commit and note what you learned"),
            bullets("Spend 30 minutes writing one test")),
        cur("cur-w3-sun-review", d(11, 15), "sun-weekly-review", "Week 3 review",
            bullets("Review the week's tasks and evidence", "Record progress across goals", "Set week 4's main priority"),
            bullets("15 minutes: one win, one blocker")),

        // Week 4: 16–22 Nov 2026
        cur("cur-w4-mon-js", d(11, 16), "mon-tech-js", "Review async JavaScript",
            bullets("Re-explain promises, async/await and the event loop without notes", "Fix or improve one async snippet from week 2"),
            bullets("Explain async/await in two sentences")),
        cur("cur-w4-tue-backend", d(11, 17), "tue-tech-backend", "Authentication & authorization",
            bullets("Study sessions vs. tokens (JWT) and password hashing", "Explain authentication vs. authorization", "Note what can go wrong in each"),
            bullets("Study for 10 minutes", "Write the difference between authN and authZ")),
        cur("cur-w4-wed-biz", d(11, 18), "wed-biz-discovery", "Summarise the problem & customer evidence",
            bullets("Write a one-page summary: segment, problem, evidence, quotes", "Decide: continue, adjust, or switch segment"),
            bullets("Write the problem statement in two sentences")),
        cur("cur-w4-thu-sys", d(11, 19), "thu-tech-sysdesign", "Caching and its trade-offs",
            bullets("Study cache-aside, TTLs and invalidation", "Decide where a cache would help your project", "Describe one stale-data failure and how you'd handle it"),
            bullets("Write one benefit and one risk of caching")),
        cur("cur-w4-fri-dsa", d(11, 20), "fri-tech-dsa", "DSA: new problem + revisit an earlier one",
            bullets("Attempt one new problem independently", "Re-solve an earlier problem from memory", "Compare how your approach has changed"),
            bullets("Re-solve one earlier problem for 10 minutes")),
        cur("cur-w4-sat-project", d(11, 21), "sat-tech-project", "Complete a feature & update the README",
            bullets("Finish one coherent feature end-to-end", "Update the README: what it does, how to run it, design decisions", "Commit"),
            bullets("Spend 30 minutes on the README")),
        cur("cur-w4-sun-review", d(11, 22), "sun-weekly-review", "Month review & adjust priorities",
            bullets("Review four weeks of progress across all goals", "Decide what to keep, drop and change", "Set December's priorities toward the 31 Dec checkpoint"),
            bullets("20 minutes: three wins, one change for December"), 60, 20),
    )

    private fun eom(y: Int, m: Int) = LocalDate.of(y, m, 1).with(TemporalAdjusters.lastDayOfMonth())

    val goals: List<Goal> = listOf(
        Goal(GoalIds.CAREER, "Career: ₹10 LPA+ role",
            "Move from ₹6 LPA to at least ₹10 LPA (ideally ₹10–15 LPA or more) with remote/hybrid flexibility.",
            "≥ ₹10 LPA (ideal ₹10–15 LPA+), remote/hybrid", Campaign.END, "₹6 LPA", "LPA", sortOrder = 0, shortName = "Career", source = Source.SEED, categoryId = Cat.CAREER),
        Goal(GoalIds.FITNESS, "Fitness: strength & sustainable progress",
            "From approximately 63 kg toward 45–48 kg, while building to 5 strict pull-ups and 50 consecutive push-ups with good form. These are targets, not guarantees. No crash dieting, no unsafe training.",
            "45–48 kg · 5 strict pull-ups · 50 push-ups", Campaign.END, "~63 kg (estimate)", "kg", sortOrder = 1, shortName = "Fitness", source = Source.SEED, categoryId = Cat.FITNESS),
        Goal(GoalIds.TECH, "Technical confidence",
            "Become an engineer who analyses problems, evaluates architectural options, explains trade-offs and proposes solutions, not only implements assigned tasks. Tracked through exercises and analysed problems, not a made-up percentage.",
            "Demonstrated independent engineering reasoning", Campaign.END, "Starting out", "exercises", sortOrder = 2, shortName = "Tech confidence", source = Source.SEED, categoryId = Cat.LEARNING),
        Goal(GoalIds.FAMILY, "New phones for both parents",
            "Save for two phones without compromising emergency savings. Target amount and date are editable.",
            "₹35,000 phone fund", Campaign.END, "Not recorded yet", "₹", sortOrder = 3, shortName = "Parents' phones", source = Source.SEED, categoryId = Cat.FAMILY),
        Goal(GoalIds.BUSINESS, "Business: ₹10,000 MRR",
            "Start with little or no upfront investment (micro-SaaS or service-to-product) and work toward ₹10,000 monthly recurring revenue.",
            "₹10,000 MRR", Campaign.END, "₹0 MRR (no business yet)", "₹/month", sortOrder = 4, shortName = "Business", source = Source.SEED, categoryId = Cat.BUSINESS),
    )

    val milestones: List<Milestone> = listOf(
        Milestone("career-2026-12", GoalIds.CAREER, eom(2026, 12), "Identify technical gaps and establish a consistent learning routine", sortOrder = 0),
        Milestone("career-2027-02", GoalIds.CAREER, eom(2027, 2), "Strengthen DSA and backend foundations", sortOrder = 1),
        Milestone("career-2027-04", GoalIds.CAREER, eom(2027, 4), "Demonstrate a coherent backend project and practise interviews", sortOrder = 2),
        Milestone("career-2027-06", GoalIds.CAREER, eom(2027, 6), "Actively interview for ₹10–15 LPA opportunities", sortOrder = 3),
        Milestone("career-2027-12", GoalIds.CAREER, eom(2027, 12), "Target a substantial salary increase", sortOrder = 4),

        Milestone("fitness-2026-12", GoalIds.FITNESS, eom(2026, 12), "Establish consistent training and baseline strength", sortOrder = 0),
        Milestone("fitness-2027-03", GoalIds.FITNESS, eom(2027, 3), "Make measurable strength and body-composition progress", sortOrder = 1),
        Milestone("fitness-2027-06", GoalIds.FITNESS, eom(2027, 6), "Review progress and adjust training", sortOrder = 2),
        Milestone("fitness-2027-12", GoalIds.FITNESS, eom(2027, 12), "Aim toward the desired weight range and strength targets", sortOrder = 3),

        Milestone("tech-2026-11", GoalIds.TECH, eom(2026, 11), "Build the habit of analysing one technical question at a time", sortOrder = 0),
        Milestone("tech-2026-12", GoalIds.TECH, eom(2026, 12), "Establish foundations and record technical reasoning", sortOrder = 1),
        Milestone("tech-2027-03", GoalIds.TECH, eom(2027, 3), "Independently analyse more unfamiliar problems", sortOrder = 2),
        Milestone("tech-2027-06", GoalIds.TECH, eom(2027, 6), "Explain design choices and trade-offs in interview practice", sortOrder = 3),
        Milestone("tech-2027-12", GoalIds.TECH, eom(2027, 12), "Build confidence through demonstrated engineering ability", sortOrder = 4),

        Milestone("business-2026-12", GoalIds.BUSINESS, eom(2026, 12), "Investigate customer problems", sortOrder = 0),
        Milestone("business-2027-02", GoalIds.BUSINESS, eom(2027, 2), "Validate a problem and seek a paid pilot", sortOrder = 1),
        Milestone("business-2027-04", GoalIds.BUSINESS, eom(2027, 4), "Aim to have a working solution and initial customers", sortOrder = 2),
        Milestone("business-2027-06", GoalIds.BUSINESS, eom(2027, 6), "Target ₹2,000–₹5,000 MRR", sortOrder = 3),
        Milestone("business-2027-12", GoalIds.BUSINESS, eom(2027, 12), "Target ₹10,000 MRR", sortOrder = 4),
    )

    /**
     * Roadmap checkpoints generated from the milestones: one light task on the last Sunday on or
     * before each milestone month-end, combining all milestones that fall due that month.
     */
    val checkpointTasks: List<Task> = milestones
        .groupBy { it.dueDate.with(TemporalAdjusters.previousOrSame(SUNDAY)) }
        .toSortedMap()
        .map { (sunday, ms) ->
            val month = ms.first().dueDate
            Task(
                id = "checkpoint-${month.year}-${"%02d".format(month.monthValue)}",
                title = "Roadmap checkpoint: ${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}",
                description = "Milestones due this month:\n" + ms.joinToString("\n") { "• ${GoalIds.shortName(it.goalId)}: ${it.title}" },
                category = Cat.PLANNING, timeSlot = TimeSlot.EVENING,
                idealDescription = bullets(
                    "Compare where you are against each milestone above",
                    "Mark achieved milestones in Goals, or adjust the plan without judgement",
                ),
                minimumDescription = bullets("Write one sentence per goal on where you are"),
                estimatedIdealMinutes = 30, estimatedMinimumMinutes = 10,
                scheduledDate = sunday, goalId = null, energyLevel = EnergyLevel.LOW, sortOrder = 35, isSeed = true, source = Source.SEED,
            )
        }

    val allTasks: List<Task> get() = recurringTasks + curriculumTasks + checkpointTasks

    /** Starting values taken only from what the plan states. Everything else starts unrecorded. */
    val initialProgress: List<ProgressEntry> = listOf(
        ProgressEntry(goalId = GoalIds.CAREER, date = Campaign.START, metric = "salary_lpa", value = 6.0, notes = "Starting salary from the plan"),
        ProgressEntry(goalId = GoalIds.CAREER, date = Campaign.START, metric = "target_salary_lpa", value = 10.0, notes = "Minimum target (ideal ₹10–15 LPA+)"),
        ProgressEntry(goalId = GoalIds.FITNESS, date = Campaign.START, metric = "weight_kg", value = 63.0, notes = "Approximate starting weight", isEstimate = true),
        ProgressEntry(goalId = GoalIds.FAMILY, date = Campaign.START, metric = "fund_target", value = 35000.0, notes = "Initial target for both phones"),
        ProgressEntry(goalId = GoalIds.BUSINESS, date = Campaign.START, metric = "mrr", value = 0.0, notes = "Starting point: no business yet"),
        ProgressEntry(goalId = GoalIds.BUSINESS, date = Campaign.START, metric = "paying_customers", value = 0.0, notes = "Starting point: no business yet"),
    )
}
