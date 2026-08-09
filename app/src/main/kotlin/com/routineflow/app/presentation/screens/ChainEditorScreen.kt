package com.routineflow.app.presentation.screens

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.card.MaterialCardView
import com.routineflow.app.R
import com.routineflow.app.model.Action
import com.routineflow.app.model.AppState
import com.routineflow.app.model.Chain
import com.routineflow.app.presentation.RecurrenceDisplayFormatter
import com.routineflow.app.presentation.ScreenLayout
import com.routineflow.app.presentation.TimeFormatter
import com.routineflow.app.presentation.components.ReorderDragController

class ChainEditorScreen(
    private val context: android.content.Context,
    private val textColor: Int,
    private val accentColor: Int,
    private val card: (View) -> MaterialCardView,
    private val reorder: ReorderDragController,
    private val recurrenceFormatter: RecurrenceDisplayFormatter,
    private val onNameChanged: (Chain, String) -> Unit,
    private val onEditAction: (Chain, Action) -> Unit,
    private val onDragAction: (View, Action) -> Boolean,
    private val onDragActionEvent: (Chain, Action, View, View, android.view.DragEvent) -> Boolean,
    private val onMoveActionToEnd: (Chain, Long) -> Unit,
    private val settingsBar: (AppState, Chain) -> View
) {
    fun build(state: AppState, chain: Chain): LinearLayout {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val nameField = EditText(context).apply {
            setText(chain.name); textSize = 30f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(textColor); setSingleLine(true); setPadding(0, 0, 0, 20); background = null
            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) onNameChanged(chain, text.toString()) }
            setOnEditorActionListener { _, _, _ -> clearFocus(); true }
        }
        root.addView(nameField)
        if (chain.actions.isEmpty()) root.addView(TextView(context).apply { text = context.getString(R.string.chain_empty); setPadding(0, 20, 0, 10) })
        chain.actions.forEachIndexed { index, action -> root.addView(actionCard(chain, index, action), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }) }
        root.addView(reorder.endDropZone { id -> onMoveActionToEnd(chain, id) }, LinearLayout.LayoutParams(-1, 10))
        root.addView(Space(context), LinearLayout.LayoutParams(1, 0, 1f)); root.addView(settingsBar(state, chain), LinearLayout.LayoutParams(-1, ScreenLayout.BOTTOM_MENU_HEIGHT_PX))
        return root
    }

    private fun actionCard(chain: Chain, index: Int, action: Action): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8, 12, 8, 12); isClickable = true; isFocusable = true }
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        details.addView(TextView(context).apply { text = "${index + 1}. ${action.title}"; textSize = 17f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(textColor) })
        details.addView(TextView(context).apply { text = "${recurrenceFormatter.format(action.recurrence)} • ${TimeFormatter.duration(action.durationSeconds.toLong())}"; textSize = 14f; setTextColor(0xff64748b.toInt()); setPadding(0, 4, 0, 0) })
        details.setOnLongClickListener { onDragAction(details, action) }; row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageView(context).apply { setImageResource(android.R.drawable.ic_menu_sort_by_size); imageTintList = ColorStateList.valueOf(0xff94a3b8.toInt()); contentDescription = context.getString(R.string.reorder_chains); setPadding(8, 8, 4, 8); setOnLongClickListener { onDragAction(this, action) } }, LinearLayout.LayoutParams(44, 44))
        row.setOnClickListener { onEditAction(chain, action) }; details.setOnClickListener { onEditAction(chain, action) }
        val marker = View(context).apply { setBackgroundColor(accentColor); visibility = View.GONE }
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(marker, LinearLayout.LayoutParams(-1, 4)); addView(row) }
        row.setOnDragListener { view, event -> onDragActionEvent(chain, action, view, marker, event) }
        return card(content).apply { setOnClickListener { onEditAction(chain, action) }; setOnLongClickListener { onDragAction(this, action) }; setOnDragListener { view, event -> onDragActionEvent(chain, action, view, marker, event) } }
    }

}
