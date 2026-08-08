package com.routineflow.app.presentation.components

import android.app.AlertDialog
import android.graphics.Typeface
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.routineflow.app.R
import com.routineflow.app.model.Chain

class ChainDialogFactory(
    private val context: android.content.Context,
    private val textColor: Int,
    private val secondaryColor: Int,
    private val inputSurfaceColor: Int,
    private val borderColor: Int,
    private val create: (String) -> Unit,
    private val rename: (Long, String) -> Unit
) {
    fun showCreate() {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 14, 24, 16) }
        content.addView(TextView(context).apply { text = context.getString(R.string.new_chain); textSize = 24f; setTypeface(typeface, Typeface.BOLD); setTextColor(textColor); setPadding(0, 0, 0, 6) })
        content.addView(TextView(context).apply { text = context.getString(R.string.chain_name_label); textSize = 14f; setTextColor(secondaryColor); setPadding(0, 0, 0, 22) })
        val input = EditText(context).apply {
            tag = "chain_create_input"
            hint = context.getString(R.string.chain_name_hint); textSize = 17f; setSingleLine(true); setTextColor(textColor); setHintTextColor(secondaryColor); setPadding(16, 14, 16, 14)
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 16f; setColor(inputSurfaceColor); setStroke(1, borderColor) }
        }
        content.addView(input, LinearLayout.LayoutParams(-1, 88))
        MaterialAlertDialogBuilder(context).setView(content).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.create) { _, _ -> input.text.toString().trim().takeIf(String::isNotEmpty)?.let(create) }.show()
    }

    fun showEdit(chain: Chain) {
        val input = EditText(context).apply { tag = "chain_edit_input"; setText(chain.name); hint = context.getString(R.string.chain_name_hint) }
        AlertDialog.Builder(context).setTitle(context.getString(R.string.edit_chain)).setView(input).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save) { _, _ -> input.text.toString().trim().takeIf(String::isNotEmpty)?.let { rename(chain.id, it) } }.show()
    }
}
