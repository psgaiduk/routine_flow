package com.routineflow.app.presentation.components

import android.content.ClipData
import android.content.Context
import android.view.DragEvent
import android.view.View
import android.widget.Toast
import com.google.android.material.card.MaterialCardView

/** Shared drag-and-drop mechanics for ordered chains and actions. */
class ReorderDragController(
    private val context: Context,
    private val accentColor: Int,
    private val borderColor: Int,
    private val dragHint: String
) {
    private var sourceView: View? = null

    fun begin(source: View, itemId: Long): Boolean {
        sourceView = source
        source.alpha = 0.45f
        Toast.makeText(context, dragHint, Toast.LENGTH_SHORT).show()
        @Suppress("DEPRECATION")
        return source.startDrag(ClipData.newPlainText("reorder_id", itemId.toString()), View.DragShadowBuilder(source), null, 0)
    }

    fun handle(
        targetView: View,
        insertionMarker: View,
        event: DragEvent,
        onDrop: (Long) -> Unit
    ): Boolean = when (event.action) {
        DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("text/plain") == true
        DragEvent.ACTION_DRAG_ENTERED -> {
            if (targetView is MaterialCardView) targetView.strokeColor = accentColor
            insertionMarker.visibility = View.VISIBLE
            true
        }
        DragEvent.ACTION_DRAG_EXITED -> {
            resetTarget(targetView, insertionMarker)
            true
        }
        DragEvent.ACTION_DROP -> {
            event.clipData.getItemAt(0).text.toString().toLongOrNull()?.let(onDrop)
            resetTarget(targetView, insertionMarker)
            true
        }
        DragEvent.ACTION_DRAG_ENDED -> {
            resetTarget(targetView, insertionMarker)
            sourceView?.alpha = 1f
            sourceView = null
            true
        }
        else -> false
    }

    fun endDropZone(onDrop: (Long) -> Unit): View = View(context).apply {
        setBackgroundColor(accentColor)
        alpha = 0f
        setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("text/plain") == true
                DragEvent.ACTION_DRAG_ENTERED -> { alpha = 1f; true }
                DragEvent.ACTION_DRAG_EXITED -> { alpha = 0f; true }
                DragEvent.ACTION_DROP -> {
                    event.clipData.getItemAt(0).text.toString().toLongOrNull()?.let(onDrop)
                    alpha = 0f
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    alpha = 0f
                    sourceView?.alpha = 1f
                    sourceView = null
                    true
                }
                else -> false
            }
        }
    }

    private fun resetTarget(targetView: View, insertionMarker: View) {
        if (targetView is MaterialCardView) targetView.strokeColor = borderColor
        insertionMarker.visibility = View.GONE
    }
}
