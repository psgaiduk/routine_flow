package com.routineflow.app.presentation

import java.util.Locale

object TimeFormatter {
    fun adaptive(seconds: Long): String = when {
        seconds >= 3600 -> "%02d:%02d:%02d".format(Locale.US, seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        seconds >= 60 -> "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)
        else -> "%02d".format(Locale.US, seconds)
    }

    fun duration(seconds: Long): String = "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)
}
