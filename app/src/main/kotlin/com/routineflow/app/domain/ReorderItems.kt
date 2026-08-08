package com.routineflow.app.domain

/** Moves an item before or after the target, preserving the existing drag behavior. */
fun <T> moveItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
    val reordered = items.toMutableList()
    val moved = reordered.removeAt(fromIndex)
    val targetIndex = reordered.indexOf(items[toIndex]).coerceAtLeast(0)
    val insertionIndex = if (fromIndex < toIndex) targetIndex + 1 else targetIndex
    reordered.add(insertionIndex.coerceIn(0, reordered.size), moved)
    return reordered
}
