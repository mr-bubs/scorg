package com.ncorti.kotlin.template.app

import android.content.Context
import java.util.Calendar

object BubsModeManager {

    data class Judgment(
        val reaction: BubsMascotFrames.Reaction,
        val message: String
    )

    private const val PREFS = "scorg_bubs_mode"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_SCREENSHOT = "last_screenshot"
    private const val KEY_STREAK_START = "streak_start"
    private const val KEY_STREAK_COUNT = "streak_count"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun nextJudgment(context: Context): Judgment {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_SCREENSHOT, 0L)
        var streakStart = prefs.getLong(KEY_STREAK_START, 0L)
        var streakCount = prefs.getInt(KEY_STREAK_COUNT, 0)

        if (streakStart == 0L || now - streakStart > 120_000L) {
            streakStart = now
            streakCount = 1
        } else {
            streakCount += 1
        }

        prefs.edit()
            .putLong(KEY_LAST_SCREENSHOT, now)
            .putLong(KEY_STREAK_START, streakStart)
            .putInt(KEY_STREAK_COUNT, streakCount)
            .apply()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val sinceLast = if (last == 0L) Long.MAX_VALUE else now - last

        return when {
            hour in 0..5 -> Judgment(
                BubsMascotFrames.Reaction.SLEEPY,
                "At this hour? Really?"
            )

            streakCount >= 6 -> Judgment(
                BubsMascotFrames.Reaction.ANNOYED,
                "We just cleaned this place."
            )

            sinceLast < 12_000L -> Judgment(
                BubsMascotFrames.Reaction.SURPRISED,
                "Another one already?"
            )

            streakCount >= 3 -> Judgment(
                BubsMascotFrames.Reaction.THINKING,
                "Building a collection, are we?"
            )

            sinceLast > 3_600_000L -> Judgment(
                BubsMascotFrames.Reaction.HAPPY,
                "Oh. We're back."
            )

            else -> Judgment(
                BubsMascotFrames.Reaction.PLAYFUL,
                "You have a folder for that, right?"
            )
        }
    }
}
