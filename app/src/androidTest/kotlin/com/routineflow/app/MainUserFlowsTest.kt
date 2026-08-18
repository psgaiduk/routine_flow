package com.routineflow.app

import android.graphics.Rect
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.`is`
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.widget.EditText

@RunWith(AndroidJUnit4::class)
class MainUserFlowsTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val suffix = System.currentTimeMillis().toString()

    @Test
    fun canCreateChain() {
        val chain = "UI chain $suffix"
        openChains()
        openNewChainDialog()
        onView(withTagValue(`is`("chain_create_input"))).perform(replaceText(chain))
        onView(withText(R.string.create)).perform(click())

        onView(withText(chain)).check(matches(isDisplayed()))
    }

    @Test
    fun canAddActionToChain() {
        val chain = "UI action chain $suffix"
        val action = "UI action $suffix"
        createChain(chain)
        openChain(chain)
        onView(withText(R.string.add_action)).perform(click())
        onView(withTagValue(`is`("action_title_input"))).perform(replaceText(action))
        onView(withText(R.string.save)).perform(click())

        onView(withText(action)).check(matches(isDisplayed()))
    }

    @Test
    fun actionAutoAdvanceSettingCanBeEnabled() {
        val chain = "UI auto advance chain $suffix"
        val action = "UI auto advance action $suffix"
        createChain(chain)
        addAction(chain, action)
        onView(withText(action)).perform(click())

        onView(withTagValue(`is`("action_auto_advance_checkbox")))
            .check(matches(isDisplayed()))
            .perform(click())
            .check(matches(isChecked()))
        onView(withTagValue(`is`("action_advanced_header"))).perform(click())
        onView(withTagValue(`is`("action_start_date_input"))).check(matches(isDisplayed()))
    }

    @Test
    fun canEditAction() {
        val chain = "UI edit chain $suffix"
        val oldAction = "Old action $suffix"
        val newAction = "Edited action $suffix"
        createChain(chain)
        addAction(chain, oldAction)
        onView(withText(oldAction)).perform(click())
        onView(withTagValue(`is`("action_title_input"))).perform(replaceText(newAction))
        onView(withText(R.string.save)).perform(click())

        onView(withText(newAction)).check(matches(isDisplayed()))
        onView(withText(oldAction)).check(doesNotExist())
    }

    @Test
    fun canStartChainFromRunTab() {
        val chain = "UI run chain $suffix"
        val action = "UI running action $suffix"
        createChain(chain)
        addAction(chain, action)
        onView(withText(R.string.tab_run)).perform(click())
        onView(withText(chain)).perform(click())
        onView(withText(R.string.run_start)).perform(click())

        onView(withText(action)).check(matches(isDisplayed()))
    }

    @Test
    fun quickStartAreaIsWideEnoughAndStartsChain() {
        val chain = "UI quick chain $suffix"
        val action = "UI quick action $suffix"
        createChain(chain)
        addAction(chain, action)
        onView(withText(R.string.tab_run)).perform(click())

        onView(withText(chain)).check { chainTitle, _ ->
            val card = chainTitle.parent?.parent?.parent as? android.view.View
                ?: error("Chain card is missing")
            val quickArea = card.findViewWithTag<android.view.View>("quick_start_area")
                ?: error("Quick-start area is missing")
            val areaBounds = Rect()
            val cardBounds = Rect()
            quickArea.getGlobalVisibleRect(areaBounds)
            card.getGlobalVisibleRect(cardBounds)
            val ratio = areaBounds.width().toFloat() / cardBounds.width().toFloat()
            check(ratio in 0.15f..0.20f) { "Expected quick-start area to occupy 15–20% of card, got $ratio" }
            quickArea.performClick()
        }
        onView(withText(action)).check(matches(isDisplayed()))
    }

    @Test
    fun previousStepButtonAppearsAfterCompletingFirstStep() {
        val chain = "UI back chain $suffix"
        val firstAction = "UI first step $suffix"
        val secondAction = "UI second step $suffix"
        createChain(chain)
        addAction(chain, firstAction)
        onView(withText(R.string.add_action)).perform(click())
        onView(withTagValue(`is`("action_title_input"))).perform(replaceText(secondAction))
        onView(withText(R.string.save)).perform(click())

        onView(withText(R.string.tab_run)).perform(click())
        onView(withText(chain)).perform(click())
        onView(withText(R.string.run_start)).perform(click())
        onView(withText("✓")).perform(click())
        SystemClock.sleep(1_200)

        onView(withText(R.string.running_back_step)).check(matches(isDisplayed()))
    }

    @Test
    fun timerCardHasGapBeforeResetButton() {
        val chain = "UI spacing chain $suffix"
        val action = "UI spacing action $suffix"
        createChain(chain)
        addAction(chain, action)
        onView(withText(R.string.tab_run)).perform(click())
        onView(withText(chain)).perform(click())
        onView(withText(R.string.run_start)).perform(click())

        onView(withTagValue(`is`("running_reset_button"))).check { resetButton, _ ->
            val timerCard = resetButton.rootView.findViewWithTag<android.view.View>("running_current_card")
            check(timerCard != null) { "Running timer card is missing" }
            val cardBounds = Rect()
            val resetBounds = Rect()
            timerCard.getGlobalVisibleRect(cardBounds)
            resetButton.getGlobalVisibleRect(resetBounds)
            check(resetBounds.top - cardBounds.bottom >= 8) {
                "Expected at least 8 px gap, got ${resetBounds.top - cardBounds.bottom}"
            }
        }
    }

    @Test
    fun nextStepCardHasStandardGapAfterPostponeButton() {
        val chain = "UI next spacing chain $suffix"
        val action = "UI next spacing action $suffix"
        createChain(chain)
        addAction(chain, action)
        onView(withText(R.string.tab_run)).perform(click())
        onView(withText(chain)).perform(click())
        onView(withText(R.string.run_start)).perform(click())

        onView(withTagValue(`is`("running_postpone_button"))).check { postponeButton, _ ->
            val nextCard = postponeButton.rootView.findViewWithTag<android.view.View>("running_next_card")
            check(nextCard != null) { "Running next-step card is missing" }
            val nextBounds = Rect()
            val postponeBounds = Rect()
            nextCard.getGlobalVisibleRect(nextBounds)
            postponeButton.getGlobalVisibleRect(postponeBounds)
            check(nextBounds.top - postponeBounds.bottom >= 6) {
                "Expected at least 6 px gap after postpone button, got ${nextBounds.top - postponeBounds.bottom}"
            }
        }
    }

    private fun openChains() {
        onView(withText(R.string.tab_chains)).perform(click())
        onView(withText(R.string.tab_chains)).check(matches(isDisplayed()))
    }

    private fun openNewChainDialog() {
        onView(withText(containsString(string(R.string.new_chain)))).perform(click())
    }

    private fun createChain(name: String) {
        openChains()
        openNewChainDialog()
        onView(withTagValue(`is`("chain_create_input"))).perform(replaceText(name))
        onView(withText(R.string.create)).perform(click())
        onView(withText(name)).check(matches(isDisplayed()))
    }

    private fun openChain(name: String) {
        onView(withText(name)).perform(click())
    }

    private fun addAction(chain: String, action: String) {
        openChain(chain)
        onView(withText(R.string.add_action)).perform(click())
        onView(withTagValue(`is`("action_title_input"))).perform(replaceText(action))
        onView(withText(R.string.save)).perform(click())
        onView(withText(action)).check(matches(isDisplayed()))
    }

    private fun string(resource: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(resource)
}
