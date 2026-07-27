package com.routineflow.app.domain

import com.routineflow.app.model.Action
import java.util.Calendar
import javax.inject.Inject

class GetTodayActionsUseCase @Inject constructor() {
    fun isDue(action: Action): Boolean = RecurrenceStrategyFactory.create(action.recurrence).isDue(action, Calendar.getInstance())
}
