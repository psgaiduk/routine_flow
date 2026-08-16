package com.routineflow.app.notifications

interface RoutineNotifier {
    fun playCountdownBeep()
    fun playCompletionAlarm()
    fun notifyOvertime(chainName: String, actionName: String, reminderNumber: Int, overtimeSeconds: Long)
    fun updateTimer(chainName: String, actionName: String, elapsedSeconds: Long, totalSeconds: Long, overtime: Boolean)
    fun clearTimer()
}
