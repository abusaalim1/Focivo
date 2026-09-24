package com.example.util

import java.util.Locale

object OnDeviceStudyClassifier {

    data class ClassificationResult(
        val isCasual: Boolean,
        val confidence: Float,
        val reason: String
    )

    // Regex patterns for strong non-study / casual / entertainment queries
    private val EXPLICIT_NON_STUDY_REGEXES = listOf(
        // 18+ / Adult / NSFW / Sensual / Erotic queries
        Regex("""\b(18\+|porn|xxx|xvideos|nude|nudity|sex|erotic|sexy\s+video|hot\s+scene|kissing\s+scene|love\s+making|intimate\s+scene|ullu|altbalaji|hotshots|sensual|cleavage|bikini\s+photos?|hot\s+model|dirty\s+talk|bhabhi|lust|desire|erotica|strip|onlyfans)\b""", RegexOption.IGNORE_CASE),
        // Travel / vacation
        Regex("""\b(where\s+should\s+i\s+travel|beach\s+vacation|vacation\s+spot|vacation\s+destination|holiday\s+destination|travel\s+destination|pack\s+for\s+(a\s+)?vacation|trip\s+to|best\s+places\s+to\s+visit|flight\s+tickets?|resort|tourist\s+places?)\b""", RegexOption.IGNORE_CASE),
        // Horoscopes / Zodiac / Astrology
        Regex("""\b(horoscope|zodiac(\s+sign)?|astrology(\s+reading|\s+chart)?|birth\s+chart|tarot|kundali|rashi|future\s+prediction)\b""", RegexOption.IGNORE_CASE),
        // Food / Cooking / Recipes / Dining
        Regex("""\b(recipe\s+using|suggest\s+a\s+recipe|recipe\s+for|cook\s+for\s+dinner|what\s+should\s+i\s+cook|dinner\s+tonight|lunch\s+ideas|pizza|burger|biryani|maggi|pasta|momo|chowmein|restaurant|zomato|swiggy|what\s+to\s+eat|delicious\s+food)\b""", RegexOption.IGNORE_CASE),
        // Gifts / Birthdays / Parties / Celebrations
        Regex("""\b(birthday\s+gift|gift\s+ideas|surprise\s+party|plan\s+a\s+(surprise\s+)?party|birthday\s+message|happy\s+birthday|anniversary\s+wish|wedding\s+gift)\b""", RegexOption.IGNORE_CASE),
        // Fiction / Stories / Entertainment
        Regex("""\b(short\s+story|scary\s+story|tell\s+me\s+a\s+(scary\s+|horror\s+|funny\s+|bedtime\s+)?story|bedtime\s+story|fairy\s+tale|kahani\s+sunao|horror\s+story|ghost\s+story)\b""", RegexOption.IGNORE_CASE),
        // Flattery / Roasts / Boredom / Small Talk
        Regex("""\b(give\s+me\s+a\s+compliment|brighten\s+my\s+day|compliment\s+me|roast\s+me|can\s+you\s+roast|roast\s+my\s+friend|make\s+small\s+talk|small\s+talk|can\s+we\s+just\s+talk|i('m|m)\s+bored|bore\s+ho\s+raha|boredom|timepass|entertain\s+me|tell\s+me\s+something\s+interesting|kuch\s+mast\s+batao)\b""", RegexOption.IGNORE_CASE),
        // Roleplay / Persona for entertainment
        Regex("""\b(roleplay(\s+as)?|pretend\s+you\s+are|pretend\s+to\s+be|act\s+as\s+(a\s+)?(time-travel|tour\s+guide|superhero|pirate|detective)|act\s+like\s+a\s+(gangster|superhero)|you('re|re)\s+a\s+longtime\s+bookstore\s+owner)\b""", RegexOption.IGNORE_CASE),
        // Fitness / Workout / Health
        Regex("""\b(workout\s+routine|building\s+muscle|lose\s+weight|fat\s+loss|bicep|exercise\s+routine|gym\s+routine|six\s+pack)\b""", RegexOption.IGNORE_CASE),
        // News / Current Events / Celebrity
        Regex("""\b(happening\s+in\s+the\s+news|today('s)?\s+news|celebrity\s+gossip|drama\s+topic|what's\s+trending|trending\s+on\s+social\s+media|news\s+today)\b""", RegexOption.IGNORE_CASE),
        // Recommendations & Leisure queries
        Regex("""\b(recommend\s+me\s+books|what's\s+your\s+opinion\s+on|opinion\s+on\s+\[?celebrity|meaning\s+of\s+life)\b""", RegexOption.IGNORE_CASE),
        // Relationships / Dating / Romance / Shayari
        Regex("""\b(girlfriend|boyfriend|my\s+crush|crush\s+ko|poem\s+about\s+my\s+crush|pickup\s+lines?|first\s+date|conversation\s+starters\s+for\s+(a\s+)?(first\s+)?date|relationship\s+advice|breakup|propose\s+karne|love\s+letter|romantic\s+message|shayari|pyaar|ishq|mohabbat|flirt|impress\s+a\s+(girl|boy|crush)|dating\s+tips)\b""", RegexOption.IGNORE_CASE),
        // Jokes & Comedy & Memes
        Regex("""\b(tell\s+me\s+a\s+(funny\s+)?joke|tell\s+a\s+joke|funny\s+joke|knock\s+knock\s+joke|chutkula|joke\s+sunao|joke\s+batao|meme|memes|funny\s+memes?|make\s+me\s+laugh)\b""", RegexOption.IGNORE_CASE),
        // Weather
        Regex("""\b(weather\s+like\s+today|today('s)?\s+weather|weather\s+forecast\s+today|is\s+it\s+raining\s+outside|temperature\s+outside)\b""", RegexOption.IGNORE_CASE),
        // Movies / TV / Netflix / Anime / Web Series
        Regex("""\b(watch\s+on\s+netflix|netflix|what\s+should\s+i\s+watch|movie\s+recommendation|recommend\s+me\s+(a\s+good\s+)?(movie|anime|film|series)|anime\s+to\s+watch|anime|manga|naruto|dragon\s+ball|one\s+piece|demon\s+slayer|jujutsu\s+kaisen|attack\s+on\s+titan|marvel|avengers|batman|spiderman|iron\s+man|bollywood|hollywood|mirzapur|jawan|pushpa|kgf|kalki|box\s+office|trailer|teaser|web\s+series)\b""", RegexOption.IGNORE_CASE),
        // Music / Songs / Rap
        Regex("""\b(song\s+recommendation|suggest\s+(a\s+)?song|lyrics\s+of|write\s+a\s+(rap|song|lyrics)|rap\s+song|lofi\s+song|spotify\s+playlist|trending\s+songs?|singer|badshah|honey\s+singh|arijit|taylor\s+swift|kpop|bts)\b""", RegexOption.IGNORE_CASE),
        // Pets
        Regex("""\b(name\s+for\s+my\s+new\s+(puppy|dog|kitten|cat|pet)|puppy\s+name|kitten\s+name|cute\s+dog|cute\s+cat)\b""", RegexOption.IGNORE_CASE),
        // Social Media / Reels / Instagram / WhatsApp
        Regex("""\b(instagram\s+caption|caption\s+for\s+(my\s+)?(photo|post|pic|insta|reel)|bio\s+for\s+instagram|insta\s+bio|followers|reels?|tiktok|snapchat\s+streak|viral\s+video|trending\s+audio|status\s+for\s+whatsapp|whatsapp\s+status|dp\s+ideas)\b""", RegexOption.IGNORE_CASE),
        // Video Games & Esports
        Regex("""\b(video\s+game|game\s+should\s+i\s+play|game\s+to\s+play|pubg|bgmi|free\s+fire|gta|gta\s+5|gta\s+6|minecraft|fortnite|roblox|valorant|call\s+of\s+duty|cod\s+mobile|clash\s+of\s+clans|fifa\s+mobile|cheat\s+codes?|redeem\s+codes?|diamond\s+hack|headshot\s+settings?|gameplay)\b""", RegexOption.IGNORE_CASE),
        // Shopping / Gadgets / Phones / Cars / Bikes
        Regex("""\b(best\s+phone\s+under|which\s+mobile\s+to\s+buy|iphone\s+vs|smartphone\s+under|laptop\s+for\s+gaming|gaming\s+pc|unboxing|tech\s+review|buy\s+online|flipkart\s+sale|amazon\s+deal|supercar|fastest\s+car|best\s+bike|sneakers|shoes\s+to\s+buy|outfit\s+for)\b""", RegexOption.IGNORE_CASE),
        // Sports / Cricket / Football / IPL
        Regex("""\b(cricket|ipl|score\s+update|live\s+score|who\s+won\s+(the\s+)?match|virat\s+kohli|dhoni|rohit\s+sharma|hardik\s+pandya|football\s+match|messi|ronaldo|premier\s+league|champions\s+league|world\s+cup\s+winner|fifa\s+world\s+cup)\b""", RegexOption.IGNORE_CASE),
        // Hindi & Hinglish casual chat & greetings
        Regex("""\b(kya\s+kar\s+rahe\s+ho|kaun\s+ho\s+tum|tu\s+kaun\s+hai|kya\s+hal\s+hai|kaise\s+ho|kya\s+chal\s+raha\s+hai|aur\s+batao|bhai\s+sun|kuch\s+batao|bore\s+ho\s+raha|maja\s+nhi\s+aa\s+raha|chitchat|gup\s+shup|kaha\s+se\s+ho|tumhara\s+naam\s+kya\s+hai|chutkula|shayari|time\s+pass)\b""", RegexOption.IGNORE_CASE),
        // Direct non-study greetings & idle identity prompts to AI
        Regex("""\b(hi|hello|hey|sup|what'?s\s+up|good\s+morning|good\s+night|good\s+evening|who\s+are\s+you|what\s+is\s+your\s+name|how\s+are\s+you|what\s+can\s+you\s+do|are\s+you\s+real|are\s+you\s+human|do\s+you\s+have\s+feelings|can\s+you\s+love|tell\s+me\s+a\s+secret)\b""", RegexOption.IGNORE_CASE),
        // Crypto & Fast Money
        Regex("""\b(crypto|bitcoin|ethereum|trading\s+tips|earn\s+money\s+fast|make\s+money\s+online\s+fast|lottery|dream11|betting)\b""", RegexOption.IGNORE_CASE)
    )

    // Strong educational / academic keywords and concepts
    private val STRONG_ACADEMIC_KEYWORDS = setOf(
        // Math / Equations
        "equation", "equations", "calculus", "derivative", "derivatives", "integral", "integrals",
        "trigonometry", "algebra", "geometry", "triangle", "pythagorean", "pythagoras", "polynomial", "matrix",
        "matrices", "logarithm", "arithmetic", "probability", "statistics", "theorem", "proof", "derivation",
        "quadratic", "factorization", "fraction", "percentage", "ratio", "proportion", "mean", "median", "mode",
        "hypotenuse", "circle", "radius", "diameter", "perimeter", "volume", "surface area", "arithmetic progression",
        // Sciences (Physics, Chemistry, Biology)
        "physics", "chemistry", "biology", "mitosis", "meiosis", "photosynthesis", "dna", "rna",
        "cell", "cells", "cellular", "molecule", "molecular", "atom", "atomic", "electron", "proton",
        "neutron", "thermodynamics", "optics", "quantum", "relativity", "gravity", "gravitational",
        "newton", "newton's", "motion", "kinematics", "organic", "inorganic", "periodic", "element",
        "water cycle", "climate change", "renewable energy", "ecology", "ecosystem", "genetics",
        "evolution", "respiration", "photosynthetic", "acid", "acid base", "acids and bases", "alkali", "salt", "compound", "catalyst",
        "oxidation", "reduction", "lens", "mirror", "reflection", "refraction", "velocity", "acceleration",
        "force", "friction", "kinetic", "potential", "momentum", "voltage", "resistance", "current", "circuit",
        "magnetism", "induction", "frequency", "wavelength", "chlorophyll", "neuron", "enzyme", "hormone",
        // Humanities, History & Social Sciences
        "revolution", "french revolution", "world war", "wwii", "wwi", "history", "historical",
        "economics", "supply and demand", "microeconomics", "macroeconomics", "inflation", "gdp",
        "literature", "civics", "government", "constitution", "parliament", "treaty", "democracy",
        "fundamental rights", "judiciary", "federalism", "monsoon", "geography", "plateau", "peninsula",
        "latitude", "longitude", "rbi", "banking", "sovereign", "dynasty", "civilization",
        // Languages & Grammar
        "grammar", "vocabulary", "vocab", "irregular verbs", "conjugation", "verbs", "tense",
        "spanish", "french", "german", "latin", "japanese", "mandarin", "sanskrit", "hindi grammar",
        "sandhi", "samas", "alankar", "muhavare", "synonym", "antonym", "idiom", "active voice",
        "passive voice", "direct indirect", "preposition", "conjunction", "comprehension",
        // Computer Science / Programming
        "algorithm", "data structure", "machine learning", "neural network", "recursion",
        "syntax", "compiler", "complexity", "database", "sql", "python", "java", "kotlin", "c++",
        "javascript", "html", "css", "binary search", "array", "linked list", "stack", "queue",
        "binary tree", "sorting", "oop", "inheritance", "polymorphism", "encapsulation",
        // Educational Tasks, Exams & Contexts
        "10th grade", "7th grade", "middle school", "high school", "college", "university",
        "student", "teacher", "lesson plan", "curriculum", "syllabus", "textbook", "chapter",
        "homework", "assignment", "exam", "quiz", "quiz me", "flashcards", "practice questions",
        "multiple-choice", "sample paper", "mock test", "thesis statement", "research paper",
        "essay", "proofread", "cover letter", "job interview", "mnemonic", "analogies",
        "step by step", "science project", "ncert", "cbse", "icse", "jee", "neet", "upsc",
        "gate", "cat", "clat", "nda", "pyq", "revision notes"
    )

    // Generic non-study keywords indicating entertainment, sports, romance, gaming, leisure
    private val CASUAL_DISTRACTION_KEYWORDS = setOf(
        "movie", "movies", "film", "films", "cinema", "actor", "actress", "hero", "heroine",
        "netflix", "prime video", "hotstar", "anime", "manga", "naruto", "one piece", "dragon ball",
        "demon slayer", "jujutsu kaisen", "marvel", "dc", "avengers", "batman", "spiderman", "iron man",
        "song", "songs", "lyrics", "sing", "singer", "album", "music", "rap", "rapper", "spotify",
        "game", "games", "gaming", "gamer", "pubg", "bgmi", "free fire", "gta", "minecraft", "roblox",
        "fortnite", "valorant", "cod", "fifa", "clash of clans", "gameplay", "aimbot", "headshot",
        "cricket", "ipl", "football", "soccer", "messi", "ronaldo", "match", "wicket", "sixes", "batsman",
        "bowler", "world cup", "tournament", "champions league", "premier league",
        "girlfriend", "boyfriend", "crush", "dating", "date", "flirt", "propose", "breakup", "love",
        "romantic", "shayari", "pyaar", "ishq", "mohabbat", "marriage", "shaadi", "wedding",
        "joke", "jokes", "funny", "meme", "memes", "roast", "prank", "comedy", "chutkula",
        "instagram", "insta", "reels", "reel", "tiktok", "snapchat", "streak", "followers", "likes", "bio",
        "whatsapp status", "status", "dp",
        "recipe", "cook", "cooking", "pizza", "burger", "biryani", "maggi", "pasta", "momo", "chowmein",
        "restaurant", "zomato", "swiggy", "snack", "dinner", "lunch", "breakfast", "delicious",
        "travel", "trip", "holiday", "vacation", "resort", "beach", "hotel", "flight", "tourist",
        "buy", "price", "shopping", "flipkart", "amazon", "iphone", "smartphone", "supercar", "bike",
        "sneakers", "shoes", "outfit", "perfume", "fashion",
        "crypto", "bitcoin", "ethereum", "trading", "lottery", "betting", "dream11",
        "horoscope", "astrology", "zodiac", "tarot", "kundali", "rashi"
    )

    // Strong instructional educational stems
    private val EDUCATIONAL_STEMS = listOf(
        Regex("""\bexplain\s+.+\s+(in\s+simple|step\s+by\s+step|like\s+i('m|m)\s+in)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsolve\s+this\s+(equation|problem|math)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bquiz\s+me\s+on\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcreate\s+a\s+quiz\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsummarize\s+.+\s+(in\s+a\s+table|causes\s+vs\s+effects|chapter)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(comparison\s+table|differences?\s+between\s+.+\s+and)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(analogies\s+to\s+help\s+me\s+remember|mnemonic\s+to\s+remember)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(edit\s+this\s+paragraph|check\s+my\s+essay|proofread)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(act\s+as\s+my\s+.+\s+teacher|practice\s+speaking\s+(french|spanish|german|sanskrit))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(design\s+a\s+lesson\s+plan|outline\s+an\s+essay|structure\s+my\s+research\s+paper)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(what\s+are\s+the\s+causes\s+of|formula\s+for\s+the\s+area|thesis\s+statement)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(help\s+me\s+understand|help\s+me\s+solve|help\s+me\s+outline)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(flashcards\s+for|practice\s+questions\s+on)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(what\s+is\s+the\s+capital\s+of|capital\s+city\s+of)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(define\s+[a-zA-Z]+|definition\s+of\s+[a-zA-Z]+|meaning\s+of\s+(the\s+word|the\s+term|[a-zA-Z]+\s+in\s+))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(how\s+to\s+implement|how\s+to\s+write\s+code\s+for|how\s+to\s+solve)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(who\s+discovered|who\s+invented|when\s+did\s+.+\s+(start|end|occur|happen))\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * Evaluates a user message or screen content.
     * Returns isCasual = true for non-study queries (games, sports, entertainment, casual chat, etc.).
     * Returns isCasual = false for academic, educational, or homework-related queries.
     */
    fun evaluateContent(text: String): ClassificationResult {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return ClassificationResult(isCasual = false, confidence = 0f, reason = "Empty content")
        }

        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Check YouTube Shorts directly
        if (lower.contains("shorts") && (lower.contains("#shorts") || lower.contains("/shorts/") || lower == "shorts")) {
            return ClassificationResult(
                isCasual = true,
                confidence = 1.0f,
                reason = "YouTube Shorts detected during active study"
            )
        }

        // 2. Check for explicit educational stems (Highest priority: overrides ambiguous wording)
        for (stem in EDUCATIONAL_STEMS) {
            if (stem.containsMatchIn(trimmed)) {
                return ClassificationResult(
                    isCasual = false,
                    confidence = 0.95f,
                    reason = "Educational instruction pattern matched: ${stem.pattern}"
                )
            }
        }

        // 3. Check for strong academic keywords
        var academicScore = 0
        val matchedAcademic = mutableListOf<String>()
        for (kw in STRONG_ACADEMIC_KEYWORDS) {
            val matches = if (kw.contains(" ")) {
                lower.contains(kw)
            } else {
                Regex("""\b${Regex.escape(kw)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
            }
            if (matches) {
                academicScore += if (kw.contains(" ")) 2 else 1
                matchedAcademic.add(kw)
            }
        }

        // Check math equations and coding formats
        val hasEquation = Regex("""\d+\s*[\(\+\-\*\/]\s*\d+.*=|[a-z]\s*[\+\-\*\/]\s*\d+\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
        if (hasEquation) {
            academicScore += 3
            matchedAcademic.add("equation_format")
        }

        // 4. Check for explicit Non-Study / Casual regexes
        val matchedNonStudy = mutableListOf<String>()
        for (reg in EXPLICIT_NON_STUDY_REGEXES) {
            val match = reg.find(trimmed)
            if (match != null) {
                matchedNonStudy.add(match.value)
            }
        }

        // Check for casual distraction keywords
        val matchedCasualKeywords = mutableListOf<String>()
        for (kw in CASUAL_DISTRACTION_KEYWORDS) {
            val matches = if (kw.contains(" ")) {
                lower.contains(kw)
            } else {
                Regex("""\b${Regex.escape(kw)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
            }
            if (matches) {
                matchedCasualKeywords.add(kw)
            }
        }

        // 5. Evaluate classification
        if (matchedNonStudy.isNotEmpty()) {
            // If academic score is significantly higher (e.g. teaching context), give study priority
            if (academicScore >= 3 && matchedAcademic.any { it.contains("teacher") || it.contains("quiz") || it.contains("exam") }) {
                return ClassificationResult(
                    isCasual = false,
                    confidence = 0.85f,
                    reason = "Study content (educational context overrides casual phrasing)"
                )
            }

            val reasonDesc = matchedNonStudy.distinct().take(2).joinToString(", ")
            return ClassificationResult(
                isCasual = true,
                confidence = 0.95f,
                reason = "Non-study topic detected: $reasonDesc"
            )
        }

        if (matchedCasualKeywords.isNotEmpty() && academicScore == 0) {
            val reasonDesc = matchedCasualKeywords.distinct().take(2).joinToString(", ")
            return ClassificationResult(
                isCasual = true,
                confidence = 0.90f,
                reason = "Casual distraction topic detected: $reasonDesc"
            )
        }

        if (academicScore > 0) {
            return ClassificationResult(
                isCasual = false,
                confidence = 0.90f,
                reason = "Study topic detected: ${matchedAcademic.distinct().take(3).joinToString(", ")}"
            )
        }

        // 6. Conversational / non-academic queries with no academic concepts:
        // When a student is using ChatGPT with AI Study Guard, pure conversational small talk
        // or leisure prompts without any educational/academic context are non-study distractions.
        val isShortConversational = trimmed.length < 80 && (
            lower.contains("hi") || lower.contains("hello") || lower.contains("hey") ||
            lower.contains("kaise") || lower.contains("kya") || lower.contains("sun") ||
            lower.contains("batao") || lower.contains("who") || lower.contains("how") ||
            lower.contains("tell me") || lower.contains("write") || lower.contains("can you")
        )
        if (isShortConversational) {
            return ClassificationResult(
                isCasual = true,
                confidence = 0.80f,
                reason = "Non-academic casual query: '${trimmed.take(40)}'"
            )
        }

        return ClassificationResult(
            isCasual = false,
            confidence = 0.5f,
            reason = "General learning topic (benefit of doubt)"
        )
    }
}
