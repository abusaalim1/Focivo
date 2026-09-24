package com.example

import com.example.util.OnDeviceStudyClassifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceStudyClassifierTest {

    private val studyExamples = listOf(
        "Explain gravity in simple words for a 10th grade student",
        "Solve this equation step by step: 3(2x – 4) = 18",
        "Can you give me an example of how to solve a trigonometry problem?",
        "Quiz me on 15 biology vocabulary words about cell structure",
        "Create a quiz on World War II history with ten multiple-choice questions",
        "Explain mitosis like I'm in 7th grade",
        "Summarize this topic in a table comparing causes vs effects",
        "Make a clear comparison table showing differences between mitosis and meiosis",
        "Give me three analogies to help me remember Newton's three laws of motion",
        "Edit this paragraph for grammar and clarity: [pasted text]",
        "Act as my Spanish teacher and quiz me on irregular verbs",
        "Design a lesson plan for teaching basic algebra to middle schoolers",
        "What are the causes of the French Revolution?",
        "Help me understand photosynthesis",
        "Can you check my essay for grammar mistakes?",
        "Explain the theory of relativity in simple terms",
        "What's the difference between mitosis and meiosis?",
        "Help me practice speaking French with a simple conversation",
        "Give me 10 practice questions on organic chemistry",
        "Explain Newton's second law with a real-world example",
        "What is the formula for the area of a triangle?",
        "Help me outline an essay about climate change",
        "Can you proofread my college application essay?",
        "What are the key differences between DNA and RNA?",
        "Explain how photosynthesis works step by step",
        "Create flashcards for these vocabulary words: [list]",
        "I don't understand this physics problem, can you help?",
        "What's a good thesis statement for an essay on renewable energy?",
        "Explain the water cycle for a science project",
        "Help me solve this calculus derivative problem",
        "What are Newton's laws of motion?",
        "Summarize chapter 5 of my history textbook",
        "Give me a mnemonic to remember the periodic table",
        "Can you explain supply and demand in economics?",
        "Help me structure my research paper introduction"
    )

    private val ambiguousStudyCases = listOf(
        "Explain machine learning to me",
        "Help me write a cover letter",
        "What's a good book to read?",
        "Tell me about World War II",
        "Help me practice for a job interview"
    )

    private val nonStudyExamples = listOf(
        "Where should I travel for a beach vacation?",
        "What's my horoscope for today?",
        "Can you suggest a recipe using chicken, garlic, and rice?",
        "What are some good birthday gift ideas for my parents?",
        "Can you create a short story for children about a curious kitten?",
        "Give me a compliment to brighten my day",
        "Ask me something interesting, make small talk",
        "You're a longtime bookstore owner, recommend me books based on [list]",
        "Act as a time-travel tour guide and take me to ancient Rome",
        "What should I get my girlfriend for her birthday?",
        "Tell me a funny joke",
        "What's the weather like today?",
        "Can you help me write a text to apologize to my friend?",
        "What's the best pizza topping combination?",
        "Roleplay as a superhero and save the city",
        "What should I watch on Netflix tonight?",
        "Give me relationship advice, my partner and I are fighting",
        "What's a good name for my new puppy?",
        "Can you write a funny birthday message for my friend?",
        "What are some conversation starters for a first date?",
        "Help me plan a surprise party for my mom",
        "What's trending on social media right now?",
        "Can you roast me?",
        "Tell me about celebrity gossip",
        "What's the latest iPhone release date?",
        "Help me pick an outfit for a party",
        "What video game should I play this weekend?",
        "Write me a poem about my crush",
        "Can we just talk, I'm bored",
        "What's your opinion on [celebrity/drama topic]?",
        "Help me come up with a funny Instagram caption",
        "What's the meaning of life?",
        "Tell me a scary story",
        "Can you pretend to be my girlfriend/boyfriend and chat with me?",
        "What should I cook for dinner tonight?",
        "Give me pickup lines to use on someone I like",
        "What's a good workout routine for building muscle?",
        "Recommend me a good anime to watch",
        "Help me write a breakup text",
        "What's happening in the news today?"
    )

    @Test
    fun testAllStudyExamplesAreClassifiedAsStudy() {
        for ((index, prompt) in studyExamples.withIndex()) {
            val result = OnDeviceStudyClassifier.evaluateContent(prompt)
            assertFalse(
                "Study example #${index + 1} was incorrectly flagged as casual: '$prompt' (reason: ${result.reason})",
                result.isCasual
            )
        }
    }

    @Test
    fun testAmbiguousCasesDefaultToStudy() {
        for ((index, prompt) in ambiguousStudyCases.withIndex()) {
            val result = OnDeviceStudyClassifier.evaluateContent(prompt)
            assertFalse(
                "Ambiguous example #${index + 1} was incorrectly flagged as casual: '$prompt' (reason: ${result.reason})",
                result.isCasual
            )
        }
    }

    @Test
    fun testAllNonStudyExamplesAreClassifiedAsNonStudy() {
        for ((index, prompt) in nonStudyExamples.withIndex()) {
            val result = OnDeviceStudyClassifier.evaluateContent(prompt)
            assertTrue(
                "Non-study example #${index + 1} was NOT flagged as casual: '$prompt' (reason: ${result.reason})",
                result.isCasual
            )
        }
    }
}
