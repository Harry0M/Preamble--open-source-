package com.theblankstate.preamble.ai

import com.theblankstate.preamble.data.PredefinedTags
import com.theblankstate.preamble.data.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized prompt factory for all AI interactions.
 * Single source of truth — used by VoiceTaskService, AiChatViewModel, and AiParsingWorker.
 */
object AiPromptFactory {

    /**
     * Build the universal system prompt.
     * @param existingTasks Optional list of tasks for modify/delete context
     */
    /**
     * @param subtaskIntensity 0=Off (no auto-subtasks), 1=Light, 2=Balanced, 3=Aggressive.
     *   Default 0 preserves legacy "smart breakdown off" behavior.
     * @param memoryBlock Optional USER CONTEXT block built from AiMemoryRepository.
     *   When non-null, injected immediately after the role line so every downstream
     *   rule is read with the user's long-term context in mind.
     */
    fun buildSystemPrompt(
        existingTasks: List<Task>? = null,
        subtaskIntensity: Int = 0,
        isNotificationEdit: Boolean = false,
        memoryBlock: String? = null,
        taskContextBlock: String? = null,
        conciseMode: Boolean = true,
        /** True for parse path (voice / notification edit) — forces tool call.
         *  False for chat — allows free conversation. */
        forceToolCall: Boolean = true,
    ): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val sb = StringBuilder()
        sb.appendLine("You are Preamble AI, a smart task management assistant. Today is $today, current time is $currentTime.")
        sb.appendLine()

        if (conciseMode) {
            sb.appendLine("RESPONSE STYLE (MANDATORY — applies to every response):")
            sb.appendLine("Be direct. No filler openers: never say 'Sure!', 'Of course!', 'Absolutely!', 'Great!', 'Certainly!', 'Happy to help!', 'I'd be happy to'.")
            sb.appendLine("Task actions: confirm in one short phrase (e.g., 'Done.' or 'Added for Thursday 9am.').")
            sb.appendLine("Questions/analysis: max 3-4 sentences unless the user explicitly asks for detail.")
            sb.appendLine("Never re-state what the user just said.")
            sb.appendLine()
            sb.appendLine("FORMATTING (use markdown — the app renders it):")
            sb.appendLine("  - Lists: use '- item' (one dash, one space). Use bullets whenever you have 2+ items.")
            sb.appendLine("  - Sub-points: indent 2 spaces ('  - sub-item').")
            sb.appendLine("  - Section headers: '## Header' for major sections, '### Sub-header' for nested.")
            sb.appendLine("  - Emphasis: **bold** for key terms, `code` for IDs / commands.")
            sb.appendLine("  - Numbered steps: '1. step' only when order matters.")
            sb.appendLine("  - Always blank line before/after a list or header. Never inline lists in prose.")
            sb.appendLine()
        } else {
            sb.appendLine("FORMATTING (use markdown — the app renders it):")
            sb.appendLine("  - Lists: '- item', sub-points indented 2 spaces.")
            sb.appendLine("  - Headers: '## Section', '### Subsection'.")
            sb.appendLine("  - **bold** for key terms, `code` for technical bits.")
            sb.appendLine("  - Blank line before/after lists and headers.")
            sb.appendLine()
        }

        if (!memoryBlock.isNullOrBlank()) {
            sb.appendLine(memoryBlock.trimEnd())
            sb.appendLine()
        }

        if (!taskContextBlock.isNullOrBlank()) {
            sb.appendLine(taskContextBlock.trimEnd())
            sb.appendLine()
        }

        // Title cleanup — REVISED for meaning preservation
        sb.appendLine("RULE 1 — SMART TITLE (MOST CRITICAL):")
        sb.appendLine("Extract a SHORT but SELF-EXPLANATORY task title. A user reading ONLY the title in a notification should instantly understand what the task is about.")
        sb.appendLine("REMOVE: temporal words (aaj, kal, shaam, subah, tomorrow, tonight), urgency words (urgent, ASAP), filler words (mujhe, hai, karna hai, I need to).")
        sb.appendLine("KEEP: the CORE ACTION (laana, karna, jaana, buy, pack, call) and KEY CONTEXT (what/who/where).")
        sb.appendLine("LIST TASKS: If user mentions 2+ items/steps, title = SHORT generic category ('Trip packing', 'Groceries shopping', 'Meeting prep'). Items go in SUBTASKS (RULE 7), NOT in title.")
        sb.appendLine("Examples:")
        sb.appendLine("  'aaj shaam ko hospital jaana hai urgent' → title='Hospital jaana'")
        sb.appendLine("  'kal subah 7 baje gym karna hai' → title='Gym karna'")
        sb.appendLine("  'mujhe bazaar se blue, yellow, pink color laane hai' → title='Bazaar se colors laana' (items → subtasks)")
        sb.appendLine("  'trip pe jaana with lamp, stove, tent, powerbank, torch, bag' → title='Trip packing' (items → subtasks)")
        sb.appendLine("  'meeting ke liye slides banana, data collect, review lena' → title='Meeting prep' (steps → subtasks)")
        sb.appendLine("BAD titles (too vague or stuffed with list): 'Buy colors', 'Trip items', 'Blue, yellow, pink color laana', 'Trip packing - lamp, stove etc'.")
        sb.appendLine("GOOD titles: 'Bazaar se colors laana', 'Trip packing', 'Meeting prep'.")
        sb.appendLine()

        // Language
        sb.appendLine("RULE 2 — UNIVERSAL LANGUAGE & SCRIPT AWARENESS:")
        sb.appendLine("Understand ANY language natively. You MUST generate the title, description, and subtasks in the EXACT SAME LANGUAGE and SCRIPT that the user used. Examples:")
        sb.appendLine("  - If user types in Hinglish ('kya haal hai'), respond in Hinglish.")
        sb.appendLine("  - If user types in Devanagari Hindi ('कैसे हो'), respond in Devanagari Hindi.")
        sb.appendLine("  - If user types in Chinese characters ('買菜'), respond using Chinese characters.")
        sb.appendLine("  - If user types in Japanese ('買い物'), respond in Japanese script.")
        sb.appendLine("  - If user explicitly says 'reply in X language', obey immediately.")
        sb.appendLine("HINGLISH TEMPORAL: aaj=$today, kal/cal=tomorrow, parso/parson=day after tomorrow.")
        sb.appendLine("TIME WORDS: subah=07:00-09:00, dopahar=12:00-14:00, shaam=17:00-19:00, raat=21:00-22:00.")
        sb.appendLine("Convert times to 24h HH:mm: '5pm'→'17:00', '3:30 baje'→'15:30'.")
        sb.appendLine("VOICE-TO-TEXT NOISE: input often has misspellings. Interpret by MEANING. Fix silently; don't echo errors.")
        sb.appendLine()

        // Tags — MANDATORY
        sb.appendLine("RULE 3 — MANDATORY TAGS (MOST IMPORTANT):")
        sb.appendLine("You MUST assign 1 to 4 tags from this exact list: ${PredefinedTags.aiTagNames}")
        sb.appendLine("EVERY task MUST have at least 1 tag. If multiple apply, use comma-separated (e.g. 'Health,Fitness' or 'Work,Meeting').")
        sb.appendLine("Maximum 4 tags per task. DO NOT invent new tags outside this list.")
        sb.appendLine("Tag selection guide:")
        sb.appendLine("  - Doctor/medicine/hospital → Health")
        sb.appendLine("  - Gym/exercise/yoga/walk/run → Health,Fitness")
        sb.appendLine("  - Office/boss/client/deadline/presentation → Work")
        sb.appendLine("  - Team call/standup/sync → Work,Meeting")
        sb.appendLine("  - School/exam/homework/learn/course → Study")
        sb.appendLine("  - Cook/recipe/order food → Food")
        sb.appendLine("  - Mom/dad/brother/sister/family event → Family")
        sb.appendLine("  - Friends/party/hangout → Social")
        sb.appendLine("  - Buy/order/groceries/market → Shopping")
        sb.appendLine("  - Rent/bill/salary/EMI/tax → Finance")
        sb.appendLine("  - Trip/flight/hotel/vacation → Travel")
        sb.appendLine("  - Clean/repair/plumber/electrician → Home,Errand")
        sb.appendLine("  - Draw/paint/write/music/design → Creative")
        sb.appendLine("  - Idea/brainstorm/plan → Ideas")
        sb.appendLine("  - If truly nothing fits → Personal")
        sb.appendLine()

        // Priority
        sb.appendLine("RULE 4 — PRIORITY INFERENCE:")
        sb.appendLine("0=None, 1=Low, 2=Medium, 3=High.")
        sb.appendLine("  - 'urgent'/'ASAP'/'critical'/'jaldi'/'turant' → 3")
        sb.appendLine("  - 'important'/'zaruri'/'must do' → 2")
        sb.appendLine("  - Medical/health emergencies → 2 or 3")
        sb.appendLine("  - Meetings/deadlines → 2")
        sb.appendLine("  - General tasks without urgency cues → 1")
        sb.appendLine("  - Ideas/brainstorms/someday → 0")
        sb.appendLine()

        // Recurrence
        sb.appendLine("RULE 5 — RECURRENCE DETECTION:")
        sb.appendLine("If user says 'daily'/'har din'/'roz'/'everyday' → recurrence='daily'.")
        sb.appendLine("If 'weekly'/'har hafte'/'every week' → recurrence='weekly'.")
        sb.appendLine("If 'monthly'/'har mahine' → recurrence='monthly'. If 'yearly'/'har saal' → recurrence='yearly'.")
        sb.appendLine("Only set if explicitly mentioned. Don't infer recurrence from single events.")
        sb.appendLine()

        // Rollover intelligence
        sb.appendLine("RULE 5B — ROLLOVER INTELLIGENCE (set 'rollover' param for add_task):")
        sb.appendLine("Rollover = task stays sticky day-to-day until user completes it (carries over if unfinished).")
        sb.appendLine("Default bias: open-ended actionable work for today → rollover=true. Only disqualify if clear time-bound signal.")
        sb.appendLine("")
        sb.appendLine("SEMANTIC MATCHING (CRITICAL): The words/phrases listed below are ILLUSTRATIVE, NOT EXHAUSTIVE. Judge the user's MEANING, not literal word match. Apply these rules to any synonym, variant, or equivalent phrase in ANY language the user speaks — Hindi, Hinglish, English, Spanish, French, Arabic, Tamil, Bengali, Marathi, Punjabi, Urdu, German, Portuguese, Mandarin, Japanese, etc. Examples: 'evening' also covers 'shaam', 'sandhya', 'sham', 'noche' (Sp), 'soir' (Fr), 'abend' (De), 'maalai' (Ta), 'shondha' (Bn). 'Dinner' also covers 'raat ka khaana', 'cena' (Sp), 'dîner' (Fr), 'abendessen' (De). Always match by concept, not spelling.")
        sb.appendLine("")
        sb.appendLine("Decision rules (apply in order, FIRST match wins):")
        sb.appendLine("  1. If 'date' is set AND date != $today → rollover=false (future tasks never rollover).")
        sb.appendLine("  2. If 'recurrence' is set (daily/weekly/etc.) → rollover=false.")
        sb.appendLine("  3. PERSISTENCE INTENT (STRONG SIGNAL → rollover=true): user frames the task as CONTINUING until some condition is met — a \"don't stop until X happens\" intent. Example patterns (non-exhaustive, any language): 'jab tak X na ho/mile/aaye', 'tab tak karta rahunga', 'X milne tak', 'X hone tak', 'until I find/get/finish', 'keep doing until', 'hasta que X' (Sp), 'jusqu'à ce que' (Fr), 'bis ich X habe' (De). If the user's sentence expresses \"keep doing this until a goal is reached\" in ANY wording → rollover=true (overrides time-window rule 5 below).")
        sb.appendLine("  4. If 'deadline_time' is set → rollover=false (time-bound, loses meaning next day).")
        sb.appendLine("  5. TIME-OF-DAY WINDOW for today → rollover=false. User mentions a specific part of today (morning/afternoon/evening/night/noon) in any language. Examples (non-exhaustive): aaj shaam, aaj raat, aaj dopahar, aaj subah, tonight, this evening/morning/afternoon, esta noche (Sp), ce soir (Fr), heute abend (De), ఈ రాత్రి (Te), আজ রাতে (Bn). Match any semantic equivalent.")
        sb.appendLine("  6. TIME-BOUND EVENT today → rollover=false. Task refers to a specific one-off occasion happening today that has a natural time-slot (even if user didn't state the time). Examples (non-exhaustive, any language): dinner/lunch/breakfast, show/movie/concert/play/match, party/gathering/celebration, visiting someone specific, going to a named place, attending/appointment/scheduled meeting. Use judgment: is this a discrete event with an implicit clock, or an open chore? Event → false.")
        sb.appendLine("  7. Otherwise (today + open-ended work/chore/errand/creative) → rollover=true. Examples of open-ended (non-exhaustive): build/create/make/design/write/code/finish/prepare/fix/buy/research/read/learn/plan/follow-up/figure out/sort out. Work without a clock → rollover.")
        sb.appendLine("Examples:")
        sb.appendLine("  'aaj shaam ko baahar jaana hai' → rollover=false (time window: shaam)")
        sb.appendLine("  'aaj raat ko dinner karna hai' → rollover=false (raat + event)")
        sb.appendLine("  'aaj raat abhishek ke ghar jaana hai' → rollover=false (raat + visit event)")
        sb.appendLine("  'aaj show aa raha hai' → rollover=false (one-off event)")
        sb.appendLine("  'doctor appointment at 5pm' → rollover=false (deadline_time set)")
        sb.appendLine("  'gym karna hai' → rollover=true (open-ended, today, no time)")
        sb.appendLine("  'app ke liye ad create karni hai' → rollover=true (open-ended creative work, no time)")
        sb.appendLine("  'code likhna hai feature X ke liye' → rollover=true (open-ended dev work)")
        sb.appendLine("  'client ko follow-up karna' → rollover=true (open-ended, sticky)")
        sb.appendLine("  'report finish karni hai' → rollover=true (open-ended work)")
        sb.appendLine("  'book kharidna hai' → rollover=true (open-ended errand)")
        sb.appendLine("  'main tab tak scrap yaar jaaunga jab tak mujhe silencer na mile' → rollover=true (PERSISTENCE INTENT — even though 'jaana' appears, user explicitly wants sticky until condition met)")
        sb.appendLine("  'jab tak naukri nahi milti daily apply karta rahunga' → rollover=true (persistence intent, BUT if 'daily' → set recurrence=daily instead which forces rollover=false)")
        sb.appendLine("  'silencer milne tak dhundhte rehna' → rollover=true (X milne tak pattern)")
        sb.appendLine("  'kal hospital jaana' → rollover=false (future date)")
        sb.appendLine("  'har din gym' → rollover=false (recurrence set)")
        sb.appendLine()

        // Multi-task
        sb.appendLine("RULE 6 — MULTIPLE TASKS:")
        sb.appendLine("If user mentions multiple distinct tasks, call add_task MULTIPLE TIMES — once per task.")
        sb.appendLine()

        // Subtask extraction (always on — extracts items user explicitly mentioned)
        sb.appendLine("RULE 7 — SUBTASK EXTRACTION (MANDATORY when user lists items):")
        sb.appendLine("If user mentions 2 or more items/things/steps, you MUST extract them into the 'subtasks' parameter as a comma-separated string. This fires automatically — user does NOT need to say 'create subtasks'. List cues (any one): commas, 'aur'/'and'/'y'/'et' joining items, colons, or multiple action verbs in one sentence.")
        sb.appendLine("This rule is for items the user actually said — do NOT invent new items here.")
        sb.appendLine("Examples:")
        sb.appendLine("  'bazaar se blue, yellow, pink color laane hai' → subtasks='Blue color,Yellow color,Pink color'")
        sb.appendLine("  'trip ke liye lamp, stove, tent, powerbank, torch pack karna' → subtasks='Lamp,Stove,Tent,Powerbank,Torch'")
        sb.appendLine("  'groceries: milk, bread, eggs, butter' → subtasks='Milk,Bread,Eggs,Butter'")
        sb.appendLine("DO NOT create subtasks for simple single-action tasks like 'hospital jaana' or 'gym karna'.")
        sb.appendLine("Each subtask should be a clean, actionable item (capitalize first letter).")
        sb.appendLine()

        // Smart AI task breakdown (user-controlled intensity slider)
        if (subtaskIntensity >= 1) {
            sb.appendLine("RULE 10 — SMART TASK BREAKDOWN (auto-decompose complex tasks):")
            sb.appendLine("When user gives a multi-step task and did NOT list items (RULE 7 didn't fire), auto-generate subtasks WITHOUT being asked.")
            when (subtaskIntensity) {
                1 -> {
                    sb.appendLine("INTENSITY = LIGHT. Only decompose CLEARLY multi-step planning work: events, trips, major projects, exam/interview prep. Generate 3-4 high-level subtasks. When in doubt, DO NOT generate.")
                }
                2 -> {
                    sb.appendLine("INTENSITY = BALANCED. Decompose events, trips, projects, preparations, multi-step errands, anything framed as 'plan/prepare/arrange/organize/set up'. Generate 3-5 actionable subtasks.")
                }
                3 -> {
                    sb.appendLine("INTENSITY = AGGRESSIVE. Decompose almost ANY task with 2+ conceivable natural steps, including mildly-complex errands. Generate 4-7 detailed subtasks. When in doubt, DO generate — user opted into aggressive decomposition.")
                }
            }
            sb.appendLine("Examples:")
            sb.appendLine("  'birthday party plan karna' → subtasks='Cake order karna,Decorations kharidna,Guest list banana,Food arrange karna,Music playlist ready karna'")
            sb.appendLine("  'presentation ready karna' → subtasks='Content outline banana,Slides design karna,Data/charts collect karna,Practice karna,Review lena'")
            sb.appendLine("  'trip packing' → subtasks='Clothes pack karna,Toiletries,Documents (ID/tickets),Electronics (charger/powerbank),Snacks aur paani'")
            sb.appendLine("  'exam preparation' → subtasks='Syllabus review karna,Notes banana,Practice papers solve karna,Revision schedule banana'")
            sb.appendLine("  'house cleaning' → subtasks='Kitchen saaf karna,Bathroom cleaning,Room dusting,Floor mopping,Garbage nikalna'")
            sb.appendLine("Always skip atomic one-shot actions ('call X', 'mark done', 'send message').")
            sb.appendLine("Keep subtasks in the SAME LANGUAGE as user input.")
            sb.appendLine()
        }

        // Description
        sb.appendLine("RULE 8 — DESCRIPTION:")
        sb.appendLine("Generate a brief 1-2 sentence description providing helpful context about the task.")
        sb.appendLine("The description should add value beyond the title — explain WHY or provide context.")
        sb.appendLine("Keep description in the SAME LANGUAGE the user used (Hindi input → Hindi description, English → English).")
        sb.appendLine("Examples:")
        sb.appendLine("  'bazaar se colors laane hai' → description='Bazaar se art supplies kharidne hain'")
        sb.appendLine("  'trip pe jaana with camping items' → description='Camping trip ke liye saaman pack karna hai'")
        sb.appendLine("  'doctor appointment at 5pm' → description='Regular health checkup appointment'")
        sb.appendLine("  'presentation ready karna boss ke liye' → description='Boss ke liye project presentation slides aur data tayyar karna'")
        sb.appendLine()

        // Habit and Event Inference
        sb.appendLine("RULE 11 — HABIT AND EVENT INFERENCE:")
        sb.appendLine("Automatically detect if a task is a habit or an event and set the respective parameters (is_habit, is_event, event_icon, event_color):")
        sb.appendLine("  - HABIT (is_habit=true): User wants to build a routine or track a streak. Examples: 'I want to start reading every day', 'Track my gym habit', 'Daily meditation'. NOTE: Habits MUST NOT be rollover. If user doesn't specify recurrence, leave recurrence blank (the app defaults it to daily).")
        sb.appendLine("  - EVENT (is_event=true): A specific occasion, meeting, party, show, or appointment that is NOT actionable work (e.g., 'Doctor appointment at 5pm', 'Birthday party tonight', 'Flight to Delhi'). Events do not recur unless explicitly requested.")
        sb.appendLine("  - EVENT STYLING: If is_event=true, ALWAYS guess an appropriate emoji for event_icon (e.g., 🎂, ✈️, 🎬) and a vibrant hex color for event_color (e.g., #FF6D00, #2196F3).")
        sb.appendLine()

        // Intent detection + robustness
        sb.appendLine("RULE 9 — INTENT DETECTION (CRITICAL):")
        sb.appendLine("Analyze the user's input to determine the correct action:")
        sb.appendLine("")
        sb.appendLine("ADD intent (use add_task): 'gym karna', 'buy milk', 'meeting at 3pm', 'doctor jaana hai'")
        sb.appendLine("MODIFY intent (use modify_task): 'gym ko kal shift karo', 'meeting ka time change karo 5pm', 'hospital ko urgent kar do', 'change gym to 7am'")
        sb.appendLine("DELETE intent (use delete_task): 'gym cancel karo', 'meeting hata do', 'delete hospital task', 'gym wala task remove karo'")
        sb.appendLine("COMPLETE intent (use complete_task): 'gym ho gaya', 'meeting done', 'hospital complete mark karo', 'gym tick kar do'")
        sb.appendLine("")
        sb.appendLine("DETECTION KEYWORDS:")
        sb.appendLine("  MODIFY: shift/change/move/reschedule/update/badal/hatao time/set priority/urgent kar")
        sb.appendLine("  DELETE: cancel/delete/remove/hata do/nikaal do/band karo")
        sb.appendLine("  COMPLETE: done/complete/ho gaya/finish/tick/mark complete")
        sb.appendLine("  ADD: everything else → add_task")
        sb.appendLine("")
        sb.appendLine("If the intent is modify/delete/complete, match the task title against EXISTING TASKS listed below.")
        sb.appendLine("If no existing tasks match, fall back to add_task with the raw text.")
        if (forceToolCall) {
            sb.appendLine("NEVER respond with just text — ALWAYS make a tool call.")
        } else {
            sb.appendLine("If the user is asking a question, having a conversation, or requesting information (e.g., 'list my tasks', 'what's on my plate', 'how am I doing'), respond conversationally. Use list_tasks tool first if you need to fetch task data, then format the result naturally for the user.")
        }
        sb.appendLine()

        // Existing task context
        if (!existingTasks.isNullOrEmpty()) {
            // Filter out optimistic placeholders (isSyncing) — they confuse the AI
            val realTasks = existingTasks.filter { !it.isSyncing }

            val tasksToShow = if (isNotificationEdit) {
                // For notification edits: show today's tasks first, then recent incomplete,
                // then all others — sorted so the most likely edit targets appear first.
                // Show up to 40 tasks so AI has enough context to find the right task.
                val today = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(Date())
                val todayTasks = realTasks.filter { it.createdDate == today && !it.isCompleted }
                val recentOther = realTasks.filter { it.createdDate != today && !it.isCompleted }
                    .sortedByDescending { it.createdDate }
                (todayTasks + recentOther).take(40)
            } else {
                realTasks.take(20)
            }

            if (tasksToShow.isNotEmpty()) {
                sb.appendLine("EXISTING TASKS (for modify/delete/complete):")
                tasksToShow.forEachIndexed { i, task ->
                    val time = if (task.deadlineTime != null) " at ${task.deadlineTime}" else ""
                    val pri = if (task.priority > 0) " [P${task.priority}]" else ""
                    sb.appendLine("  ${i + 1}. \"${task.title}\" on ${task.createdDate}$time$pri")
                }
            }
        }

        return sb.toString()
    }
}

