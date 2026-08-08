package com.routineflow.app

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun todayScreenIsDisplayed() {
        onView(withText(R.string.run_title)).check(matches(isDisplayed()))
        onView(withText(R.string.tab_chains)).check(matches(isDisplayed()))
    }

    @Test
    fun chainsTabOpens() {
        onView(withText(R.string.tab_chains)).perform(click())
        onView(withText(R.string.chains_subtitle)).check(matches(isDisplayed()))
    }
}
