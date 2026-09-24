package com.example.util

import java.util.Locale

/**
 * Maintainable database of recognized Indian and international educational channels,
 * platforms, creators, and institutions across competitive exams, STEM, school boards,
 * coding, skill-building, and academic lectures.
 */
object TrustedEducationalChannels {

    enum class ChannelCategory(val label: String) {
        COMPETITIVE_EXAMS("Competitive Exams (JEE/NEET/UPSC/SSC)"),
        CODING_AND_TECH("Programming & Computer Science"),
        K12_AND_BOARDS("K-12 & School Boards (CBSE/ICSE)"),
        HIGHER_ED_AND_STEM("Higher Education & STEM"),
        LANGUAGE_AND_SKILLS("Language & Professional Skills")
    }

    data class TrustedChannelInfo(
        val name: String,
        val handle: String? = null,
        val category: ChannelCategory,
        val aliases: List<String> = emptyList()
    )

    private val TRUSTED_CHANNELS: List<TrustedChannelInfo> = listOf(
        // === COMPETITIVE EXAMS (JEE / NEET / UPSC / SSC / BANKING) ===
        TrustedChannelInfo("Physics Wallah - Alakh Pandey", "@physicswallah", ChannelCategory.COMPETITIVE_EXAMS, listOf("physics wallah", "alakh pandey", "pw", "competition wallah", "jee wallah", "neet wallah", "pw foundation", "pw vidyapeeth")),
        TrustedChannelInfo("Unacademy", "@unacademy", ChannelCategory.COMPETITIVE_EXAMS, listOf("unacademy jee", "unacademy neet", "unacademy upsc", "unacademy ssc", "unacademy 11&12", "unacademy atoms", "unacademy olympiad")),
        TrustedChannelInfo("Vedantu", "@vedantu", ChannelCategory.COMPETITIVE_EXAMS, listOf("vedantu jee", "vedantu neet", "vedantu 9 and 10", "vedantu 11 and 12", "vedantu math", "vedantu master teacher")),
        TrustedChannelInfo("Khan Academy India", "@khanacademyindia", ChannelCategory.COMPETITIVE_EXAMS, listOf("khan academy", "khan academy hindi", "sal khan")),
        TrustedChannelInfo("Allen Career Institute", "@allen_career_institute", ChannelCategory.COMPETITIVE_EXAMS, listOf("allen career institute", "allen jee", "allen neet", "allen kota")),
        TrustedChannelInfo("Drishti IAS", "@drishtiias", ChannelCategory.COMPETITIVE_EXAMS, listOf("drishti ias", "vikas divyakirti", "drishti ias english", "drishti ras", "drishti judiciary")),
        TrustedChannelInfo("Next IAS", "@nextias", ChannelCategory.COMPETITIVE_EXAMS, listOf("next ias", "made easy", "next ias daily current affairs", "b. singh")),
        TrustedChannelInfo("StudyIQ IAS", "@studyiqias", ChannelCategory.COMPETITIVE_EXAMS, listOf("studyiq", "study iq ias", "study iq education", "prashant dhawan", "world affairs")),
        TrustedChannelInfo("Vajiram and Ravi", "@vajiramandravi", ChannelCategory.COMPETITIVE_EXAMS, listOf("vajiram & ravi", "vajiram ias")),
        TrustedChannelInfo("Vision IAS", "@visionias", ChannelCategory.COMPETITIVE_EXAMS, listOf("vision ias", "vision ias hindi")),
        TrustedChannelInfo("Adda247", "@adda247", ChannelCategory.COMPETITIVE_EXAMS, listOf("adda247", "ssc adda247", "teachers adda247", "bankers adda")),
        TrustedChannelInfo("Testbook", "@testbook", ChannelCategory.COMPETITIVE_EXAMS, listOf("testbook supercoaching", "testbook pass", "testbook ssc")),
        TrustedChannelInfo("Mohit Tyagi (Competishun)", "@mohittyagi", ChannelCategory.COMPETITIVE_EXAMS, listOf("mohit tyagi", "competishun", "alk sir", "abj sir", "ns sir")),
        TrustedChannelInfo("MathonGo", "@mathongo", ChannelCategory.COMPETITIVE_EXAMS, listOf("mathongo", "anup sir mathongo", "marks app")),
        TrustedChannelInfo("Physics Galaxy", "@physicsgalaxy", ChannelCategory.COMPETITIVE_EXAMS, listOf("physics galaxy", "ashish arora", "pg sir")),
        TrustedChannelInfo("Apni Kaksha", "@apnikaksha", ChannelCategory.COMPETITIVE_EXAMS, listOf("apni kaksha", "aman dhattarwal", "apni kaksha 9 and 10", "apni kaksha jee")),
        TrustedChannelInfo("Neetprep", "@neetprep", ChannelCategory.COMPETITIVE_EXAMS, listOf("neetprep", "dr nk sharma", "kapil sir neetprep")),
        TrustedChannelInfo("Biomentors Classes Online", "@biomentors", ChannelCategory.COMPETITIVE_EXAMS, listOf("biomentors", "dr geetendra singh")),
        TrustedChannelInfo("Doubtnut", "@doubtnut", ChannelCategory.COMPETITIVE_EXAMS, listOf("doubtnut", "doubtnut jee", "doubtnut neet")),
        TrustedChannelInfo("Magnet Brains", "@magnetbrains", ChannelCategory.COMPETITIVE_EXAMS, listOf("magnet brains", "magnet brains education")),
        TrustedChannelInfo("Gagan Pratap Maths", "@gaganpratapmaths", ChannelCategory.COMPETITIVE_EXAMS, listOf("gagan pratap", "gagan pratap sir", "careerwill ssc")),
        TrustedChannelInfo("Rakesh Yadav Readers Publication", "@rakeshyadav", ChannelCategory.COMPETITIVE_EXAMS, listOf("rakesh yadav", "careerwill")),
        TrustedChannelInfo("Aditya Ranjan Talks", "@adityaranjantalks", ChannelCategory.COMPETITIVE_EXAMS, listOf("aditya ranjan sir", "rankers gurukul")),
        TrustedChannelInfo("Rankers Gurukul", "@rankersgurukul", ChannelCategory.COMPETITIVE_EXAMS, listOf("rankers gurukul ssc", "rankers gurukul")),
        TrustedChannelInfo("Canvas Classes", "@canvasclasses", ChannelCategory.COMPETITIVE_EXAMS, listOf("canvas classes", "paaras sir chemistry")),
        TrustedChannelInfo("Sachin Sir Physics", "@sachinsirphysics", ChannelCategory.COMPETITIVE_EXAMS, listOf("ssp", "sachin sir physics")),

        // === CODING, CS & SOFTWARE ENGINEERING ===
        TrustedChannelInfo("CodeWithHarry", "@codewithharry", ChannelCategory.CODING_AND_TECH, listOf("code with harry", "harry bhai", "haris ali khan", "programmingwithharry")),
        TrustedChannelInfo("Chai aur Code", "@chaiaurcode", ChannelCategory.CODING_AND_TECH, listOf("chai aur code", "hitesh choudhary", "hitesh code", "learncodeonline")),
        TrustedChannelInfo("Apna College", "@apnacollege", ChannelCategory.CODING_AND_TECH, listOf("apna college", "shraddha khapra", "aman dhattarwal coding", "delta batch")),
        TrustedChannelInfo("take U forward (Striver)", "@takeuforward", ChannelCategory.CODING_AND_TECH, listOf("striver", "takeuforward", "striver sde sheet", "raj vikramaditya")),
        TrustedChannelInfo("Love Babbar (CodeHelp)", "@lovebabbar", ChannelCategory.CODING_AND_TECH, listOf("love babbar", "codehelp", "love babbar dsa")),
        TrustedChannelInfo("Kunal Kushwaha", "@kunalkushwaha", ChannelCategory.CODING_AND_TECH, listOf("kunal kushwaha", "we make devs", "commclassroom")),
        TrustedChannelInfo("Abdul Bari", "@abdul_bari", ChannelCategory.CODING_AND_TECH, listOf("abdul bari algorithms", "abdul bari dsa")),
        TrustedChannelInfo("Gate Smashers", "@gatesmashers", ChannelCategory.CODING_AND_TECH, listOf("gate smashers", "varun singla gate")),
        TrustedChannelInfo("Knowledge Gate", "@knowledgegate", ChannelCategory.CODING_AND_TECH, listOf("knowledge gate", "sanchit jain")),
        TrustedChannelInfo("Jenny's Lectures CS IT", "@jennyslectures", ChannelCategory.CODING_AND_TECH, listOf("jenny's lectures", "jenny cs it")),
        TrustedChannelInfo("freeCodeCamp.org", "@freecodecamp", ChannelCategory.CODING_AND_TECH, listOf("freecodecamp", "free code camp")),
        TrustedChannelInfo("Programming with Mosh", "@programmingwithmosh", ChannelCategory.CODING_AND_TECH, listOf("mosh hamedani", "programming with mosh")),
        TrustedChannelInfo("Traversy Media", "@traversymedia", ChannelCategory.CODING_AND_TECH, listOf("traversy media", "brad traversy")),
        TrustedChannelInfo("Fireship", "@fireship", ChannelCategory.CODING_AND_TECH, listOf("fireship", "fireship.io", "beyondfireship")),
        TrustedChannelInfo("CS Dojo", "@csdojo", ChannelCategory.CODING_AND_TECH, listOf("cs dojo", "yk sanchit")),
        TrustedChannelInfo("Telusko", "@telusko", ChannelCategory.CODING_AND_TECH, listOf("telusko", "navin reddy")),
        TrustedChannelInfo("GeeksforGeeks", "@geeksforgeeks", ChannelCategory.CODING_AND_TECH, listOf("geeksforgeeks", "gfg", "geeks for geeks")),
        TrustedChannelInfo("Edureka", "@edureka", ChannelCategory.CODING_AND_TECH, listOf("edureka", "edureka in")),
        TrustedChannelInfo("Simplilearn", "@simplilearn", ChannelCategory.CODING_AND_TECH, listOf("simplilearn", "simplilearn official")),
        TrustedChannelInfo("MIT OpenCourseWare", "@mitocw", ChannelCategory.CODING_AND_TECH, listOf("mit opencourseware", "mit ocw", "mit lectures")),
        TrustedChannelInfo("Stanford Online", "@stanfordonline", ChannelCategory.CODING_AND_TECH, listOf("stanford online", "stanford university lectures", "cs229", "cs231n")),
        TrustedChannelInfo("CS50", "@cs50", ChannelCategory.CODING_AND_TECH, listOf("cs50", "david j malan", "harvard cs50")),
        TrustedChannelInfo("NPTEL-NOC IITM", "@nptel", ChannelCategory.CODING_AND_TECH, listOf("nptel", "nptel iit", "nptelhrd", "iit lectures")),

        // === K-12 & SCHOOL BOARDS (CBSE, ICSE, STATE BOARDS) ===
        TrustedChannelInfo("Dear Sir", "@dearsir", ChannelCategory.K12_AND_BOARDS, listOf("dear sir", "dear sir english", "dear sir maths")),
        TrustedChannelInfo("Shubham Pathak", "@shubhampathak", ChannelCategory.K12_AND_BOARDS, listOf("shubham pathak sst", "shubham pathak biology")),
        TrustedChannelInfo("Science and Fun", "@scienceandfun", ChannelCategory.K12_AND_BOARDS, listOf("science and fun", "ashu sir science and fun")),
        TrustedChannelInfo("Prashant Kirad (Exphub)", "@prashantkirad", ChannelCategory.K12_AND_BOARDS, listOf("prashant kirad", "exphub", "exphub 9&10")),
        TrustedChannelInfo("Next Toppers", "@nexttoppers", ChannelCategory.K12_AND_BOARDS, listOf("next toppers", "digraj sir sst", "digraj singh rajput")),
        TrustedChannelInfo("LearnOHub - Class 11, 12", "@learnohub", ChannelCategory.K12_AND_BOARDS, listOf("learnohub", "examfear education", "roshni mam")),
        TrustedChannelInfo("BKP (Bhai Ki Padhai)", "@bhaikipadhai", ChannelCategory.K12_AND_BOARDS, listOf("bhai ki padhai", "bkp")),

        // === HIGHER ED, SCIENCE, KNOWLEDGE & CONCEPTUAL ===
        TrustedChannelInfo("3Blue1Brown", "@3blue1brown", ChannelCategory.HIGHER_ED_AND_STEM, listOf("3blue1brown", "grant sanderson")),
        TrustedChannelInfo("Veritasium", "@veritasium", ChannelCategory.HIGHER_ED_AND_STEM, listOf("veritasium", "derek muller")),
        TrustedChannelInfo("MinutePhysics", "@minutephysics", ChannelCategory.HIGHER_ED_AND_STEM, listOf("minutephysics", "minute physics")),
        TrustedChannelInfo("Kurzgesagt – In a Nutshell", "@kurzgesagt", ChannelCategory.HIGHER_ED_AND_STEM, listOf("kurzgesagt", "in a nutshell")),
        TrustedChannelInfo("Numberphile", "@numberphile", ChannelCategory.HIGHER_ED_AND_STEM, listOf("numberphile", "brady haran")),
        TrustedChannelInfo("SmarterEveryDay", "@smartereveryday", ChannelCategory.HIGHER_ED_AND_STEM, listOf("smarter everyday", "destin sandlin")),
        TrustedChannelInfo("CrashCourse", "@crashcourse", ChannelCategory.HIGHER_ED_AND_STEM, listOf("crashcourse", "crash course", "hank green", "john green")),
        TrustedChannelInfo("TED-Ed", "@teded", ChannelCategory.HIGHER_ED_AND_STEM, listOf("ted-ed", "ted ed", "ted lessons"))
    )

    /**
     * Normalizes a channel query or handle for comparison.
     */
    fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace("@", "")
            .replace("&", "and")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Checks if a channel name or handle matches any known trusted educational institution or creator.
     */
    fun isKnownEducationalChannel(channelNameOrHandle: String?): Boolean {
        if (channelNameOrHandle.isNullOrBlank()) return false
        val clean = normalize(channelNameOrHandle)

        // Direct containment check on trusted channel entries
        for (channel in TRUSTED_CHANNELS) {
            val normName = normalize(channel.name)
            if (clean == normName || clean.contains(normName) || normName.contains(clean)) {
                return true
            }
            if (channel.handle != null) {
                val normHandle = normalize(channel.handle)
                if (clean == normHandle || clean.contains(normHandle)) {
                    return true
                }
            }
            for (alias in channel.aliases) {
                val normAlias = normalize(alias)
                if (clean == normAlias || clean.contains(normAlias)) {
                    return true
                }
            }
        }

        // Generic patterns for institutional and academic channels
        val institutionalKeywords = listOf(
            "academy", "institute", "classes", "education", "tutorial", "lectures",
            "coaching", "university", "faculty", "physics", "chemistry", "mathematics",
            "biology", "school", "college", "foundation", "iit", "neet", "upsc", "ias",
            "coding", "learn", "study", "prep", "exam", "board", "mathongo", "pw",
            "unacademy", "vedantu", "byju", "doubtnut", "testbook", "adda247"
        )
        return institutionalKeywords.any { clean.contains(it) }
    }

    /**
     * Retrieves channel category if recognized.
     */
    fun getChannelCategory(channelNameOrHandle: String?): ChannelCategory? {
        if (channelNameOrHandle.isNullOrBlank()) return null
        val clean = normalize(channelNameOrHandle)

        for (channel in TRUSTED_CHANNELS) {
            if (clean.contains(normalize(channel.name)) ||
                (channel.handle != null && clean.contains(normalize(channel.handle))) ||
                channel.aliases.any { clean.contains(normalize(it)) }
            ) {
                return channel.category
            }
        }
        return null
    }

    /**
     * List of all indexed channels for UI / information reference.
     */
    fun getAllTrustedChannels(): List<TrustedChannelInfo> = TRUSTED_CHANNELS
}
