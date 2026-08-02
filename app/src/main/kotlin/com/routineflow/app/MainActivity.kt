package com.routineflow.app

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.routineflow.app.model.Action
import com.routineflow.app.model.AppState
import com.routineflow.app.model.Chain
import com.routineflow.app.domain.RoutineDay
import com.routineflow.app.presentation.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private enum class Tab { RUN, CHAINS, STATS }
    private val viewModel: MainViewModel by viewModels()
    private var currentChainId: Long? = null
    private var currentTab = Tab.RUN
    private var executionChainId: Long? = null
    private val dateKey get() = RoutineDay.currentDateKey()
    private val navy = 0xff172554.toInt()
    private val accent = 0xff4f46e5.toInt()
    private var activeRunRoot: View? = null
    private var activeRunChainId: Long? = null
    private var activeRunActionId: Long? = null
    private var activeTimerText: TextView? = null
    private var activeProgress: ProgressBar? = null
    private var activePauseButton: MaterialButton? = null
    private var executionScreenRoot: View? = null
    private var executionScreenChainId: Long? = null
    private var completedChainsExpanded = false

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
        val executionChain = executionChainId?.let { id -> state.chains.firstOrNull { it.id == id } }
        if (executionChainId != null && executionChain == null) executionChainId = null
        if (executionChain != null) {
            if (state.running?.chainId == executionChain.id) {
                showChainRunning(state, executionChain)
            } else if (executionScreenRoot?.isAttachedToWindow == true && executionScreenChainId == executionChain.id) {
                return
            } else {
                showChainExecution(state, executionChain)
            }
            return
        }
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
        post { ViewCompat.requestApplyInsets(this) }
        addView(TextView(this@MainActivity).apply { text = title; textSize = 30f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(navy); setPadding(0, 0, 0, 20) })
    }

    private fun button(text: String, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; cornerRadius = 18; setTextSize(15f); setTextColor(0xffffffff.toInt())
        backgroundTintList = ColorStateList.valueOf(accent); insetTop = 6; insetBottom = 6
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(text: String, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; cornerRadius = 18; setTextSize(20f); setTextColor(0xff334155.toInt())
        backgroundTintList = ColorStateList.valueOf(0xffe2e8f0.toInt()); insetTop = 6; insetBottom = 6
        setOnClickListener { onClick() }
    }

    private fun bottomActionButton(text: String, primary: Boolean, textSizeSp: Float = 14f, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; cornerRadius = 8; setTextSize(textSizeSp); gravity = Gravity.CENTER; insetTop = 0; insetBottom = 0; minHeight = 144; elevation = 0f; stateListAnimator = null
        backgroundTintList = ColorStateList.valueOf(if (primary) accent else 0xffe2e8f0.toInt())
        setTextColor(if (primary) 0xffffffff.toInt() else 0xff334155.toInt())
        setOnClickListener { onClick() }
    }

    private fun card(content: View) = MaterialCardView(this).apply {
        radius = 22f; cardElevation = 0f; setCardBackgroundColor(0xffffffff.toInt()); setContentPadding(16, 8, 16, 8); addView(content)
    }

    private fun showToday(state: AppState) {
        val root = base(getString(R.string.run_title))
        val chainsToday = state.chains.filter { chain -> chain.actions.any { viewModel.isDue(it) } }
        val completedChains = chainsToday.filter { chain ->
            chain.actions.filter { viewModel.isDue(it) }.all { it.doneOn == dateKey }
        }
        val activeChains = chainsToday - completedChains.toSet()
        root.addView(TextView(this).apply {
            text = if (activeChains.isEmpty()) {
                if (completedChains.isEmpty()) getString(R.string.run_no_chains) else getString(R.string.run_no_active_chains)
            } else getString(R.string.run_subtitle)
            textSize = 15f; setPadding(0, 0, 0, 12)
        })

        fun addChainCard(chain: Chain) {
            val dueActions = chain.actions.filter { viewModel.isDue(it) }
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, 12, 4, 12) }
            row.addView(TextView(this).apply { text = getString(R.string.run_chain_meta, formatDuration(dueActions.sumOf { it.durationSeconds }.toLong()), dueActions.size); textSize = 13f; setTextColor(0xff64748b.toInt()) })
            val name = TextView(this).apply { text = chain.name; textSize = 20f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(navy); setPadding(0, 4, 0, 0) }
            row.addView(name)
            val open = {
                executionChainId = chain.id
                viewModel.stopAction()
                showChainExecution(state.copy(running = null), chain)
            }
            row.setOnClickListener { open() }; name.setOnClickListener { open() }
            root.addView(card(row).apply {
                radius = 22f; strokeWidth = 2; strokeColor = 0xffcbd5e1.toInt(); isClickable = true; isFocusable = true
                setOnClickListener { open() }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }
        activeChains.forEach(::addChainCard)

        if (completedChains.isNotEmpty()) {
            val completedHeader = TextView(this).apply {
                text = getString(if (completedChainsExpanded) R.string.run_completed_collapse else R.string.run_completed_expand, completedChains.size)
                textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(navy)
                setPadding(4, 20, 4, 12); isClickable = true; isFocusable = true
                setOnClickListener { completedChainsExpanded = !completedChainsExpanded; render(state) }
            }
            root.addView(completedHeader)
            if (completedChainsExpanded) completedChains.forEach(::addChainCard)
        }
        addBottomNav(root, Tab.RUN)
        setContentView(root)
    }

    private fun showChainExecution(state: AppState, chain: Chain) {
        val root = base(chain.name)
        val actions = chain.actions.filter { viewModel.isDue(it) }
        val totalSeconds = actions.sumOf { it.durationSeconds }.toLong()
        val now = java.util.Calendar.getInstance()
        val end = now.clone() as java.util.Calendar
        end.timeInMillis += totalSeconds * 1000L
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        root.addView(TextView(this).apply { text = getString(R.string.run_time_range, timeFormat.format(now.time), timeFormat.format(end.time)); textSize = 15f; setTextColor(0xff64748b.toInt()); setPadding(0, 0, 0, 20) })
        val allCompleted = actions.isNotEmpty() && actions.all { it.doneOn == dateKey }
        actions.forEachIndexed { index, action ->
            val completed = action.doneOn == dateKey
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = "${index + 1}. ${action.title}\n${formatDuration(action.durationSeconds.toLong())}"; textSize = 17f; setPadding(8, 12, 8, 12) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(circleToggle(completed, action.executionStatus == "SKIPPED") { checked, view ->
                styleCircleToggle(view, checked, false)
                val nextAllCompleted = actions.all { item ->
                    if (item.id == action.id) checked else item.doneOn == dateKey
                }
                if (nextAllCompleted != allCompleted) {
                    executionScreenRoot = null
                    executionScreenChainId = null
                }
                viewModel.toggleAction(chain.id, action.id, checked)
            }, LinearLayout.LayoutParams(48, 48).apply {
                marginStart = 12
            })
            root.addView(card(row).apply { setContentPadding(16, 10, 12, 10) })
        }
        val running = state.running?.takeIf { it.chainId == chain.id }
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 0); background = android.graphics.drawable.GradientDrawable().apply { setColor(0xffe2e8f0.toInt()) } }
        fun goBack() {
            executionScreenRoot = null
            executionScreenChainId = null
            executionChainId = null
            render(state)
        }
        if (allCompleted) {
            bottom.addView(bottomActionButton(getString(R.string.run_back), false, 16f, ::goBack), LinearLayout.LayoutParams(-1, 150))
        } else {
            val close = TextView(this).apply {
                text = "✕"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(0xff334155.toInt()); isClickable = true
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8f; setColor(0xffe2e8f0.toInt()) }
                setOnClickListener { goBack() }
            }
            bottom.addView(close, LinearLayout.LayoutParams(144, 150))
            val startLabel = if (running != null) formatDuration(running.remainingSeconds) else getString(R.string.run_start)
            bottom.addView(bottomActionButton(startLabel, true) {
                if (running == null && actions.any { it.doneOn != dateKey }) {
                    executionScreenRoot = null
                    executionScreenChainId = null
                    viewModel.startChain(chain.id)
                }
            }, LinearLayout.LayoutParams(0, 150, 1f))
        }
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f)); root.addView(bottom, LinearLayout.LayoutParams(-1, 158))
        executionScreenRoot = root
        executionScreenChainId = chain.id
        setContentView(root)
    }

    private fun showChainRunning(state: AppState, chain: Chain) {
        val current = state.running ?: return
        val action = chain.actions.firstOrNull { it.id == current.actionId } ?: return
        if (activeRunRoot?.isAttachedToWindow == true && activeRunChainId == chain.id && activeRunActionId == action.id) {
            updateRunningUi(current)
            return
        }
        activeRunRoot = null; activeRunChainId = null; activeRunActionId = null
        val actions = chain.actions.filter { viewModel.isDue(it) }
        val currentIndex = actions.indexOfFirst { it.id == action.id }
        val next = actions.drop(currentIndex + 1).firstOrNull { it.doneOn != dateKey }
        val root = base(chain.name)
        val remainingCount = actions.count { it.doneOn != dateKey }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 12) }
        header.addView(TextView(this).apply { text = getString(R.string.running_remaining, remainingCount); textSize = 14f; setTextColor(0xff64748b.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.forEach { step ->
            val color = when {
                step.doneOn == dateKey && step.executionStatus == "SKIPPED" -> 0xffef4444.toInt()
                step.doneOn == dateKey -> 0xff22c55e.toInt()
                else -> accent
            }
            header.addView(TextView(this).apply { text = "●"; textSize = 18f; setTextColor(color); setPadding(2, 0, 2, 0) })
        }
        header.addView(TextView(this).apply { text = "✕"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(0xff334155.toInt()); setPadding(12, 0, 0, 0); isClickable = true; setOnClickListener { viewModel.stopAction(); executionChainId = null; showToday(state.copy(running = null)) } })
        root.addView(header)
        val planned = formatAdaptive(current.totalSeconds)
        val currentCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 18) }
        currentCard.addView(TextView(this).apply { text = getString(R.string.running_duration, planned); textSize = 14f; setTextColor(0xff64748b.toInt()) })
        currentCard.addView(TextView(this).apply { text = action.title; textSize = 26f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(navy); setPadding(0, 10, 0, 10) })
        val timerText = TextView(this).apply { text = formatAdaptive(current.elapsedSeconds); textSize = 36f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(if (current.overtime) 0xffef4444.toInt() else accent); gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 8, 0, 14) }
        currentCard.addView(timerText)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = current.totalSeconds.coerceAtLeast(1).toInt(); progress = current.elapsedSeconds.coerceAtMost(current.totalSeconds).toInt(); progressTintList = ColorStateList.valueOf(if (current.overtime) 0xffef4444.toInt() else accent); progressBackgroundTintList = ColorStateList.valueOf(0xffe2e8f0.toInt()) }
        currentCard.addView(progress, LinearLayout.LayoutParams(-1, 20))
        root.addView(card(currentCard).apply { radius = 24f; strokeWidth = 2; strokeColor = accent })
        root.addView(secondaryButton(getString(R.string.running_reset)) { viewModel.resetCurrentTimer() })
        root.addView(secondaryButton(getString(R.string.running_postpone)) { viewModel.postponeCurrent() })
        val nextCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16) }
        nextCard.addView(TextView(this).apply { text = getString(R.string.running_next); textSize = 14f; setTextColor(0xff64748b.toInt()) })
        nextCard.addView(TextView(this).apply { text = next?.title ?: getString(R.string.running_last_step); textSize = 20f; setTextColor(navy); setPadding(0, 6, 0, 0) })
        root.addView(card(nextCard))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 0) }
        bottom.addView(TextView(this).apply { text = getString(R.string.running_end, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(current.estimatedEndMillis))); textSize = 14f; gravity = Gravity.CENTER; setTextColor(0xff64748b.toInt()); setPadding(0, 0, 0, 8) })
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        lateinit var pauseButton: MaterialButton
        pauseButton = controlButton(if (current.paused) getString(R.string.running_resume) else getString(R.string.running_pause), false) { viewModel.pauseResume() }
        controls.addView(pauseButton, LinearLayout.LayoutParams(0, 150, 1f))
        controls.addView(controlButton("✓", true, 28f) { viewModel.completeCurrent() }, LinearLayout.LayoutParams(0, 150, 1f))
        controls.addView(controlButton(getString(R.string.running_skip), false) { viewModel.skipCurrent() }, LinearLayout.LayoutParams(0, 150, 1f))
        bottom.addView(controls); root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f)); root.addView(bottom, LinearLayout.LayoutParams(-1, 210))
        activeRunRoot = root; activeRunChainId = chain.id; activeRunActionId = action.id; activeTimerText = timerText; activeProgress = progress; activePauseButton = pauseButton
        setContentView(root)
    }

    private fun updateRunningUi(current: com.routineflow.app.model.RunningAction) {
        activeTimerText?.text = formatAdaptive(current.elapsedSeconds)
        activeTimerText?.setTextColor(if (current.overtime) 0xffef4444.toInt() else accent)
        activeProgress?.progress = current.elapsedSeconds.coerceAtMost(current.totalSeconds).toInt()
        activeProgress?.progressTintList = ColorStateList.valueOf(if (current.overtime) 0xffef4444.toInt() else accent)
        activePauseButton?.text = if (current.paused) getString(R.string.running_resume) else getString(R.string.running_pause)
    }

    private fun controlButton(text: String, primary: Boolean, textSizeSp: Float = 14f, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; textSize = textSizeSp; cornerRadius = 8; insetTop = 0; insetBottom = 0; elevation = 0f; stateListAnimator = null
        backgroundTintList = ColorStateList.valueOf(if (primary) accent else 0xffe2e8f0.toInt()); setTextColor(if (primary) 0xffffffff.toInt() else 0xff334155.toInt()); setOnClickListener { onClick() }
    }

    private fun circleToggle(checked: Boolean, skipped: Boolean, onToggle: (Boolean, ImageView) -> Unit) = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER
        imageTintList = ColorStateList.valueOf(0xffffffff.toInt())
        isClickable = true; isFocusable = true
        styleCircleToggle(this, checked, skipped)
        setOnClickListener {
            val nextChecked = !isSelected
            isSelected = nextChecked
            onToggle(nextChecked, this)
        }
    }

    private fun styleCircleToggle(view: ImageView, checked: Boolean, skipped: Boolean) {
        view.isSelected = checked
        if (checked) view.setImageResource(R.drawable.ic_check) else view.setImageDrawable(null)
        view.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(when { skipped -> 0xffef4444.toInt(); checked -> 0xff22c55e.toInt(); else -> 0xffdbeafe.toInt() })
            setStroke(2, when { skipped -> 0xffdc2626.toInt(); checked -> 0xff16a34a.toInt(); else -> accent })
        }
    }

    private fun formatAdaptive(seconds: Long): String = when {
        seconds >= 3600 -> "%02d:%02d:%02d".format(Locale.US, seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        seconds >= 60 -> "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)
        else -> "%02d".format(Locale.US, seconds)
    }

    private fun showChains(state: AppState) {
        val root = base(getString(R.string.tab_chains))
        root.addView(TextView(this).apply { text = getString(R.string.chains_subtitle); textSize = 16f; setPadding(0, 12, 0, 8) })
        state.chains.forEach { chain -> root.addView(button(chain.name) { executionChainId = null; currentChainId = chain.id; render(state) }) }
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
                setOnClickListener { currentTab = tab; currentChainId = null; executionChainId = null; render(viewModel.state.value) }
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
