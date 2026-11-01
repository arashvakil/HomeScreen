package nyc.arash.homescreen.ui

import kotlin.random.Random

/** Original, short lines that fit the TV header. One per visit, without immediate repeats. */
internal class WelcomeQuotes(private val random: Random = Random.Default) {
    private var previous: String? = null

    fun next(): String = lines.filterNot { it == previous }.random(random).also { previous = it }

    companion object {
        val lines = listOf(
            "Dream big. Sit comfortably.",
            "Your couch believes in you.",
            "Small steps. Excellent snacks.",
            "Today has potential. So do you.",
            "Rest is part of the plan.",
            "Be kind. Rewind. Or just stream.",
            "A little joy goes a long way.",
            "Great things start with a pause.",
            "You bring the magic. We have apps.",
            "Progress counts. Pajamas welcome.",
            "Take a breath. Find your happy.",
            "Life is short. Pick the good stuff.",
            "The remote is in good hands.",
            "You made it. Time to unwind.",
            "Stay curious. Stay cozy.",
            "Big ideas need comfy seats.",
            "Less scrolling. More living.",
            "The best seat is yours.",
            "Plot twist: today could be great.",
            "Do your best. Then take a break.",
            "Even superheroes need a sofa.",
            "Good things take time. And snacks.",
            "There is room for joy today.",
            "Recharge. Your next chapter awaits.",
        )
    }
}
