package com.routineflow.app.presentation

enum class AppTab { RUN, CHAINS, STATS }

data class NavigationState(
    val tab: AppTab = AppTab.RUN,
    val chainId: Long? = null,
    val executionChainId: Long? = null
)
