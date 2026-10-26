package app.levelup2027.ai

import kotlinx.serialization.Serializable

/**
 * A starter paragraph for one area of life. Picking several areas in "Build my plan" combines
 * their paragraphs into the description, which the user then edits. Users can edit these texts,
 * add their own areas, or reset to the defaults (Settings → Plan prompts).
 */
@Serializable
data class PromptTemplate(
    val id: String,
    val label: String,
    val text: String,
    val icon: String = "label",
    val builtIn: Boolean = false,
)

object PromptTemplates {
    val DEFAULTS: List<PromptTemplate> = listOf(
        PromptTemplate("week", "My week",
            "About my week: I work (or study) from 9 to 6 on weekdays, and have about an hour free most evenings and 2–3 hours on weekends. I'd rather do demanding things in the morning.",
            "planning", true),
        PromptTemplate("fitness", "Fitness",
            "Fitness: I want to get stronger and fitter. Right now I exercise rarely. I'd like to be able to run 5 km without stopping and do 10 push-ups within 3 months. I can train about 3 times a week.",
            "fitness", true),
        PromptTemplate("weight", "Healthy weight",
            "Weight: I'd like to lose about 5 kg in a healthy, sustainable way over the next 4–5 months, without crash dieting. I currently weigh about 75 kg.",
            "wellness", true),
        PromptTemplate("career", "Career change",
            "Career: I want to move into a new role (for example, from support into software development) within a year. I have some basics but no portfolio yet.",
            "work", true),
        PromptTemplate("job", "Promotion / raise",
            "Career: I want to earn a promotion or a significantly better-paid job within 9 months. I need to strengthen my skills, build visible results and prepare for interviews.",
            "work", true),
        PromptTemplate("coding", "Learn programming",
            "Learning: I want to learn programming (for example Python or JavaScript) well enough to build small projects on my own in 6 months. I'm a beginner.",
            "code", true),
        PromptTemplate("language", "Learn a language",
            "Language: I want to hold a 10-minute everyday conversation in Spanish by next summer. I know a few words. I can practise 15–20 minutes on most days.",
            "language", true),
        PromptTemplate("exams", "Exams / studies",
            "Studies: I have exams in about 8 weeks and want to revise all subjects properly without cramming at the end.",
            "school", true),
        PromptTemplate("business", "Side business",
            "Business: I want to start a small side business with little upfront money and get my first paying customers within 6 months. I have an idea but haven't validated it.",
            "store", true),
        PromptTemplate("money", "Savings & money",
            "Money: I want to save a fixed amount every month toward a goal (for example an emergency fund of 3 months' expenses) and understand where my money goes.",
            "savings", true),
        PromptTemplate("sleep", "Sleep & wellbeing",
            "Wellbeing: I want to sleep 7–8 hours, go to bed at a consistent time, and feel less stressed during the week.",
            "sleep", true),
        PromptTemplate("mind", "Mindfulness",
            "Mindfulness: I want a short daily habit of meditation or journaling, starting small.",
            "mind", true),
        PromptTemplate("family", "Family & friends",
            "Relationships: I want to spend more intentional time with family and friends: a weekly call with my parents and seeing friends at least twice a month.",
            "family", true),
        PromptTemplate("home", "Home & admin",
            "Home: I want to keep my home tidy with small regular routines and stop putting off admin like bills and paperwork.",
            "home", true),
        PromptTemplate("creative", "Creative project",
            "Creative: I want to finish a creative project (for example write short stories, draw or play an instrument) by practising a few times a week.",
            "art", true),
        PromptTemplate("reading", "Reading",
            "Reading: I want to read 12 books this year, about 20 pages a day.",
            "book", true),
    )

    /** The user's templates if they customised them, else the defaults. */
    fun effective(custom: List<PromptTemplate>?): List<PromptTemplate> = custom ?: DEFAULTS

    /**
     * Adds or removes a template's paragraph in the description. Removing only happens when the
     * paragraph is still unedited; edited text is never deleted.
     */
    fun toggle(description: String, template: PromptTemplate, selected: Boolean): String {
        val paragraphs = description.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        return if (selected) {
            if (paragraphs.any { it == template.text.trim() }) description
            else (paragraphs + template.text.trim()).joinToString("\n\n")
        } else {
            paragraphs.filter { it != template.text.trim() }.joinToString("\n\n")
        }
    }

    fun newId(label: String, existing: List<PromptTemplate>): String {
        var id = "c-" + label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "area" }.take(20)
        while (existing.any { it.id == id }) id += "-2"
        return id
    }
}
