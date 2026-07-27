package com.routineflow.app.data

import com.routineflow.app.model.Chain
import kotlinx.coroutines.flow.StateFlow

interface ChainRepository {
    val chains: StateFlow<List<Chain>>
    suspend fun replace(chains: List<Chain>)
}
