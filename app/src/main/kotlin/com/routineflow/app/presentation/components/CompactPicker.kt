package com.routineflow.app.presentation.components

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

/** Reusable three-row numeric picker used by the duration editor. */
class CompactPicker(
    context: Context,
    private val min: Int,
    private val max: Int,
    initial: Int,
    private val wrap: Boolean,
    private val textColor: Int
) : LinearLayout(context) {
    private var selected = initial.coerceIn(min, max)
    private var startY = 0f
    private var lastY = 0f
    private var moved = false
    private val values = listOf(TextView(context), TextView(context), TextView(context))

    var value: Int
        get() = selected
        set(newValue) {
            selected = newValue.coerceIn(min, max)
            render()
        }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        values.forEach { view ->
            view.textSize = 20f
            view.gravity = Gravity.CENTER
            view.setPadding(0, 0, 0, 0)
            view.setTextColor(textColor)
            view.typeface = Typeface.create("sans", Typeface.NORMAL)
            addView(view, LayoutParams(-1, 72))
        }
        values[1].textSize = 26f
        values[1].translationY = -10f
        render()
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    lastY = event.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    var distance = event.y - lastY
                    while (abs(distance) >= 28f) {
                        change(if (distance < 0) 1 else -1)
                        lastY += if (distance < 0) -28f else 28f
                        distance = event.y - lastY
                        moved = true
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && abs(event.y - startY) < 12f) {
                        if (event.y < height / 3f) change(-1)
                        else if (event.y > height * 2f / 3f) change(1)
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun change(delta: Int) {
        val candidate = selected + delta
        selected = when {
            wrap && candidate > max -> min
            wrap && candidate < min -> max
            else -> candidate.coerceIn(min, max)
        }
        render()
    }

    private fun render() {
        fun formatted(number: Int) = "%02d".format(Locale.US, number)
        values[0].text = if (selected == min && !wrap) "" else formatted(if (selected == min) max else selected - 1)
        values[1].text = formatted(selected)
        values[2].text = if (selected == max && !wrap) "" else formatted(if (selected == max) min else selected + 1)
        values.forEach { it.alpha = 1f }
    }
}
