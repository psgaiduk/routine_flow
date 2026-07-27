package com.routineflow.app

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.routineflow.app.model.Action
import com.routineflow.app.model.AppState
import com.routineflow.app.model.Chain
import com.routineflow.app.presentation.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private enum class Tab { RUN, CHAINS, STATS }
    private val viewModel: MainViewModel by viewModels()
    private var currentChainId: Long? = null
    private var currentTab = Tab.RUN
    private val dateKey get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private val navy = 0xff172554.toInt()
    private val accent = 0xff4f46e5.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    private fun render(state: AppState) {
        val chain = currentChainId?.let { id -> state.chains.firstOrNull { it.id == id } }
        if (currentChainId != null && chain == null) currentChainId = null
        when (currentTab) {
            Tab.RUN -> showToday(state)
            Tab.CHAINS -> if (chain != null) showChain(state, chain) else showChains(state)
            Tab.STATS -> showStats(state)
        }
    }

    private fun base(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; val baseTop = 28; val baseBottom = 24; setPadding(24, baseTop, 24, baseBottom); setBackgroundColor(0xfff8fafc.toInt())
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(24, baseTop + bars.top, 24, baseBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(this)
        addView(TextView(this@MainActivity).apply { text = title; textSize = 30f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(navy); setPadding(0, 0, 0, 20) })
    }

    private fun button(text: String, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; cornerRadius = 18; setTextSize(15f); setTextColor(0xffffffff.toInt())
        backgroundTintList = ColorStateList.valueOf(accent); insetTop = 6; insetBottom = 6
        setOnClickListener { onClick() }
    }

    private fun card(content: View) = MaterialCardView(this).apply {
        radius = 22f; cardElevation = 0f; setCardBackgroundColor(0xffffffff.toInt()); setContentPadding(16, 8, 16, 8); addView(content)
    }

    private fun showToday(state: AppState) {
        val root = base("Сегодня")
        val chainsToday = state.chains.filter { chain -> chain.actions.any { viewModel.isDue(it) } }
        root.addView(TextView(this).apply { text = if (chainsToday.isEmpty()) "На сегодня цепочек нет" else "Запускайте цепочку — действия выполнятся последовательно"; textSize = 15f; setPadding(0, 0, 0, 12) })
        chainsToday.forEach { chain ->
            val dueActions = chain.actions.filter { viewModel.isDue(it) }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val running = state.running?.takeIf { it.chainId == chain.id }
            val currentAction = running?.let { current -> chain.actions.firstOrNull { it.id == current.actionId } }
            val completed = dueActions.count { it.doneOn == dateKey }
            val label = TextView(this).apply {
                text = if (currentAction != null) "${chain.name}\nШаг ${completed + 1} из ${dueActions.size}: ${currentAction.title}" else "${chain.name}\n$completed из ${dueActions.size} действий выполнено"
                textSize = 16f; setPadding(0, 8, 8, 8)
            }
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            if (running != null) {
                row.addView(button(formatDuration(running.remainingSeconds)) { viewModel.stopAction() })
            } else if (completed == dueActions.size) {
                row.addView(TextView(this).apply { text = "✓ Готово"; textSize = 14f })
            } else {
                row.addView(button("Запустить") { viewModel.startChain(chain.id) })
            }
            root.addView(card(row))
        }
        addBottomNav(root, Tab.RUN)
        setContentView(root)
    }

    private fun showChains(state: AppState) {
        val root = base(getString(R.string.tab_chains))
        root.addView(TextView(this).apply { text = getString(R.string.chains_subtitle); textSize = 16f; setPadding(0, 12, 0, 8) })
        state.chains.forEach { chain -> root.addView(button(chain.name) { currentChainId = chain.id; render(state) }) }
        root.addView(button("＋  ${getString(R.string.new_chain)}") { newChainDialog() })
        addBottomNav(root, Tab.CHAINS)
        setContentView(root)
    }

    private fun showStats(state: AppState) {
        val root = base(getString(R.string.tab_stats))
        root.addView(TextView(this).apply { text = getString(R.string.stats_placeholder); textSize = 18f; setPadding(0, 32, 0, 32) })
        addBottomNav(root, Tab.STATS)
        setContentView(root)
    }

    private fun bottomNav(active: Tab): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 0, 0, 0)
        background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 0f; setColor(0xffe2e8f0.toInt()) }
        fun add(label: String, iconRes: Int, tab: Tab) {
            val selected = tab == active
            val foreground = if (selected) 0xffffffff.toInt() else 0xff334155.toInt()
            val item = MaterialButton(this@MainActivity).apply {
                text = label; isAllCaps = false; textSize = 14f; cornerRadius = 8
                this.icon = getDrawable(iconRes); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_TOP; iconPadding = 14; iconSize = 64
                minHeight = 144; minWidth = 0; setPadding(4, 12, 4, 12); insetTop = 0; insetBottom = 0; elevation = 0f; stateListAnimator = null
                backgroundTintList = ColorStateList.valueOf(if (selected) accent else android.graphics.Color.TRANSPARENT)
                iconTint = ColorStateList.valueOf(foreground); setTextColor(foreground)
                setOnClickListener { currentTab = tab; currentChainId = null; render(viewModel.state.value) }
            }
            addView(item, LinearLayout.LayoutParams(0, 150, 1f))
        }
        add(getString(R.string.tab_run), android.R.drawable.ic_media_play, Tab.RUN)
        add(getString(R.string.tab_chains), android.R.drawable.ic_menu_sort_by_size, Tab.CHAINS)
        add(getString(R.string.tab_stats), android.R.drawable.ic_menu_info_details, Tab.STATS)
    }

    private fun addBottomNav(root: LinearLayout, active: Tab) {
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(bottomNav(active), LinearLayout.LayoutParams(-1, 158))
    }

    private fun showChain(state: AppState, chain: Chain) {
        val root = base(chain.name)
        root.addView(button("← ${getString(R.string.tab_chains)}") { currentChainId = null; currentTab = Tab.CHAINS; render(state) })
        root.addView(button("✎ Изменить название") { editChainDialog(chain) })
        if (chain.actions.isEmpty()) root.addView(TextView(this).apply { text = "В цепочке пока нет действий"; setPadding(0, 20, 0, 10) })
        chain.actions.forEachIndexed { index, action ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val check = CheckBox(this).apply { isChecked = action.doneOn == dateKey; text = "${index + 1}. ${action.title}\n${displayRecurrence(action.recurrence)} • ${action.durationSeconds / 60} мин"; textSize = 16f; setPadding(0, 8, 0, 8) }
            check.setOnCheckedChangeListener { _, checked -> viewModel.toggleAction(chain.id, action.id, checked) }
            check.setOnLongClickListener { confirmDeleteAction(chain, action); true }
            row.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("Изменить") { editActionDialog(chain, action) })
            root.addView(card(row))
        }
        root.addView(button("＋ Добавить действие") { newActionDialog(chain) })
        root.addView(button("Удалить цепочку") { confirmDeleteChain(chain) })
        addBottomNav(root, Tab.CHAINS)
        setContentView(root)
    }

    private fun newChainDialog() {
        val input = EditText(this).apply { hint = "Например: Утренняя рутина" }
        AlertDialog.Builder(this).setTitle("Новая цепочка").setView(input).setNegativeButton("Отмена", null).setPositiveButton("Создать") { _, _ ->
            input.text.toString().trim().takeIf(String::isNotEmpty)?.let(viewModel::addChain)
        }.show()
    }

    private fun newActionDialog(chain: Chain) {
        actionDialog(chain, null)
    }

    private fun editActionDialog(chain: Chain, action: Action) {
        actionDialog(chain, action)
    }

    private fun actionDialog(chain: Chain, existing: Action?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 0, 40, 0) }
        val title = EditText(this).apply { hint = "Например: Выпить стакан воды"; setText(existing?.title ?: "") }; box.addView(title)
        val duration = existing?.durationSeconds ?: 20 * 60
        val hours = EditText(this).apply { hint = "Часы"; inputType = InputType.TYPE_CLASS_NUMBER; setText((duration / 3600).toString()) }
        val minutes = EditText(this).apply { hint = "Минуты"; inputType = InputType.TYPE_CLASS_NUMBER; setText(((duration % 3600) / 60).toString()) }
        val seconds = EditText(this).apply { hint = "Секунды"; inputType = InputType.TYPE_CLASS_NUMBER; setText((duration % 60).toString()) }
        val durationRow = LinearLayout(this)
        durationRow.addView(hours, LinearLayout.LayoutParams(0, -2, 1f)); durationRow.addView(minutes, LinearLayout.LayoutParams(0, -2, 1f)); durationRow.addView(seconds, LinearLayout.LayoutParams(0, -2, 1f)); box.addView(durationRow)
        var selected = existing?.recurrence ?: "NONE"
        lateinit var recurrenceButton: MaterialButton
        recurrenceButton = button("Повторение: ${displayRecurrence(selected)}") { showRecurrenceDialog { value, summary -> selected = value; recurrenceButton.text = "Повторение: $summary" } }
        box.addView(recurrenceButton)
        val dialogTitle = if (existing == null) "Новое действие" else "Изменить действие"
        AlertDialog.Builder(this).setTitle(dialogTitle).setView(box).setNegativeButton("Отмена", null).setPositiveButton("Сохранить") { _, _ ->
            val totalSeconds = (hours.text.toString().toIntOrNull() ?: 0) * 3600 + (minutes.text.toString().toIntOrNull() ?: 0) * 60 + (seconds.text.toString().toIntOrNull() ?: 0)
            title.text.toString().trim().takeIf(String::isNotEmpty)?.let { name ->
                if (existing == null) viewModel.addAction(chain.id, name, selected, totalSeconds.coerceAtLeast(1))
                else viewModel.editAction(chain.id, existing.id, name, selected, totalSeconds.coerceAtLeast(1))
            }
        }.show()
    }

    private fun editChainDialog(chain: Chain) {
        val input = EditText(this).apply { setText(chain.name); hint = "Название цепочки" }
        AlertDialog.Builder(this).setTitle("Изменить цепочку").setView(input).setNegativeButton("Отмена", null).setPositiveButton("Сохранить") { _, _ -> input.text.toString().trim().takeIf(String::isNotEmpty)?.let { viewModel.renameChain(chain.id, it) } }.show()
    }

    private fun showRecurrenceDialog(onSelected: (String, String) -> Unit) {
        val types = arrayOf("Не повторять", "Каждый день", "Каждую неделю", "Каждый месяц", "Интервал")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 0, 40, 0) }
        val type = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, types) }
        val options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val summary = TextView(this).apply { setPadding(0, 20, 0, 0); textSize = 15f }
        var encoded = "NONE"
        root.addView(type); root.addView(options); root.addView(summary)

        fun refresh() {
            options.removeAllViews()
            when (type.selectedItem.toString()) {
                "Каждую неделю" -> {
                    val labels = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                    val checks = labels.map { CheckBox(this).apply { text = it; isChecked = it in listOf("Пн", "Ср", "Пт") } }
                    val row = LinearLayout(this); checks.forEach { row.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }; options.addView(row)
                    fun update() { val days = checks.filter { it.isChecked }.map { it.text }; encoded = "WEEKLY:${days.joinToString(",")}"; summary.text = "Повторять: ${days.joinToString(", ")}" }
                    checks.forEach { it.setOnCheckedChangeListener { _, _ -> update() } }; update()
                }
                "Каждый месяц" -> {
                    val days = EditText(this).apply { hint = "Числа месяца: 10, 25"; inputType = InputType.TYPE_CLASS_NUMBER; setText("1") }; options.addView(days)
                    encoded = "MONTHLY:1"; summary.text = "Повторять 1 числа каждого месяца"
                    days.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) { val value = days.text.toString().ifBlank { "1" }; encoded = "MONTHLY:$value"; summary.text = "Повторять $value числа каждого месяца" } }
                }
                "Интервал" -> {
                    val row = LinearLayout(this)
                    val count = EditText(this).apply { hint = "3"; inputType = InputType.TYPE_CLASS_NUMBER; setText("3") }
                    val units = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("дня", "недели", "месяца")) }
                    row.addView(count, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(units, LinearLayout.LayoutParams(0, -2, 1f)); options.addView(row)
                    encoded = "INTERVAL:3:DAYS"; summary.text = "Повторять каждые 3 дня"
                }
                "Каждый день" -> { encoded = "DAILY"; summary.text = "Повторять каждый день" }
                else -> { encoded = "NONE"; summary.text = "Однократное действие" }
            }
        }
        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refresh()
        }
        AlertDialog.Builder(this).setTitle("Повторение").setView(root).setNegativeButton("Отмена", null).setPositiveButton("Готово") { _, _ -> onSelected(encoded, summary.text.toString()) }.show()
    }

    private fun displayRecurrence(rule: String) = when {
        rule == "NONE" -> "Не повторяется"; rule == "DAILY" -> "Каждый день"; rule.startsWith("WEEKLY:") -> "Дни: ${rule.removePrefix("WEEKLY:").replace(",", ", ")}"; rule.startsWith("MONTHLY:") -> "Числа: ${rule.removePrefix("MONTHLY:")}"; rule.startsWith("INTERVAL:") -> "Интервал: ${rule.removePrefix("INTERVAL:").replace(":", " ")}"; else -> rule
    }

    private fun formatDuration(seconds: Long) = "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)

    private fun confirmDeleteAction(chain: Chain, action: Action) = AlertDialog.Builder(this).setTitle("Удалить действие?").setMessage(action.title).setNegativeButton("Отмена", null).setPositiveButton("Удалить") { _, _ -> viewModel.deleteAction(chain.id, action.id) }.show()
    private fun confirmDeleteChain(chain: Chain) = AlertDialog.Builder(this).setTitle("Удалить цепочку?").setMessage(chain.name).setNegativeButton("Отмена", null).setPositiveButton("Удалить") { _, _ -> viewModel.deleteChain(chain.id) }.show()
}
