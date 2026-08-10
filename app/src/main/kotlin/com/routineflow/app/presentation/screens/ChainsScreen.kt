package com.routineflow.app.presentation.screens

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.routineflow.app.R
import com.routineflow.app.model.Chain
import com.routineflow.app.presentation.TimeFormatter
import com.routineflow.app.presentation.components.ReorderDragController

class ChainsScreen(
    private val context: android.content.Context,
    private val textColor: Int,
    private val secondaryColor: Int,
    private val borderColor: Int,
    private val reorder: ReorderDragController,
    private val card: (View) -> MaterialCardView,
    private val onSettings: (View) -> Unit,
    private val onOpen: (Chain) -> Unit,
    private val onNew: () -> Unit,
    private val onMoveToEnd: (Long) -> Unit,
    private val onDrag: (View, Long) -> Boolean,
    private val onDragEvent: (Long, View, View, android.view.DragEvent) -> Boolean
) {
    fun build(chains: List<Chain>): LinearLayout {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 20) }
        header.addView(TextView(context).apply { text = context.getString(R.string.tab_chains); textSize = 30f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(textColor) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ImageButton(context).apply { setImageResource(R.drawable.ic_settings); imageTintList = ColorStateList.valueOf(textColor); background = null; contentDescription = context.getString(R.string.chain_settings); setPadding(8, 8, 8, 8); setOnClickListener { onSettings(this) } }, LinearLayout.LayoutParams(56, 56).apply { marginEnd = 8 })
        root.addView(header)
        chains.forEach { chain -> root.addView(chainCard(chain), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }) }
        root.addView(reorder.endDropZone(onMoveToEnd), LinearLayout.LayoutParams(-1, 10))
        root.addView(MaterialButton(context).apply { text = "＋  ${context.getString(R.string.new_chain)}"; isAllCaps = false; cornerRadius = 18; setOnClickListener { onNew() } })
        return root
    }

    private fun chainCard(chain: Chain): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(4, 12, 4, 12) }
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        details.addView(TextView(context).apply { text = context.getString(R.string.run_chain_meta, TimeFormatter.duration(chain.actions.sumOf { it.durationSeconds }.toLong()), chain.actions.size); textSize = 13f; setTextColor(secondaryColor) })
        details.addView(TextView(context).apply { text = chain.name; textSize = 20f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(textColor); setPadding(0, 4, 0, 0) })
        details.setOnLongClickListener { onDrag(details, chain.id) }
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageView(context).apply { setImageResource(android.R.drawable.ic_menu_sort_by_size); imageTintList = ColorStateList.valueOf(secondaryColor); contentDescription = context.getString(R.string.reorder_chains); setPadding(8, 8, 4, 8); setOnLongClickListener { onDrag(this, chain.id) } }, LinearLayout.LayoutParams(44, 44))
        row.setOnClickListener { onOpen(chain) }; details.setOnClickListener { onOpen(chain) }
        val marker = View(context).apply { setBackgroundColor(0xff4f46e5.toInt()); visibility = View.GONE }
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(marker, LinearLayout.LayoutParams(-1, 4)); addView(row) }
        val result = card(content).apply { radius = 22f; strokeWidth = 2; strokeColor = borderColor; isClickable = true; isFocusable = true; setOnClickListener { onOpen(chain) }; setOnLongClickListener { onDrag(this, chain.id) } }
        result.setOnDragListener { view, event -> onDragEvent(chain.id, view, marker, event) }
        return result
    }
}
