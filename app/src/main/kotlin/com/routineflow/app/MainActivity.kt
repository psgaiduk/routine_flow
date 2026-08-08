package com.routineflow.app

import android.app.AlertDialog
import android.Manifest
import android.content.ClipData
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.text.InputFilter
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var dragSourceView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
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

    private fun base(title: String, showTitle: Boolean = true): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; val baseTop = 28; val baseBottom = 24; setPadding(24, baseTop, 24, baseBottom); setBackgroundColor(0xfff8fafc.toInt())
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(24, baseTop + bars.top, 24, baseBottom + bars.bottom)
            insets
        }
        post { ViewCompat.requestApplyInsets(this) }
        if (showTitle) addView(TextView(this@MainActivity).apply { text = title; textSize = 30f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(navy); setPadding(0, 0, 0, 20) })
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
        state.chains.forEach { chain ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, 12, 4, 12) }
            row.addView(TextView(this).apply {
                text = getString(R.string.run_chain_meta, formatDuration(chain.actions.sumOf { it.durationSeconds }.toLong()), chain.actions.size)
                textSize = 13f; setTextColor(0xff64748b.toInt())
            })
            row.addView(TextView(this).apply {
                text = chain.name; textSize = 20f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(navy); setPadding(0, 4, 0, 0)
            })
            val open = { executionChainId = null; currentChainId = chain.id; render(state) }
            row.setOnClickListener { open() }
            root.addView(card(row).apply {
                radius = 22f; strokeWidth = 2; strokeColor = 0xffcbd5e1.toInt(); isClickable = true; isFocusable = true
                setOnClickListener { open() }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }
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
        val root = base("", showTitle = false)
        val nameField = EditText(this).apply {
            setText(chain.name); textSize = 30f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(navy)
            setSingleLine(true); setSelectAllOnFocus(false); setPadding(0, 0, 0, 20); background = null
            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveChainName(chain, text.toString()) }
            setOnEditorActionListener { _, _, _ -> clearFocus(); true }
        }
        root.addView(nameField)
        if (chain.actions.isEmpty()) root.addView(TextView(this).apply { text = "В цепочке пока нет действий"; setPadding(0, 20, 0, 10) })
        chain.actions.forEachIndexed { index, action ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8, 12, 8, 12); isClickable = true; isFocusable = true }
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(TextView(this).apply {
                text = "${index + 1}. ${action.title}"; textSize = 17f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(navy)
            })
            details.addView(TextView(this).apply {
                text = "${displayRecurrence(action.recurrence)} • ${formatDuration(action.durationSeconds.toLong())}"; textSize = 14f; setTextColor(0xff64748b.toInt()); setPadding(0, 4, 0, 0)
            })
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            val dragHandle = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_sort_by_size); imageTintList = ColorStateList.valueOf(0xff94a3b8.toInt())
                contentDescription = "Изменить порядок"; setPadding(8, 8, 4, 8)
            }
            dragHandle.setOnLongClickListener { beginActionDrag(dragHandle, action) }
            row.addView(dragHandle, LinearLayout.LayoutParams(44, 44))
            row.setOnClickListener { editActionDialog(chain, action) }
            row.setOnLongClickListener { beginActionDrag(row, action) }
            val insertionMarker = View(this).apply {
                setBackgroundColor(accent)
                visibility = View.GONE
            }
            val cardContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(insertionMarker, LinearLayout.LayoutParams(-1, 4))
                addView(row)
            }
            row.setOnDragListener { view, event -> actionDragEvent(chain, action, view, insertionMarker, event) }
            val actionCard = card(cardContent).apply {
                setOnClickListener { editActionDialog(chain, action) }
                setOnLongClickListener { beginActionDrag(this, action) }
                setOnDragListener { view, event -> actionDragEvent(chain, action, view, insertionMarker, event) }
            }
            root.addView(actionCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        }
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(chainSettingsBar(state, chain), LinearLayout.LayoutParams(-1, 158))
        setContentView(root)
    }

    private fun beginActionDrag(source: View, action: Action): Boolean {
        val dragData = ClipData.newPlainText("action_id", action.id.toString())
        dragSourceView = source
        source.alpha = 0.45f
        Toast.makeText(this, getString(R.string.drag_hint), Toast.LENGTH_SHORT).show()
        @Suppress("DEPRECATION")
        return source.startDrag(dragData, View.DragShadowBuilder(source), null, 0)
    }

    private fun actionDragEvent(chain: Chain, targetAction: Action, targetView: View, insertionMarker: View, event: android.view.DragEvent): Boolean = when (event.action) {
        android.view.DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("text/plain") == true
        android.view.DragEvent.ACTION_DRAG_ENTERED -> {
            if (targetView is MaterialCardView) targetView.strokeColor = accent
            insertionMarker.visibility = View.VISIBLE
            true
        }
        android.view.DragEvent.ACTION_DRAG_EXITED -> {
            if (targetView is MaterialCardView) targetView.strokeColor = 0xffcbd5e1.toInt()
            insertionMarker.visibility = View.GONE
            true
        }
        android.view.DragEvent.ACTION_DROP -> {
            val fromId = event.clipData.getItemAt(0).text.toString().toLongOrNull()
            if (fromId != null && fromId != targetAction.id) viewModel.moveAction(chain.id, fromId, targetAction.id)
            if (targetView is MaterialCardView) targetView.strokeColor = 0xffcbd5e1.toInt()
            insertionMarker.visibility = View.GONE
            true
        }
        android.view.DragEvent.ACTION_DRAG_ENDED -> {
            if (targetView is MaterialCardView) targetView.strokeColor = 0xffcbd5e1.toInt()
            insertionMarker.visibility = View.GONE
            dragSourceView?.alpha = 1f
            dragSourceView = null
            true
        }
        else -> false
    }

    private fun saveChainName(chain: Chain, value: String) {
        value.trim().takeIf(String::isNotEmpty)?.takeIf { it != chain.name }?.let { viewModel.renameChain(chain.id, it) }
    }

    private fun chainSettingsBar(state: AppState, chain: Chain): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 0)
        background = android.graphics.drawable.GradientDrawable().apply { setColor(0xffe2e8f0.toInt()) }

        fun item(iconRes: Int, label: String, primary: Boolean, onClick: () -> Unit) = MaterialButton(this@MainActivity).apply {
            text = label; isAllCaps = false; textSize = if (label.isEmpty()) 20f else 14f
            gravity = Gravity.CENTER; cornerRadius = 8; minHeight = 150; minWidth = 0
            insetTop = 0; insetBottom = 0; elevation = 0f; stateListAnimator = null
            icon = getDrawable(iconRes); iconGravity = if (label.isEmpty()) MaterialButton.ICON_GRAVITY_TEXT_START else MaterialButton.ICON_GRAVITY_TEXT_TOP
            iconSize = if (label.isEmpty()) 42 else 36; iconPadding = 10
            backgroundTintList = ColorStateList.valueOf(if (primary) accent else android.graphics.Color.TRANSPARENT)
            iconTint = ColorStateList.valueOf(if (primary) 0xffffffff.toInt() else 0xff334155.toInt())
            setTextColor(if (primary) 0xffffffff.toInt() else 0xff334155.toInt())
            setOnClickListener { onClick() }
        }

        addView(item(R.drawable.ic_arrow_back, "", false) {
            currentChainId = null; currentTab = Tab.CHAINS; render(state)
        }, LinearLayout.LayoutParams(0, 150, 1f))
        addView(item(R.drawable.ic_add, getString(R.string.add_action), true) {
            newActionDialog(chain)
        }, LinearLayout.LayoutParams(0, 150, 2f))
        addView(item(R.drawable.ic_delete, "", false) {
            confirmDeleteChain(chain)
        }, LinearLayout.LayoutParams(0, 150, 1f))
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
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 8) }
        fun input(value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
            setText(value); inputType = type; setSingleLine(true); textSize = 16f
        }
        box.addView(editorHeader(getString(R.string.action_name)))
        val title = input(existing?.title ?: "").apply {
            background = null
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
        }
        val titleField = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title, LinearLayout.LayoutParams(-1, 48))
            addView(View(this@MainActivity).apply {
                setBackgroundColor(0xff94a3b8.toInt())
            }, LinearLayout.LayoutParams(-1, 1))
        }
        box.addView(titleField, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 })
        val duration = existing?.durationSeconds ?: 20 * 60
        val durationRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val hours = CompactPicker(this, 0, 2, (duration / 3600).coerceAtMost(2), true)
        val minutes = CompactPicker(this, 0, 59, (duration % 3600) / 60, true)
        val seconds = CompactPicker(this, 0, 59, duration % 60, true)
        val hourColumn = durationColumn(hours)
        val minuteColumn = durationColumn(minutes)
        val secondColumn = durationColumn(seconds)
        durationRow.addView(hourColumn, LinearLayout.LayoutParams(0, 190, 1f).apply { marginEnd = 8 })
        durationRow.addView(TextView(this).apply { text = ":"; textSize = 24f; setTypeface(typeface, android.graphics.Typeface.NORMAL); setTextColor(0xff64748b.toInt()); gravity = Gravity.CENTER; setPadding(0, 0, 0, 22) }, LinearLayout.LayoutParams(18, 190))
        durationRow.addView(minuteColumn, LinearLayout.LayoutParams(0, 190, 1f).apply { marginStart = 8; marginEnd = 8 })
        durationRow.addView(TextView(this).apply { text = ":"; textSize = 24f; setTypeface(typeface, android.graphics.Typeface.NORMAL); setTextColor(0xff64748b.toInt()); gravity = Gravity.CENTER; setPadding(0, 0, 0, 22) }, LinearLayout.LayoutParams(18, 190))
        durationRow.addView(secondColumn, LinearLayout.LayoutParams(0, 190, 1f).apply { marginStart = 8 })
        val durationSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 12)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 18f
                setColor(0xfff8fafc.toInt())
                setStroke(1, 0xffe2e8f0.toInt())
            }
        }
        box.addView(editorHeader(getString(R.string.duration_label)))
        durationSection.addView(durationRow, LinearLayout.LayoutParams(-1, 190))
        box.addView(durationSection, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        box.addView(durationLabelsRow(), LinearLayout.LayoutParams(-1, 42).apply { bottomMargin = 18 })
        var selected = existing?.recurrence ?: "NONE"
        box.addView(recurrenceEditorV2(selected) { selected = it })
        MaterialAlertDialogBuilder(this).setView(ScrollView(this).apply { addView(box) }).setNegativeButton(R.string.cancel, null)
            .apply { if (existing != null) setNeutralButton(R.string.delete) { _, _ -> confirmDeleteAction(chain, existing) } }
            .setPositiveButton(R.string.save) { _, _ ->
            val totalSeconds = hours.value * 3600 + minutes.value * 60 + seconds.value
            title.text.toString().trim().takeIf(String::isNotEmpty)?.let { name ->
                if (existing == null) viewModel.addAction(chain.id, name, selected, totalSeconds.coerceAtLeast(1))
                else viewModel.editAction(chain.id, existing.id, name, selected, totalSeconds.coerceAtLeast(1))
            }
        }.show()
    }

    private fun editorHeader(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTypeface(typeface, android.graphics.Typeface.NORMAL)
        setTextColor(navy)
        setPadding(0, 0, 0, 12)
    }

    private fun durationColumn(picker: CompactPicker): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(picker, LinearLayout.LayoutParams(-1, 174))
    }

    private class CompactPicker(
        context: android.content.Context,
        private val min: Int,
        private val max: Int,
        initial: Int,
        private val wrap: Boolean
    ) : LinearLayout(context) {
        private var selected = initial.coerceIn(min, max)
        private var startY = 0f
        private var lastY = 0f
        private var moved = false
        private val values = listOf(TextView(context), TextView(context), TextView(context))

        var value: Int
            get() = selected
            set(newValue) { selected = newValue.coerceIn(min, max); render() }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            values.forEach { view ->
                view.textSize = 20f
                view.gravity = Gravity.CENTER
                view.setTextColor(0xff172554.toInt())
                view.typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)
                addView(view, LayoutParams(-1, 56))
            }
            render()
            setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> { startY = event.y; lastY = event.y; moved = false; true }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        var distance = event.y - lastY
                        while (kotlin.math.abs(distance) >= 28f) {
                            change(if (distance < 0) 1 else -1)
                            lastY += if (distance < 0) -28f else 28f
                            distance = event.y - lastY
                            moved = true
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (!moved && kotlin.math.abs(event.y - startY) < 12f) {
                            if (event.y < height / 3f) change(-1) else if (event.y > height * 2f / 3f) change(1)
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

    private fun durationLabelsRow(): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        fun label(text: String) = TextView(this@MainActivity).apply { this.text = text; textSize = 14f; setTextColor(0xff64748b.toInt()); gravity = Gravity.CENTER }
        addView(label(getString(R.string.hours_short)), LinearLayout.LayoutParams(0, 42, 1f))
        addView(View(this@MainActivity), LinearLayout.LayoutParams(18, 42))
        addView(label(getString(R.string.minutes_short)), LinearLayout.LayoutParams(0, 42, 1f))
        addView(View(this@MainActivity), LinearLayout.LayoutParams(18, 42))
        addView(label(getString(R.string.seconds_short)), LinearLayout.LayoutParams(0, 42, 1f))
    }

    private fun recurrenceEditorV2(initial: String, onChanged: (String) -> Unit): LinearLayout {
        val units = arrayOf(getString(R.string.unit_days), getString(R.string.unit_weeks), getString(R.string.unit_months))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 22, 0, 0) }
        root.addView(editorHeader(getString(R.string.repeat_title)))
        val unitPicker = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, units) }
        val count = EditText(this).apply {
            setTextSize(15f); gravity = Gravity.CENTER; setTextColor(navy); setTypeface(null, android.graphics.Typeface.NORMAL)
            inputType = InputType.TYPE_CLASS_NUMBER; filters = arrayOf(InputFilter.LengthFilter(2)); setSingleLine(true); setSelectAllOnFocus(true); setText("1")
            isEnabled = true; isFocusableInTouchMode = true; minWidth = 48; minHeight = 44; setPadding(4, 0, 4, 0)
            background = null
        }
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 18, 0, 0) }
        val summary = TextView(this).apply { textSize = 14f; setTextColor(0xff64748b.toInt()); setPadding(0, 10, 0, 0) }
        root.addView(details); root.addView(summary)

        val initialParts = initial.split(":")
        val initialUnit = when {
            initial.startsWith("WEEKLY:") -> 1
            initial.startsWith("MONTHLY:") -> 2
            initial.startsWith("INTERVAL:") -> when (initialParts.getOrNull(2)) { "WEEKS" -> 1; "MONTHS" -> 2; else -> 0 }
            else -> 0
        }
        var amount = when {
            initial == "DAILY" -> 1
            initial.startsWith("WEEKLY:") || initial.startsWith("MONTHLY:") -> 1
            initial.startsWith("INTERVAL:") -> initialParts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            else -> 1
        }
        count.setText(amount.toString())
        fun dayChips(selectedCodes: Set<String>, onSelectionChanged: (Set<String>) -> Unit): View {
            val codes = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            val labels = arrayOf(R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun).map(::getString)
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, 18, 0, 8); layoutParams = LinearLayout.LayoutParams(-1, -2) }
            lateinit var views: List<TextView>
            views = codes.mapIndexed { index, code -> TextView(this).apply {
                text = labels[index]; tag = code; gravity = Gravity.CENTER; textSize = 12f; isSelected = code in selectedCodes; isClickable = true
                fun style() { setTextColor(if (isSelected) 0xffffffff.toInt() else navy); background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(if (isSelected) accent else 0xffe2e8f0.toInt()); setStroke(2, if (isSelected) accent else 0xffcbd5e1.toInt()) } }
                style(); setOnClickListener { isSelected = !isSelected; style(); onSelectionChanged(views.filter { it.isSelected }.map { it.tag.toString() }.toSet()) }
            } }
            views.forEach { row.addView(it, LinearLayout.LayoutParams(0, 46, 1f).apply { marginStart = 3; marginEnd = 3 }) }
            row.post {
                val circleSize = ((row.width - 42) / 7).coerceAtLeast(40)
                views.forEach { view ->
                    view.layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply { marginStart = 3; marginEnd = 3 }
                }
            }
            return row
        }
        var monthMode = if (initial.contains(":WEEKDAY:")) 1 else 0
        var monthDays = when {
            initial.startsWith("MONTHLY:") -> initial.removePrefix("MONTHLY:").ifBlank { "1" }
            initial.contains(":DATE:") -> initial.substringAfter(":DATE:").ifBlank { "1" }
            else -> "1"
        }
        var weekdayPosition = initial.substringAfter(":WEEKDAY:", "FIRST").split(":").firstOrNull() ?: "FIRST"
        var weekday = initial.substringAfterLast(":").takeIf { it in setOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс") } ?: "Пн"
        fun renderDetails() {
            details.removeAllViews()
            val unitIndex = unitPicker.selectedItemPosition
            if (unitIndex == 0) { summary.text = getString(R.string.repeat_interval_summary, amount, getString(R.string.unit_days)); onChanged("INTERVAL:$amount:DAYS"); return }
            if (unitIndex == 1) {
                val saved = if (initial.startsWith("WEEKLY:")) initial.removePrefix("WEEKLY:").split(",").toSet() else setOf("Пн")
                details.addView(dayChips(saved) { selectedCodes -> summary.text = getString(R.string.repeat_days_summary, localizedDays(selectedCodes)); onChanged("INTERVAL:$amount:WEEKS:WEEKDAYS:${selectedCodes.joinToString(",")}") })
                summary.text = getString(R.string.repeat_days_summary, localizedDays(saved)); onChanged("INTERVAL:$amount:WEEKS:WEEKDAYS:${saved.joinToString(",")}"); return
            }
            if (unitIndex == 2) {
                val mode = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
                val numbers = RadioButton(this).apply { id = View.generateViewId(); text = getString(R.string.repeat_specific_number); isChecked = monthMode == 0 }
                val weekdayMode = RadioButton(this).apply { id = View.generateViewId(); text = getString(R.string.repeat_weekday_mode); isChecked = monthMode == 1 }
                mode.addView(numbers); mode.addView(weekdayMode); details.addView(mode)
                val monthDetails = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 6, 0, 0) }; details.addView(monthDetails)
                fun renderMonthDetails() {
                    monthMode = if (weekdayMode.isChecked) 1 else 0; monthDetails.removeAllViews()
                    if (monthMode == 0) {
                        val input = EditText(this).apply { hint = getString(R.string.month_days_hint); setText(monthDays); inputType = InputType.TYPE_CLASS_NUMBER }
                        monthDetails.addView(input); input.addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { monthDays = s?.toString().orEmpty(); summary.text = getString(R.string.repeat_month_summary, monthDays); onChanged("INTERVAL:$amount:MONTHS:DATE:$monthDays") }; override fun afterTextChanged(s: android.text.Editable?) = Unit }); summary.text = getString(R.string.repeat_month_summary, monthDays); onChanged("INTERVAL:$amount:MONTHS:DATE:$monthDays")
                    } else {
                        val positionCodes = arrayOf("FIRST", "SECOND", "THIRD", "FOURTH", "LAST")
                        val positions = arrayOf(R.string.position_first, R.string.position_second, R.string.position_third, R.string.position_fourth, R.string.position_last).map(::getString)
                        val positionPicker = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, positions); setSelection(positionCodes.indexOf(weekdayPosition).coerceAtLeast(0)) }
                        fun refreshWeekday() { val positionIndex = positionPicker.selectedItemPosition.coerceIn(0, positionCodes.lastIndex); weekdayPosition = positionCodes[positionIndex]; summary.text = getString(R.string.repeat_weekday_summary, positions[positionIndex], getString(dayResource(weekday))); onChanged("INTERVAL:$amount:MONTHS:WEEKDAY:$weekdayPosition:$weekday") }
                        positionPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) = Unit; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refreshWeekday() }
                        monthDetails.addView(positionPicker); monthDetails.addView(dayChips(setOf(weekday)) { selected -> weekday = selected.firstOrNull() ?: "Пн"; refreshWeekday() }); refreshWeekday()
                    }
                }
                numbers.setOnClickListener { monthMode = 0; numbers.isChecked = true; weekdayMode.isChecked = false; renderMonthDetails() }
                weekdayMode.setOnClickListener { monthMode = 1; numbers.isChecked = false; weekdayMode.isChecked = true; renderMonthDetails() }
                mode.setOnCheckedChangeListener { _, checkedId ->
                    if (checkedId == numbers.id) monthMode = 0
                    if (checkedId == weekdayMode.id) monthMode = 1
                }
                renderMonthDetails(); return
            }
        }
        val frequencyRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply { text = getString(R.string.repeat_every_short); textSize = 16f; setTextColor(navy) }, LinearLayout.LayoutParams(-2, 52))
            val countField = FrameLayout(this@MainActivity).apply {
                addView(count, FrameLayout.LayoutParams(-1, 52))
                addView(View(this@MainActivity).apply { setBackgroundColor(0xff94a3b8.toInt()) }, FrameLayout.LayoutParams(-1, 1, Gravity.BOTTOM))
            }
            addView(countField, LinearLayout.LayoutParams(58, 52).apply { marginStart = 8 })
            addView(unitPicker, LinearLayout.LayoutParams(0, 52, 1f).apply { marginStart = 10 })
        }
        root.addView(frequencyRow, 1)
        unitPicker.setSelection(initialUnit)
        count.setText(amount.toString())
        count.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { amount = s?.toString()?.toIntOrNull()?.coerceIn(1, 99) ?: 1; renderDetails() }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        unitPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) = Unit; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = renderDetails() }; unitPicker.post { renderDetails() }
        return root
    }

    private fun dayResource(code: String): Int = mapOf("Пн" to R.string.day_mon, "Вт" to R.string.day_tue, "Ср" to R.string.day_wed, "Чт" to R.string.day_thu, "Пт" to R.string.day_fri, "Сб" to R.string.day_sat, "Вс" to R.string.day_sun)[code] ?: R.string.day_mon

    private fun localizedDays(codes: Collection<String>): String = codes.joinToString(", ") { getString(dayResource(it)) }

    private fun recurrenceEditor(initial: String, onChanged: (String) -> Unit): LinearLayout {
        val types = arrayOf(R.string.repeat_none, R.string.repeat_daily, R.string.repeat_weekly, R.string.repeat_monthly, R.string.repeat_interval).map(::getString).toTypedArray()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
        root.addView(TextView(this).apply { text = getString(R.string.repeat_title); textSize = 13f; setTextColor(0xff64748b.toInt()) })
        val type = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, types) }
        val options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 0) }
        val summary = TextView(this).apply { textSize = 14f; setTextColor(0xff64748b.toInt()); setPadding(0, 8, 0, 0) }
        root.addView(type); root.addView(options); root.addView(summary)
        val initialPosition = when {
            initial == "DAILY" -> 1
            initial.startsWith("WEEKLY:") -> 2
            initial.startsWith("MONTHLY:") -> 3
            initial.startsWith("INTERVAL:") -> 4
            else -> 0
        }
        var encoded = initial
        fun update(value: String, text: String) { encoded = value; summary.text = text; onChanged(encoded) }
        fun refresh() {
            options.removeAllViews()
            when (type.selectedItemPosition) {
                1 -> update("DAILY", getString(R.string.repeat_daily_summary))
                2 -> {
                    val codes = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                    val labels = arrayOf(R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun).map(::getString)
                    val saved = initial.removePrefix("WEEKLY:").split(",").filter(String::isNotBlank).toSet()
                    lateinit var refreshWeek: () -> Unit
                    val checks = labels.mapIndexed { index, label ->
                        TextView(this).apply {
                            text = label; tag = codes[index]; gravity = Gravity.CENTER; textSize = 12f; isClickable = true; isFocusable = true
                            isSelected = if (saved.isEmpty()) codes[index] in setOf("Пн", "Ср", "Пт") else codes[index] in saved
                            minimumWidth = 42; minimumHeight = 42; setPadding(0, 0, 0, 0)
                            fun style() {
                                setTextColor(if (isSelected) 0xffffffff.toInt() else navy)
                                background = android.graphics.drawable.GradientDrawable().apply {
                                    shape = android.graphics.drawable.GradientDrawable.OVAL
                                    setColor(if (isSelected) accent else 0xffe2e8f0.toInt())
                                    setStroke(2, if (isSelected) accent else 0xffcbd5e1.toInt())
                                }
                            }
                            style()
                            setOnClickListener { isSelected = !isSelected; style(); refreshWeek() }
                        }
                    }
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, 4, 0, 4) }
                    checks.forEach { row.addView(it, LinearLayout.LayoutParams(0, 48, 1f).apply { marginEnd = 5 }) }; options.addView(row)
                    refreshWeek = { val days = checks.filter { it.isSelected }.map { it.tag.toString() }; val visibleDays = checks.filter { it.isSelected }.map { it.text }; update("WEEKLY:${days.joinToString(",")}", getString(R.string.repeat_days_summary, visibleDays.joinToString(", "))) }
                    refreshWeek()
                }
                3 -> {
                    val value = EditText(this).apply { hint = getString(R.string.month_days_hint); inputType = InputType.TYPE_CLASS_NUMBER; setText(initial.removePrefix("MONTHLY:").ifBlank { "1" }) }
                    options.addView(value)
                    fun refreshMonth() { val days = value.text.toString().ifBlank { "1" }; update("MONTHLY:$days", getString(R.string.repeat_month_summary, days)) }
                    value.addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshMonth(); override fun afterTextChanged(s: android.text.Editable?) = Unit }); refreshMonth()
                }
                4 -> {
                    val parts = initial.split(":")
                    val count = EditText(this).apply { hint = getString(R.string.repeat_count); inputType = InputType.TYPE_CLASS_NUMBER; setText(parts.getOrNull(1) ?: "3") }
                    val units = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf(R.string.unit_days, R.string.unit_weeks, R.string.unit_months).map(::getString)) }
                    val row = LinearLayout(this); row.addView(count, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(units, LinearLayout.LayoutParams(0, -2, 1f)); options.addView(row)
                    fun refreshInterval() { val n = count.text.toString().ifBlank { "1" }; val unit = when (units.selectedItemPosition) { 1 -> "WEEKS"; 2 -> "MONTHS"; else -> "DAYS" }; update("INTERVAL:$n:$unit", getString(R.string.repeat_interval_summary, n, units.selectedItem)) }
                    count.addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshInterval(); override fun afterTextChanged(s: android.text.Editable?) = Unit }); units.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) = Unit; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refreshInterval() }; refreshInterval()
                }
                else -> update("NONE", getString(R.string.repeat_none_summary))
            }
        }
        type.setSelection(initialPosition)
        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) = Unit; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refresh() }
        type.post { refresh() }
        return root
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

    private fun displayRecurrence(rule: String): String = when {
        rule == "NONE" -> getString(R.string.repeat_none_summary)
        rule == "DAILY" -> getString(R.string.repeat_daily_summary)
        rule.startsWith("WEEKLY:") -> {
            val labels = rule.removePrefix("WEEKLY:").split(",").filter(String::isNotBlank).map { code ->
                getString(mapOf("Пн" to R.string.day_mon, "Вт" to R.string.day_tue, "Ср" to R.string.day_wed, "Чт" to R.string.day_thu, "Пт" to R.string.day_fri, "Сб" to R.string.day_sat, "Вс" to R.string.day_sun)[code] ?: R.string.day_mon)
            }
            getString(R.string.repeat_days_summary, labels.joinToString(", "))
        }
        rule.startsWith("MONTHLY:") -> getString(R.string.repeat_month_summary, rule.removePrefix("MONTHLY:"))
        rule.startsWith("INTERVAL:") -> {
            val parts = rule.split(":")
            val unit = when (parts.getOrNull(2)) { "WEEKS" -> R.string.unit_weeks; "MONTHS" -> R.string.unit_months; else -> R.string.unit_days }
            getString(R.string.repeat_interval_summary, parts.getOrNull(1) ?: "1", getString(unit))
        }
        else -> rule
    }

    private fun formatDuration(seconds: Long) = "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)

    private fun confirmDeleteAction(chain: Chain, action: Action) = AlertDialog.Builder(this).setTitle("Удалить действие?").setMessage(action.title).setNegativeButton("Отмена", null).setPositiveButton("Удалить") { _, _ -> viewModel.deleteAction(chain.id, action.id) }.show()
    private fun confirmDeleteChain(chain: Chain) = AlertDialog.Builder(this).setTitle("Удалить цепочку?").setMessage(chain.name).setNegativeButton("Отмена", null).setPositiveButton("Удалить") { _, _ -> viewModel.deleteChain(chain.id) }.show()
}
