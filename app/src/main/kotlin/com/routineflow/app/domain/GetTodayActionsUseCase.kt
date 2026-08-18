package com.routineflow.app.domain

import com.routineflow.app.model.Action
import java.util.Calendar
import javax.inject.Inject

class GetTodayActionsUseCase @Inject constructor() {
    fun isDue(action: Action): Boolean = isDue(action, RoutineDay.currentCalendar())

    fun isDue(action: Action, day: Calendar): Boolean {
        val dayKey = RoutineDay.dateKey(day)
        if (action.startDate != null && dayKey < action.startDate) return false
        if (action.endDate != null && dayKey > action.endDate) return false
        return RecurrenceStrategyFactory.create(action.recurrence).isDue(action, day)
    }
}
