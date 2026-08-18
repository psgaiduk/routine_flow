package com.routineflow.app

import android.app.AlertDialog
import android.Manifest
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
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
import com.routineflow.app.presentation.AppTab
import com.routineflow.app.presentation.MainViewModel
import com.routineflow.app.presentation.RecurrenceDisplayFormatter
import com.routineflow.app.presentation.ScreenLayout
import com.routineflow.app.presentation.TimeFormatter
import com.routineflow.app.presentation.components.ActionEditorDialog
import com.routineflow.app.presentation.components.ChainDialogFactory
import com.routineflow.app.presentation.components.ReorderDragController
import com.routineflow.app.presentation.screens.ChainsScreen
import com.routineflow.app.presentation.screens.ChainEditorScreen
import com.routineflow.app.presentation.screens.ChainExecutionScreen
import com.routineflow.app.presentation.screens.StatsScreen
import com.routineflow.app.presentation.screens.RunScreen
import com.routineflow.app.presentation.screens.RunningScreen
import com.routineflow.app.notifications.ActionSpeech
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @javax.inject.Inject lateinit var actionSpeech: ActionSpeech
    private lateinit var contentHost: FrameLayout
    private val viewModel: MainViewModel by viewModels()
    private val exportSettingsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportSettings { json ->
            runCatching { contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
                .onSuccess { Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show() }
        }
    }
    private val importSettingsLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("empty")
        }.onSuccess { json ->
            viewModel.importSettings(json) { success ->
                Toast.makeText(this, getString(if (success) R.string.settings_loaded else R.string.settings_load_error), Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, getString(R.string.settings_load_error), Toast.LENGTH_SHORT).show()
        }
    }
    private val dateKey get() = RoutineDay.currentDateKey()
    private val isDarkTheme by lazy { (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES }
    private val navy by lazy { if (isDarkTheme) 0xffe2e8f0.toInt() else 0xff172554.toInt() }
    private val accent = 0xff4f46e5.toInt()
    private val pageBackground by lazy { if (isDarkTheme) 0xff0f172a.toInt() else 0xfff8fafc.toInt() }
    private val surface by lazy { if (isDarkTheme) 0xff1e293b.toInt() else 0xffffffff.toInt() }
    private val softSurface by lazy { if (isDarkTheme) 0xff273449.toInt() else 0xffe2e8f0.toInt() }
    private val inputSurface by lazy { if (isDarkTheme) 0xff172236.toInt() else 0xfff8fafc.toInt() }
    private val border by lazy { if (isDarkTheme) 0xff475569.toInt() else 0xffcbd5e1.toInt() }
    private val secondaryText by lazy { if (isDarkTheme) 0xffcbd5e1.toInt() else 0xff64748b.toInt() }
    private val darkText by lazy { if (isDarkTheme) 0xffe2e8f0.toInt() else 0xff334155.toInt() }
    private val inputLine by lazy { if (isDarkTheme) 0xff94a3b8.toInt() else 0xff94a3b8.toInt() }
    private var activeRunRoot: View? = null
    private var activeRunChainId: Long? = null
    private var activeRunActionId: Long? = null
    private var activeTimerText: TextView? = null
    private var activeProgress: ProgressBar? = null
    private var activePauseButton: MaterialButton? = null
    private var executionScreenRoot: View? = null
    private var executionScreenChainId: Long? = null
    private var completedChainsExpanded = false
    private val reorderDragController by lazy {
        ReorderDragController(this, accent, border, getString(R.string.drag_hint))
    }
    private val recurrenceDisplayFormatter by lazy { RecurrenceDisplayFormatter(this) }
    private val actionEditorDialog by lazy {
        ActionEditorDialog(this, navy, inputSurface, border, actionSpeech, ::confirmDeleteAction) { chain, existing, name, recurrence, duration, speechKey, autoAdvance, startDate, endDate ->
            if (existing?.speechKey != null && existing.speechKey != speechKey) {
                actionSpeech.delete(existing.speechKey)
            }
            if (existing == null) viewModel.addAction(chain.id, name, recurrence, duration, speechKey, autoAdvance, startDate, endDate)
            else viewModel.editAction(chain.id, existing.id, name, recurrence, duration, speechKey, autoAdvance, startDate, endDate)
        }
    }
    private val chainDialogFactory by lazy { ChainDialogFactory(this, navy, secondaryText, inputSurface, border, viewModel::addChain, viewModel::renameChain) }
    private val chainsScreen by lazy { ChainsScreen(this, navy, secondaryText, border, reorderDragController, ::card, ::showChainSettingsMenu, { chain -> viewModel.openChain(chain.id); render(viewModel.state.value) }, ::newChainDialog, viewModel::moveChainToEnd, ::beginChainDrag, ::chainDragEvent) }
    private val statsScreen by lazy { StatsScreen(this) }
    private val runScreen by lazy { RunScreen(this, navy, secondaryText, accent, border, ::card, viewModel::isDue, dateKey, { completedChainsExpanded }, { completedChainsExpanded = !completedChainsExpanded; render(viewModel.state.value) }, { chain, state -> viewModel.openExecution(chain.id); viewModel.stopAction(); showChainExecution(state.copy(running = null), chain) }, { chain -> viewModel.openExecution(chain.id); viewModel.startChain(chain.id) }) }
    private val chainEditorScreen by lazy { ChainEditorScreen(this, navy, accent, ::card, reorderDragController, recurrenceDisplayFormatter, ::saveChainName, ::editActionDialog, ::beginActionDrag, ::actionDragEvent, { chain, actionId -> viewModel.moveActionToEnd(chain.id, actionId) }, ::chainSettingsBar) }
    private val chainExecutionScreen by lazy { ChainExecutionScreen(this, navy, secondaryText, softSurface, darkText, ::card, ::bottomActionButton, ::circleToggle, dateKey, viewModel::isDue, { chain, action, checked, allCompleted, view -> styleCircleToggle(view, checked, false); if (allCompleted != (chain.actions.filter(viewModel::isDue).all { if (it.id == action.id) checked else it.doneOn == dateKey })) { executionScreenRoot = null; executionScreenChainId = null }; viewModel.toggleAction(chain.id, action.id, checked) }, { executionScreenRoot = null; executionScreenChainId = null; viewModel.closeExecution(); render(viewModel.state.value) }, { viewModel.navigation.value.executionChainId?.let { viewModel.startChain(it) } }) }
    private val runningScreen by lazy { RunningScreen(this, navy, secondaryText, accent, softSurface, darkText, { title -> base(title) }, ::card, ::secondaryButton, ::controlButton, viewModel::isDue, dateKey, { viewModel.stopAction(); viewModel.closeExecution(); render(viewModel.state.value) }, viewModel::rewindCurrent, viewModel::resetCurrentTimer, viewModel::postponeCurrent, viewModel::pauseResume, viewModel::completeCurrent, viewModel::skipCurrent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDarkTheme
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !isDarkTheme
        contentHost = FrameLayout(this).apply {
            tag = "stable_content_host"
            setBackgroundColor(pageBackground)
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }
        setContentView(contentHost)
        ViewCompat.requestApplyInsets(contentHost)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    private fun render(state: AppState) {
        if (state.running != null) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val navigation = viewModel.navigation.value
        val chain = navigation.chainId?.let { id -> state.chains.firstOrNull { it.id == id } }
        if (navigation.chainId != null && chain == null) viewModel.selectTab(AppTab.CHAINS)
        val executionChain = navigation.executionChainId?.let { id -> state.chains.firstOrNull { it.id == id } }
        if (navigation.executionChainId != null && executionChain == null) viewModel.closeExecution()
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
        when (navigation.tab) {
            AppTab.RUN -> showToday(state)
            AppTab.CHAINS -> if (chain != null) showChain(state, chain) else showChains(state)
            AppTab.STATS -> showStats(state)
        }
    }

    private fun base(title: String, showTitle: Boolean = true): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(24, 28, 24, 24); setBackgroundColor(pageBackground)
        if (showTitle) addView(TextView(this@MainActivity).apply { text = title; textSize = 30f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(navy); setPadding(0, 0, 0, 20) })
    }

    private fun display(root: View) {
        contentHost.removeAllViews()
        contentHost.addView(root, FrameLayout.LayoutParams(-1, -1))
    }

    private fun secondaryButton(text: String, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; cornerRadius = 18; setTextSize(20f); setTextColor(darkText)
        backgroundTintList = ColorStateList.valueOf(softSurface); insetTop = 6; insetBottom = 6
        setOnClickListener { onClick() }
    }

    private fun bottomActionButton(text: String, primary: Boolean, textSizeSp: Float = 14f, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; cornerRadius = 8; setTextSize(textSizeSp); gravity = Gravity.CENTER; insetTop = 0; insetBottom = 0; minHeight = ScreenLayout.BOTTOM_MENU_HEIGHT_PX; elevation = 0f; stateListAnimator = null
        backgroundTintList = ColorStateList.valueOf(if (primary) accent else softSurface)
        setTextColor(if (primary) 0xffffffff.toInt() else darkText)
        setOnClickListener { onClick() }
    }

    private fun card(content: View) = MaterialCardView(this).apply {
        radius = 22f; cardElevation = 0f; setCardBackgroundColor(surface); setContentPadding(16, 8, 16, 8); addView(content)
    }

    private fun showToday(state: AppState) {
        val root = base(getString(R.string.run_title))
        root.addView(ScreenLayout.scrollable(this, runScreen.build(state)), ScreenLayout.fillRemaining())
        addBottomNav(root, AppTab.RUN)
        display(root)
    }

    private fun showChainExecution(state: AppState, chain: Chain) {
        val root = base(chain.name)
        root.addView(chainExecutionScreen.build(state, chain), ScreenLayout.fillRemaining())
        executionScreenRoot = root
        executionScreenChainId = chain.id
        display(root)
    }

    private fun showChainRunning(state: AppState, chain: Chain) {
        val current = state.running ?: return
        val action = chain.actions.firstOrNull { it.id == current.actionId } ?: return
        if (activeRunRoot?.isAttachedToWindow == true && activeRunChainId == chain.id && activeRunActionId == action.id) { updateRunningUi(current); return }
        val result = runningScreen.build(chain, current, viewModel.canRewindCurrent())
        activeRunRoot = result.root; activeRunChainId = chain.id; activeRunActionId = action.id; activeTimerText = result.timer; activeProgress = result.progress; activePauseButton = result.pause
        display(result.root)
    }

    private fun updateRunningUi(current: com.routineflow.app.model.RunningAction) {
        activeTimerText?.text = TimeFormatter.adaptive(current.elapsedSeconds)
        activeTimerText?.setTextColor(if (current.overtime) 0xffef4444.toInt() else accent)
        activeProgress?.progress = current.elapsedSeconds.coerceAtMost(current.totalSeconds).toInt()
        activeProgress?.progressTintList = ColorStateList.valueOf(if (current.overtime) 0xffef4444.toInt() else accent)
        activePauseButton?.text = if (current.paused) getString(R.string.running_resume) else getString(R.string.running_pause)
    }

    private fun controlButton(text: String, primary: Boolean, textSizeSp: Float = 14f, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; textSize = textSizeSp; cornerRadius = 8; insetTop = 0; insetBottom = 0; elevation = 0f; stateListAnimator = null
        backgroundTintList = ColorStateList.valueOf(if (primary) accent else softSurface); setTextColor(if (primary) 0xffffffff.toInt() else darkText); setOnClickListener { onClick() }
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

    private fun showChains(state: AppState) {
        val root = base("", showTitle = false)
        root.addView(ScreenLayout.scrollable(this, chainsScreen.build(state.chains)), ScreenLayout.fillRemaining())
        addBottomNav(root, AppTab.CHAINS)
        display(root)
    }

    private fun showChainSettingsMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.save_settings)
            menu.add(0, 2, 1, R.string.load_settings)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> exportSettingsLauncher.launch("routine-flow.json")
                    2 -> importSettingsLauncher.launch(arrayOf("application/json", "text/plain"))
                }
                true
            }
        }.show()
    }

    private fun beginChainDrag(source: View, chainId: Long): Boolean {
        return beginReorderDrag(source, chainId)
    }

    private fun chainDragEvent(targetChainId: Long, targetView: View, insertionMarker: View, event: android.view.DragEvent): Boolean = reorderDragEvent(targetView, insertionMarker, event) { fromId ->
        if (fromId != targetChainId) viewModel.moveChain(fromId, targetChainId)
    }

    private fun beginReorderDrag(source: View, itemId: Long): Boolean {
        return reorderDragController.begin(source, itemId)
    }

    private fun reorderDragEvent(targetView: View, insertionMarker: View, event: android.view.DragEvent, onDrop: (Long) -> Unit): Boolean =
        reorderDragController.handle(targetView, insertionMarker, event, onDrop)

    private fun reorderEndDropZone(onDrop: (Long) -> Unit): View = reorderDragController.endDropZone(onDrop)

    private fun showStats(state: AppState) {
        val root = base(getString(R.string.tab_stats))
        root.addView(ScreenLayout.scrollable(this, statsScreen.build()), ScreenLayout.fillRemaining())
        addBottomNav(root, AppTab.STATS)
        display(root)
    }

    private fun bottomNav(active: AppTab): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 0, 0, 0)
        background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 0f; setColor(surface) }
        fun add(label: String, iconRes: Int, tab: AppTab) {
            val selected = tab == active
            val foreground = if (selected) 0xffffffff.toInt() else darkText
            val item = MaterialButton(this@MainActivity).apply {
                text = label; isAllCaps = false; textSize = 14f; cornerRadius = 8
                this.icon = getDrawable(iconRes); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_TOP; iconPadding = 14; iconSize = 64
                minHeight = ScreenLayout.BOTTOM_MENU_HEIGHT_PX; minWidth = 0; setPadding(4, 12, 4, 12); insetTop = 0; insetBottom = 0; elevation = 0f; stateListAnimator = null
                backgroundTintList = ColorStateList.valueOf(if (selected) accent else android.graphics.Color.TRANSPARENT)
                iconTint = ColorStateList.valueOf(foreground); setTextColor(foreground)
                setOnClickListener { viewModel.selectTab(tab); render(viewModel.state.value) }
            }
            addView(item, LinearLayout.LayoutParams(0, ScreenLayout.BOTTOM_MENU_HEIGHT_PX, 1f))
        }
        add(getString(R.string.tab_run), android.R.drawable.ic_media_play, AppTab.RUN)
        add(getString(R.string.tab_chains), android.R.drawable.ic_menu_sort_by_size, AppTab.CHAINS)
        add(getString(R.string.tab_stats), android.R.drawable.ic_menu_info_details, AppTab.STATS)
    }

    private fun addBottomNav(root: LinearLayout, active: AppTab) {
        root.addView(bottomNav(active), LinearLayout.LayoutParams(-1, ScreenLayout.BOTTOM_MENU_HEIGHT_PX))
    }

    private fun showChain(state: AppState, chain: Chain) {
        val root = base("", showTitle = false)
        root.addView(chainEditorScreen.build(state, chain), ScreenLayout.fillRemaining())
        display(root)
    }

    private fun beginActionDrag(source: View, action: Action): Boolean {
        return beginReorderDrag(source, action.id)
    }

    private fun actionDragEvent(chain: Chain, targetAction: Action, targetView: View, insertionMarker: View, event: android.view.DragEvent): Boolean = reorderDragEvent(targetView, insertionMarker, event) { fromId ->
        if (fromId != targetAction.id) viewModel.moveAction(chain.id, fromId, targetAction.id)
    }

    private fun saveChainName(chain: Chain, value: String) {
        value.trim().takeIf(String::isNotEmpty)?.takeIf { it != chain.name }?.let { viewModel.renameChain(chain.id, it) }
    }

    private fun chainSettingsBar(state: AppState, chain: Chain): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 0)
        background = android.graphics.drawable.GradientDrawable().apply { setColor(surface) }

        fun item(iconRes: Int, label: String, primary: Boolean, onClick: () -> Unit) = MaterialButton(this@MainActivity).apply {
            text = label; isAllCaps = false; textSize = if (label.isEmpty()) 20f else 14f
            gravity = Gravity.CENTER; cornerRadius = 8; minHeight = ScreenLayout.BOTTOM_MENU_HEIGHT_PX; minWidth = 0
            insetTop = 0; insetBottom = 0; elevation = 0f; stateListAnimator = null
            icon = getDrawable(iconRes); iconGravity = if (label.isEmpty()) MaterialButton.ICON_GRAVITY_TEXT_START else MaterialButton.ICON_GRAVITY_TEXT_TOP
            iconSize = if (label.isEmpty()) 42 else 36; iconPadding = 10
            backgroundTintList = ColorStateList.valueOf(if (primary) accent else android.graphics.Color.TRANSPARENT)
            iconTint = ColorStateList.valueOf(if (primary) 0xffffffff.toInt() else darkText)
            setTextColor(if (primary) 0xffffffff.toInt() else darkText)
            setOnClickListener { onClick() }
        }

        addView(item(R.drawable.ic_arrow_back, "", false) {
            viewModel.selectTab(AppTab.CHAINS); render(state)
        }, LinearLayout.LayoutParams(0, ScreenLayout.BOTTOM_MENU_HEIGHT_PX, ScreenLayout.CHAIN_EDITOR_SIDE_MENU_WEIGHT))
        addView(item(R.drawable.ic_add, getString(R.string.add_action), true) {
            newActionDialog(chain)
        }, LinearLayout.LayoutParams(0, ScreenLayout.BOTTOM_MENU_HEIGHT_PX, ScreenLayout.CHAIN_EDITOR_CENTER_MENU_WEIGHT))
        addView(item(R.drawable.ic_delete, "", false) {
            confirmDeleteChain(chain)
        }, LinearLayout.LayoutParams(0, ScreenLayout.BOTTOM_MENU_HEIGHT_PX, ScreenLayout.CHAIN_EDITOR_SIDE_MENU_WEIGHT))
    }

    private fun newChainDialog() {
        chainDialogFactory.showCreate()
    }

    private fun newActionDialog(chain: Chain) {
        actionEditorDialog.show(chain, null)
    }

    private fun editActionDialog(chain: Chain, action: Action) {
        actionEditorDialog.show(chain, action)
    }

    private fun editChainDialog(chain: Chain) {
        chainDialogFactory.showEdit(chain)
    }

    private fun displayRecurrence(rule: com.routineflow.app.model.RecurrenceRule): String = recurrenceDisplayFormatter.format(rule)

    private fun confirmDeleteAction(chain: Chain, action: Action) = AlertDialog.Builder(this).setTitle("Удалить действие?").setMessage(action.title).setNegativeButton("Отмена", null).setPositiveButton("Удалить") { _, _ -> viewModel.deleteAction(chain.id, action.id) }.show()
    private fun confirmDeleteChain(chain: Chain) = AlertDialog.Builder(this).setTitle("Удалить цепочку?").setMessage(chain.name).setNegativeButton("Отмена", null).setPositiveButton("Удалить") { _, _ -> viewModel.deleteChain(chain.id) }.show()
}
